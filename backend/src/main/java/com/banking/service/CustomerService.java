package com.banking.service;

import com.banking.dto.BeneficiaryRequest;
import com.banking.dto.LoginRequest;
import com.banking.dto.RegisterRequest;
import com.banking.exception.AccountNotFoundException;
import com.banking.exception.DuplicateResourceException;
import com.banking.exception.InvalidCredentialsException;
import com.banking.exception.MpinVerificationException;
import com.banking.model.Account;
import com.banking.model.Beneficiary;
import com.banking.model.Customer;
import com.banking.repository.AccountRepository;
import com.banking.repository.BeneficiaryRepository;
import com.banking.repository.CustomerRepository;
import com.banking.systemdesign.sharding.ConsistentHashRouter;
import com.banking.systemdesign.sharding.ShardConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final BankingAutocompleteService autocompleteService;
    private final ConsistentHashRouter consistentHashRouter;
    private final ShardConnectionManager shardConnectionManager;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public CustomerService(CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           BeneficiaryRepository beneficiaryRepository,
                           @Autowired(required = false) BankingAutocompleteService autocompleteService,
                           @Autowired(required = false) ConsistentHashRouter consistentHashRouter,
                           @Autowired(required = false) ShardConnectionManager shardConnectionManager) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.autocompleteService = autocompleteService;
        this.consistentHashRouter = consistentHashRouter;
        this.shardConnectionManager = shardConnectionManager;
    }

    @Transactional
    public Customer register(RegisterRequest request) {
        String cleanEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (customerRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new DuplicateResourceException("Customer with email " + cleanEmail + " already exists");
        }
        String passwordHash = passwordEncoder.encode(request.getPassword());
        String mpin = (request.getMpin() != null && !request.getMpin().isBlank()) ? request.getMpin().trim() : "1234";
        String mpinHash = passwordEncoder.encode(mpin);

        // Determine assigned database shard via consistent hashing on customer email across 4 shards
        int assignedShard = consistentHashRouter != null
                ? consistentHashRouter.getShard(cleanEmail)
                : 1;

        String cleanName = request.getFullName() != null ? request.getFullName().trim() : "";
        Customer customer = new Customer(cleanName, cleanEmail, passwordHash, mpinHash);
        customer.setShardId(assignedShard);
        customer = customerRepository.save(customer);

        // Auto-provision initial checking account for newly registered customer on the assigned shard
        Account account = provisionAccountForCustomer(customer, assignedShard);

        // Replicate to physical database shard
        if (shardConnectionManager != null) {
            shardConnectionManager.replicateCustomer(assignedShard, customer);
            shardConnectionManager.replicateAccount(assignedShard, account);
        }

        return customer;
    }

    public boolean verifyMpin(Long customerId, String mpin) {
        Customer customer = getCustomer(customerId);
        if (mpin == null || mpin.isBlank()) {
            throw new MpinVerificationException("MPIN is required");
        }
        String storedHash = customer.getMpinHash();
        if (storedHash == null) {
            // Fallback for existing demo accounts before update
            if ("1234".equals(mpin.trim())) {
                customer.setMpinHash(passwordEncoder.encode("1234"));
                customerRepository.save(customer);
                return true;
            }
            throw new MpinVerificationException("Incorrect MPIN. (Default MPIN is 1234)");
        }
        if (!passwordEncoder.matches(mpin.trim(), storedHash)) {
            throw new MpinVerificationException("Incorrect MPIN. Please try again.");
        }
        return true;
    }

    @Transactional
    public void setMpin(Long customerId, String newMpin) {
        if (newMpin == null || newMpin.trim().length() < 4) {
            throw new IllegalArgumentException("MPIN must be at least 4 digits");
        }
        Customer customer = getCustomer(customerId);
        customer.setMpinHash(passwordEncoder.encode(newMpin.trim()));
        customerRepository.save(customer);
    }

    public Customer authenticate(LoginRequest request) {
        String cleanEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        java.util.Optional<Customer> customerOpt = customerRepository.findByEmailIgnoreCase(cleanEmail);
        Customer customer;
        if (customerOpt.isEmpty() && shardConnectionManager != null) {
            customer = findCustomerOnAnyShard(cleanEmail);
            if (customer == null) {
                throw new InvalidCredentialsException("Invalid email or password");
            }
        } else {
            customer = customerOpt.orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        }

        if (!passwordEncoder.matches(request.getPassword(), customer.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return customer;
    }

    private Customer findCustomerOnAnyShard(String cleanEmail) {
        if (shardConnectionManager == null) return null;
        for (int sId = 2; sId <= 4; sId++) {
            final int currentShardId = sId;
            try {
                org.springframework.jdbc.core.JdbcTemplate template = shardConnectionManager.getShardJdbcTemplate(currentShardId);
                if (template != null) {
                    String sql = "SELECT customer_id, full_name, email, password_hash, mpin_hash, shard_id, created_at FROM customers WHERE LOWER(email) = ?";
                    List<Customer> list = template.query(sql, (rs, rowNum) -> {
                        Customer c = new Customer();
                        c.setCustomerId(rs.getLong("customer_id"));
                        c.setFullName(rs.getString("full_name"));
                        c.setEmail(rs.getString("email"));
                        c.setPasswordHash(rs.getString("password_hash"));
                        c.setMpinHash(rs.getString("mpin_hash"));
                        int sid = rs.getInt("shard_id");
                        c.setShardId(rs.wasNull() || sid <= 0 ? currentShardId : sid);
                        java.sql.Timestamp ts = rs.getTimestamp("created_at");
                        if (ts != null) c.setCreatedAt(ts.toInstant());
                        return c;
                    }, cleanEmail);
                    if (!list.isEmpty()) {
                        Customer found = list.get(0);
                        try {
                            if (!customerRepository.existsByEmailIgnoreCase(found.getEmail())) {
                                customerRepository.save(found);
                            }
                        } catch (Exception ignored) {}
                        return found;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public Customer getCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new InvalidCredentialsException("Customer not found with ID: " + customerId));
    }

    @Transactional
    public List<Account> getCustomerAccounts(Long customerId) {
        Customer customer = getCustomer(customerId); // verify existence
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        if (accounts == null || accounts.isEmpty()) {
            int shardId = customer.getShardId() != null ? customer.getShardId() : 1;
            Account newAccount = provisionAccountForCustomer(customer, shardId);
            return List.of(newAccount);
        }
        return accounts;
    }

    private Account provisionAccountForCustomer(Customer customer, int shardId) {
        Long customerId = customer.getCustomerId();
        String accNum = String.format("ACC-%04d", (shardId * 1000) + customerId);
        int counter = 1;
        while (accountRepository.findByAccountNumber(accNum).isPresent()) {
            accNum = String.format("ACC-%04d", (shardId * 1000) + customerId + (counter * 100));
            counter++;
        }
        Account account = new Account(accNum, customer.getFullName(), new BigDecimal("1000.00"), shardId, customerId);
        return accountRepository.save(account);
    }

    @Transactional
    public Beneficiary addBeneficiary(Long customerId, BeneficiaryRequest request) {
        getCustomer(customerId); // verify existence

        String targetAcc = request.getBeneficiaryAccountNumber();
        if (accountRepository.findByAccountNumber(targetAcc).isEmpty()) {
            throw new AccountNotFoundException("Beneficiary account number does not exist: " + targetAcc);
        }

        if (beneficiaryRepository.existsByCustomerIdAndBeneficiaryAccountNumber(customerId, targetAcc)) {
            throw new DuplicateResourceException("Beneficiary account is already registered for this customer");
        }

        Beneficiary beneficiary = new Beneficiary(customerId, targetAcc, request.getNickname());
        Beneficiary saved = beneficiaryRepository.save(beneficiary);
        if (autocompleteService != null) {
            autocompleteService.invalidateCustomerBeneficiaryCache(customerId);
        }
        return saved;
    }

    public List<Beneficiary> getCustomerBeneficiaries(Long customerId) {
        getCustomer(customerId); // verify existence
        return beneficiaryRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
