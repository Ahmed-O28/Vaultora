package com.banking.systemdesign.sharding;

import com.banking.model.Account;
import com.banking.model.BillPayment;
import com.banking.model.Customer;
import com.banking.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages database connections and JdbcTemplates across the four PostgreSQL database shards.
 * Provides resilient access, cross-shard querying, and deterministic record replication.
 */
@Component
public class ShardConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ShardConnectionManager.class);

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/bank_shard1}")
    private String shard1Url;

    @Value("${spring.datasource.username:postgres}")
    private String shard1Username;

    @Value("${spring.datasource.password:postgres}")
    private String shard1Password;

    @Value("${app.datasource.shard2.url:jdbc:postgresql://localhost:5433/bank_shard2}")
    private String shard2Url;

    @Value("${app.datasource.shard2.username:postgres}")
    private String shard2Username;

    @Value("${app.datasource.shard2.password:postgres}")
    private String shard2Password;

    @Value("${app.datasource.shard3.url:jdbc:postgresql://localhost:5434/bank_shard3}")
    private String shard3Url;

    @Value("${app.datasource.shard3.username:postgres}")
    private String shard3Username;

    @Value("${app.datasource.shard3.password:postgres}")
    private String shard3Password;

    @Value("${app.datasource.shard4.url:jdbc:postgresql://localhost:5435/bank_shard4}")
    private String shard4Url;

    @Value("${app.datasource.shard4.username:postgres}")
    private String shard4Username;

    @Value("${app.datasource.shard4.password:postgres}")
    private String shard4Password;

    private final DataSource primaryDataSource;
    private final Map<Integer, JdbcTemplate> templateMap = new ConcurrentHashMap<>();

    public ShardConnectionManager(@Autowired(required = false) DataSource primaryDataSource) {
        this.primaryDataSource = primaryDataSource;
    }

    /**
     * Retrieves or initializes a JdbcTemplate for the given 1-based shard ID (1, 2, 3, or 4).
     */
    public JdbcTemplate getShardJdbcTemplate(int shardId) {
        return templateMap.computeIfAbsent(shardId, this::createTemplateForShard);
    }

    private JdbcTemplate createTemplateForShard(int shardId) {
        if (shardId == 1 && primaryDataSource != null) {
            log.info("Using primary DataSource for Shard 1");
            return new JdbcTemplate(primaryDataSource);
        }

        String url;
        String user;
        String pass;

        switch (shardId) {
            case 1 -> {
                url = shard1Url;
                user = shard1Username;
                pass = shard1Password;
            }
            case 2 -> {
                url = shard2Url;
                user = shard2Username;
                pass = shard2Password;
            }
            case 3 -> {
                url = shard3Url;
                user = shard3Username;
                pass = shard3Password;
            }
            case 4 -> {
                url = shard4Url;
                user = shard4Username;
                pass = shard4Password;
            }
            default -> {
                log.warn("Invalid shard ID requested: {}", shardId);
                return null;
            }
        }

        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            var builder = DataSourceBuilder.create()
                    .url(url)
                    .username(user)
                    .password(pass);
            if (url.startsWith("jdbc:postgresql:")) {
                builder.driverClassName("org.postgresql.Driver");
            }
            DataSource ds = builder.build();
            JdbcTemplate template = new JdbcTemplate(ds);
            log.info("Initialized JdbcTemplate connection for Shard {} -> {}", shardId, url);
            return template;
        } catch (Exception e) {
            log.warn("Could not initialize connection for Shard {}: {}", shardId, e.getMessage());
            return null;
        }
    }

    public boolean isShardAvailable(int shardId) {
        JdbcTemplate template = getShardJdbcTemplate(shardId);
        if (template == null) return false;
        try {
            Integer result = template.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<Integer, JdbcTemplate> getAllAvailableShards() {
        Map<Integer, JdbcTemplate> available = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) {
            JdbcTemplate t = getShardJdbcTemplate(i);
            if (t != null) {
                available.put(i, t);
            }
        }
        return available;
    }

    /**
     * Safely replicates customer record to designated physical shard database.
     */
    public void replicateCustomer(int shardId, Customer customer) {
        if (customer == null || shardId <= 0) return;
        JdbcTemplate template = getShardJdbcTemplate(shardId);
        if (template == null) return;

        try {
            String sql = "INSERT INTO customers (customer_id, full_name, email, password_hash, mpin_hash, shard_id, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (customer_id) DO UPDATE SET " +
                    "full_name = EXCLUDED.full_name, " +
                    "email = EXCLUDED.email, " +
                    "password_hash = EXCLUDED.password_hash, " +
                    "mpin_hash = EXCLUDED.mpin_hash, " +
                    "shard_id = EXCLUDED.shard_id";

            Timestamp ts = customer.getCreatedAt() != null
                    ? Timestamp.from(customer.getCreatedAt())
                    : Timestamp.from(Instant.now());

            template.update(sql,
                    customer.getCustomerId(),
                    customer.getFullName(),
                    customer.getEmail(),
                    customer.getPasswordHash(),
                    customer.getMpinHash(),
                    shardId,
                    ts
            );
            log.debug("Replicated customer {} to Shard {}", customer.getEmail(), shardId);
        } catch (Exception e) {
            log.warn("Unable to replicate customer {} to Shard {}: {}", customer.getEmail(), shardId, e.getMessage());
        }
    }

    /**
     * Safely replicates account record to designated physical shard database.
     */
    public void replicateAccount(int shardId, Account account) {
        if (account == null || shardId <= 0) return;
        JdbcTemplate template = getShardJdbcTemplate(shardId);
        if (template == null) return;

        try {
            String sql = "INSERT INTO accounts (account_number, balance, customer_id, customer_name, shard_id) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT (account_number) DO UPDATE SET " +
                    "balance = EXCLUDED.balance, " +
                    "customer_name = EXCLUDED.customer_name, " +
                    "shard_id = EXCLUDED.shard_id";

            template.update(sql,
                    account.getAccountNumber(),
                    account.getBalance(),
                    account.getCustomerId(),
                    account.getCustomerName(),
                    shardId
            );
            log.debug("Replicated account {} to Shard {}", account.getAccountNumber(), shardId);
        } catch (Exception e) {
            log.warn("Unable to replicate account {} to Shard {}: {}", account.getAccountNumber(), shardId, e.getMessage());
        }
    }

    /**
     * Safely replicates transaction record to designated physical shard database.
     */
    public void replicateTransaction(int shardId, Transaction transaction) {
        if (transaction == null || shardId <= 0) return;
        JdbcTemplate template = getShardJdbcTemplate(shardId);
        if (template == null) return;

        try {
            String sql = "INSERT INTO transactions (transaction_id, source_account, destination_account, amount, type, status, description, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (transaction_id) DO NOTHING";

            Timestamp ts = transaction.getTimestamp() != null
                    ? Timestamp.from(transaction.getTimestamp())
                    : Timestamp.from(Instant.now());

            template.update(sql,
                    transaction.getTransactionId(),
                    transaction.getSourceAccount(),
                    transaction.getDestinationAccount(),
                    transaction.getAmount(),
                    transaction.getType(),
                    transaction.getStatus(),
                    transaction.getDescription(),
                    ts
            );
            log.debug("Replicated transaction {} to Shard {}", transaction.getTransactionId(), shardId);
        } catch (Exception e) {
            log.warn("Unable to replicate transaction {} to Shard {}: {}", transaction.getTransactionId(), shardId, e.getMessage());
        }
    }

    /**
     * Safely replicates bill payment record to designated physical shard database.
     */
    public void replicateBillPayment(int shardId, BillPayment payment) {
        if (payment == null || shardId <= 0) return;
        JdbcTemplate template = getShardJdbcTemplate(shardId);
        if (template == null) return;

        try {
            String sql = "INSERT INTO bill_payments (payment_id, account_number, biller_id, amount, status, transaction_id, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (payment_id) DO NOTHING";

            Timestamp ts = payment.getCreatedAt() != null
                    ? Timestamp.from(payment.getCreatedAt())
                    : Timestamp.from(Instant.now());

            template.update(sql,
                    payment.getPaymentId(),
                    payment.getAccountNumber(),
                    payment.getBillerId(),
                    payment.getAmount(),
                    payment.getStatus(),
                    payment.getTransactionId(),
                    ts
            );
            log.debug("Replicated bill payment {} to Shard {}", payment.getPaymentId(), shardId);
        } catch (Exception e) {
            log.warn("Unable to replicate bill payment {} to Shard {}: {}", payment.getPaymentId(), shardId, e.getMessage());
        }
    }
}
