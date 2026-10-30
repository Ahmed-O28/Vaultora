package com.banking.service;

import com.banking.dto.*;
import com.banking.exception.InvalidCredentialsException;
import com.banking.model.Account;
import com.banking.model.Admin;
import com.banking.model.Customer;
import com.banking.model.Transaction;
import com.banking.repository.AccountRepository;
import com.banking.repository.AdminRepository;
import com.banking.repository.CustomerRepository;
import com.banking.repository.TransactionRepository;
import com.banking.systemdesign.sharding.ConsistentHashRouter;
import com.banking.systemdesign.sharding.ShardConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AdminRepository adminRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final JwtService jwtService;
    private final ShardConnectionManager shardConnectionManager;
    private final ConsistentHashRouter consistentHashRouter;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminService(AdminRepository adminRepository,
                        CustomerRepository customerRepository,
                        AccountRepository accountRepository,
                        TransactionRepository transactionRepository,
                        JwtService jwtService,
                        @Autowired(required = false) ShardConnectionManager shardConnectionManager,
                        @Autowired(required = false) ConsistentHashRouter consistentHashRouter) {
        this.adminRepository = adminRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.jwtService = jwtService;
        this.shardConnectionManager = shardConnectionManager;
        this.consistentHashRouter = consistentHashRouter;
    }

    /**
     * Authenticates administrator credentials and returns JWT bearer token with ADMIN role.
     */
    public AdminAuthResponse authenticate(AdminLoginRequest request) {
        String cleanUser = request.getUsername() != null ? request.getUsername().trim() : "";
        Admin admin = adminRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(cleanUser, cleanUser)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid admin username or password"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid admin username or password");
        }

        String token = jwtService.generateToken(admin.getId(), admin.getEmail(), admin.getRole());

        return new AdminAuthResponse(
                admin.getId(),
                admin.getUsername(),
                admin.getEmail(),
                admin.getRole(),
                admin.getCreatedAt(),
                token
        );
    }

    /**
     * Queries and combines registered customers across all four PostgreSQL shards.
     * Guaranteed safe: Never returns passwords or MPINs.
     */
    public List<AdminCustomerView> getAllCustomers(String search, Integer shardFilter) {
        Map<Long, AdminCustomerView> customerMap = new LinkedHashMap<>();

        // 1. Retrieve Shard 1 customers and their linked accounts
        List<Customer> shard1Customers = customerRepository.findAll();
        for (Customer c : shard1Customers) {
            List<Account> accounts = accountRepository.findByCustomerId(c.getCustomerId());
            String accNum = accounts.isEmpty() ? "N/A" : accounts.get(0).getAccountNumber();
            BigDecimal balance = accounts.isEmpty() ? BigDecimal.ZERO : accounts.get(0).getBalance();
            Integer sId = c.getShardId();
            if (sId == null && !accounts.isEmpty()) {
                sId = accounts.get(0).getShardId();
            }
            if (sId == null && consistentHashRouter != null) {
                sId = consistentHashRouter.getShard(c.getEmail());
            }
            if (sId == null) sId = 1;

            customerMap.put(c.getCustomerId(), new AdminCustomerView(
                    c.getCustomerId(),
                    c.getFullName(),
                    c.getEmail(),
                    accNum,
                    balance,
                    sId,
                    "ACTIVE",
                    c.getCreatedAt()
            ));
        }

        // 2. Query secondary shards (Shards 2, 3, and 4) if available
        if (shardConnectionManager != null) {
            for (int sId = 2; sId <= 4; sId++) {
                final int currentShardId = sId;
                try {
                    org.springframework.jdbc.core.JdbcTemplate template = shardConnectionManager.getShardJdbcTemplate(currentShardId);
                    if (template != null) {
                        String sql = "SELECT c.customer_id, c.full_name, c.email, c.created_at, c.shard_id AS c_shard, " +
                                "a.account_number, a.balance, a.shard_id AS a_shard " +
                                "FROM customers c " +
                                "LEFT JOIN accounts a ON c.customer_id = a.customer_id";

                        template.query(sql, rs -> {
                            long customerId = rs.getLong("customer_id");
                            if (!customerMap.containsKey(customerId)) {
                                String fullName = rs.getString("full_name");
                                String email = rs.getString("email");
                                Timestamp ts = rs.getTimestamp("created_at");
                                Instant createdAt = ts != null ? ts.toInstant() : Instant.now();
                                String accNum = rs.getString("account_number");
                                BigDecimal balance = rs.getBigDecimal("balance");
                                int rowShard = rs.getInt("c_shard");
                                if (rs.wasNull() || rowShard <= 0) {
                                    rowShard = rs.getInt("a_shard");
                                }
                                if (rs.wasNull() || rowShard <= 0) {
                                    rowShard = currentShardId;
                                }

                                customerMap.put(customerId, new AdminCustomerView(
                                        customerId,
                                        fullName,
                                        email,
                                        accNum != null ? accNum : "N/A",
                                        balance != null ? balance : BigDecimal.ZERO,
                                        rowShard,
                                        "ACTIVE",
                                        createdAt
                                ));
                            }
                        });
                    }
                } catch (Exception e) {
                    log.debug("Notice: Unable to query customers from Shard {}: {}", currentShardId, e.getMessage());
                }
            }
        }

        // 3. Apply search and shard filtering
        return customerMap.values().stream()
                .filter(c -> {
                    if (shardFilter != null && !Objects.equals(c.getShardId(), shardFilter)) {
                        return false;
                    }
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase();
                        boolean matchName = c.getFullName() != null && c.getFullName().toLowerCase().contains(q);
                        boolean matchEmail = c.getEmail() != null && c.getEmail().toLowerCase().contains(q);
                        boolean matchAcc = c.getAccountNumber() != null && c.getAccountNumber().toLowerCase().contains(q);
                        return matchName || matchEmail || matchAcc;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Queries and combines transaction records across all four PostgreSQL shards.
     */
    public List<AdminTransactionView> getAllTransactions(String search, String type, String status, Integer shardFilter) {
        List<AdminTransactionView> results = new ArrayList<>();

        // 1. Shard 1 transactions
        List<Transaction> shard1Txns = transactionRepository.findAll();
        for (Transaction t : shard1Txns) {
            int sId = resolveTransactionShard(t);
            results.add(new AdminTransactionView(
                    t.getId(),
                    t.getTransactionId(),
                    t.getSourceAccount(),
                    t.getDestinationAccount(),
                    t.getAmount(),
                    t.getType(),
                    t.getStatus(),
                    t.getDescription(),
                    t.getTimestamp(),
                    sId
            ));
        }

        // 2. Secondary Shards (2, 3, 4)
        if (shardConnectionManager != null) {
            for (int sId = 2; sId <= 4; sId++) {
                final int currentShardId = sId;
                try {
                    org.springframework.jdbc.core.JdbcTemplate template = shardConnectionManager.getShardJdbcTemplate(currentShardId);
                    if (template != null) {
                        String sql = "SELECT id, transaction_id, source_account, destination_account, amount, type, status, description, timestamp " +
                                "FROM transactions";
                        template.query(sql, rs -> {
                            long id = rs.getLong("id");
                            String txnId = rs.getString("transaction_id");
                            String src = rs.getString("source_account");
                            String dst = rs.getString("destination_account");
                            BigDecimal amt = rs.getBigDecimal("amount");
                            String txnType = rs.getString("type");
                            String txnStatus = rs.getString("status");
                            String desc = rs.getString("description");
                            Timestamp ts = rs.getTimestamp("timestamp");
                            Instant timestamp = ts != null ? ts.toInstant() : Instant.now();

                            boolean exists = results.stream().anyMatch(r -> Objects.equals(r.getTransactionId(), txnId));
                            if (!exists) {
                                results.add(new AdminTransactionView(
                                        id,
                                        txnId,
                                        src,
                                        dst,
                                        amt,
                                        txnType,
                                        txnStatus,
                                        desc,
                                        timestamp,
                                        currentShardId
                                ));
                            }
                        });
                    }
                } catch (Exception e) {
                    log.debug("Notice: Unable to query transactions from Shard {}: {}", currentShardId, e.getMessage());
                }
            }
        }

        // 3. Sort by timestamp descending
        results.sort((a, b) -> {
            if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
            return b.getTimestamp().compareTo(a.getTimestamp());
        });

        // 4. Apply search & filter criteria
        return results.stream()
                .filter(t -> {
                    if (shardFilter != null && !Objects.equals(t.getShardId(), shardFilter)) {
                        return false;
                    }
                    if (type != null && !type.isBlank() && !t.getType().equalsIgnoreCase(type.trim())) {
                        return false;
                    }
                    if (status != null && !status.isBlank() && !t.getStatus().equalsIgnoreCase(status.trim())) {
                        return false;
                    }
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase();
                        boolean matchId = t.getTransactionId() != null && t.getTransactionId().toLowerCase().contains(q);
                        boolean matchSrc = t.getSourceAccount() != null && t.getSourceAccount().toLowerCase().contains(q);
                        boolean matchDst = t.getDestinationAccount() != null && t.getDestinationAccount().toLowerCase().contains(q);
                        boolean matchDesc = t.getDescription() != null && t.getDescription().toLowerCase().contains(q);
                        return matchId || matchSrc || matchDst || matchDesc;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private int resolveTransactionShard(Transaction t) {
        if (t.getSourceAccount() != null) {
            String src = t.getSourceAccount();
            Account acc = accountRepository.findByAccountNumber(src).orElse(null);
            if (acc != null && acc.getShardId() != null && acc.getShardId() > 0) {
                return acc.getShardId();
            }
            if (src.startsWith("ACC-4")) return 4;
            if (src.startsWith("ACC-3")) return 3;
            if (src.startsWith("ACC-2")) return 2;
            if (src.startsWith("ACC-1")) return 1;
        }
        if (t.getDestinationAccount() != null) {
            String dst = t.getDestinationAccount();
            Account acc = accountRepository.findByAccountNumber(dst).orElse(null);
            if (acc != null && acc.getShardId() != null && acc.getShardId() > 0) {
                return acc.getShardId();
            }
        }
        return 1;
    }

    /**
     * Aggregates key system metrics across all four shards for the Admin Dashboard overview.
     */
    public AdminStatsResponse getStats() {
        List<AdminCustomerView> customers = getAllCustomers(null, null);
        List<AdminTransactionView> transactions = getAllTransactions(null, null, null, null);

        long shard1Cust = customers.stream().filter(c -> c.getShardId() != null && c.getShardId() == 1).count();
        long shard2Cust = customers.stream().filter(c -> c.getShardId() != null && c.getShardId() == 2).count();
        long shard3Cust = customers.stream().filter(c -> c.getShardId() != null && c.getShardId() == 3).count();
        long shard4Cust = customers.stream().filter(c -> c.getShardId() != null && c.getShardId() == 4).count();

        long shard1Txn = transactions.stream().filter(t -> t.getShardId() != null && t.getShardId() == 1).count();
        long shard2Txn = transactions.stream().filter(t -> t.getShardId() != null && t.getShardId() == 2).count();
        long shard3Txn = transactions.stream().filter(t -> t.getShardId() != null && t.getShardId() == 3).count();
        long shard4Txn = transactions.stream().filter(t -> t.getShardId() != null && t.getShardId() == 4).count();

        BigDecimal totalVolume = customers.stream()
                .map(AdminCustomerView::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminStatsResponse(
                customers.size(),
                customers.size(),
                transactions.size(),
                totalVolume,
                shard1Cust,
                shard2Cust,
                shard3Cust,
                shard4Cust,
                shard1Txn,
                shard2Txn,
                shard3Txn,
                shard4Txn
        );
    }
}
