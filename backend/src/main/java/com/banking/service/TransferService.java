package com.banking.service;

import com.banking.dto.TransferRequest;
import com.banking.exception.AccountNotFoundException;
import com.banking.exception.InsufficientFundsException;
import com.banking.exception.InvalidCredentialsException;
import com.banking.exception.InvalidTransferException;
import com.banking.exception.MpinVerificationException;
import com.banking.model.Account;
import com.banking.model.Transaction;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.systemdesign.idgenerator.UniqueIdGenerator;
import com.banking.systemdesign.sharding.ShardConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UniqueIdGenerator idGenerator;
    private final MetricsService metricsService;
    private final CustomerService customerService;
    private final ShardConnectionManager shardConnectionManager;

    private final Map<String, Transaction> recentTransfers = new ConcurrentHashMap<>();
    private final Map<String, Long> recentTransferTimestamps = new ConcurrentHashMap<>();

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           UniqueIdGenerator idGenerator,
                           MetricsService metricsService,
                           CustomerService customerService,
                           @Autowired(required = false) ShardConnectionManager shardConnectionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.idGenerator = idGenerator;
        this.metricsService = metricsService;
        this.customerService = customerService;
        this.shardConnectionManager = shardConnectionManager;
    }

    /**
     * Executes a money transfer between two accounts with strict concurrency safeguards:
     * 1. Wraps operations in a database transaction (@Transactional).
     * 2. Employs ordered pessimistic locking (SELECT ... FOR UPDATE) to prevent both
     *    concurrent double-spending and deadlocks.
     * 3. Validates positive amount, existence of both accounts, and sufficient balance.
     * 4. Updates account balances and creates an immutable ledger transaction record.
     */
    @Transactional
    public Transaction executeTransfer(TransferRequest request) {
        String sourceAccNum = request.getSourceAccount();
        String destAccNum = request.getDestinationAccount();
        BigDecimal amount = request.getAmount();

        // 1. Validate positive amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            metricsService.recordRequest(false);
            throw new InvalidTransferException("Transfer amount must be positive");
        }

        // 2. Prevent self-transfer
        if (sourceAccNum != null && sourceAccNum.equalsIgnoreCase(destAccNum)) {
            metricsService.recordRequest(false);
            throw new InvalidTransferException("Cannot transfer money to the same account");
        }

        // 3. Acquire pessimistic locks in consistent alphabetical order to prevent deadlocks
        Account source;
        Account dest;
        if (sourceAccNum.compareTo(destAccNum) < 0) {
            source = accountRepository.findByAccountNumberWithLock(sourceAccNum)
                    .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + sourceAccNum));
            dest = accountRepository.findByAccountNumberWithLock(destAccNum)
                    .orElseThrow(() -> new AccountNotFoundException("Destination account not found: " + destAccNum));
        } else {
            dest = accountRepository.findByAccountNumberWithLock(destAccNum)
                    .orElseThrow(() -> new AccountNotFoundException("Destination account not found: " + destAccNum));
            source = accountRepository.findByAccountNumberWithLock(sourceAccNum)
                    .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + sourceAccNum));
        }

        // Deduplication safeguard: If an identical transfer was completed within the last 2.5 seconds,
        // return the existing transaction to prevent double-spending or duplicate ledger records.
        String dedupKey = sourceAccNum + "->" + destAccNum + ":" + amount.stripTrailingZeros().toPlainString();
        long now = System.currentTimeMillis();
        Long lastExecutionTime = recentTransferTimestamps.get(dedupKey);
        if (lastExecutionTime != null && (now - lastExecutionTime) < 2500L) {
            Transaction existing = recentTransfers.get(dedupKey);
            if (existing != null) {
                log.warn("Duplicate transfer detected within {}ms for key {}. Returning existing transaction {}",
                        (now - lastExecutionTime), dedupKey, existing.getTransactionId());
                return existing;
            }
        }

        // 4. Verify MPIN authorization for the source account owner (GPay-style security)
        if (source.getCustomerId() != null) {
            String mpin = request.getMpin();
            if (mpin == null || mpin.isBlank()) {
                metricsService.recordRequest(false);
                throw new MpinVerificationException("MPIN is required to authorize transfer");
            }
            customerService.verifyMpin(source.getCustomerId(), mpin);
        }

        // 5. Validate sufficient funds
        if (source.getBalance().compareTo(amount) < 0) {
            metricsService.recordRequest(false);
            throw new InsufficientFundsException(String.format(
                    "Insufficient funds in account %s. Current balance: %s, Required: %s",
                    sourceAccNum, source.getBalance(), amount));
        }

        // 5. Debit source and credit destination
        source.setBalance(source.getBalance().subtract(amount));
        dest.setBalance(dest.getBalance().add(amount));

        accountRepository.save(source);
        accountRepository.save(dest);

        // 6. Generate unique transaction identifier and write ledger entry
        String txnId = idGenerator.generateId("TXN");
        String desc = request.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = String.format("Transfer from %s to %s", sourceAccNum, destAccNum);
        }

        Transaction transaction = new Transaction(
                txnId,
                sourceAccNum,
                destAccNum,
                amount,
                "TRANSFER",
                "SUCCESS",
                desc,
                Instant.now()
        );

        Transaction savedTxn = transactionRepository.save(transaction);

        // Record successful transfer for deduplication cache
        recentTransfers.put(dedupKey, savedTxn);
        recentTransferTimestamps.put(dedupKey, System.currentTimeMillis());
        if (recentTransferTimestamps.size() > 500) {
            long cutoff = System.currentTimeMillis() - 60000L;
            recentTransferTimestamps.entrySet().removeIf(e -> e.getValue() < cutoff);
            recentTransfers.keySet().removeIf(k -> !recentTransferTimestamps.containsKey(k));
        }

        // 7. Replicate transaction and updated balances to respective physical shards
        if (shardConnectionManager != null) {
            if (source.getShardId() != null) {
                shardConnectionManager.replicateTransaction(source.getShardId(), savedTxn);
                shardConnectionManager.replicateAccount(source.getShardId(), source);
            }
            if (dest.getShardId() != null) {
                shardConnectionManager.replicateTransaction(dest.getShardId(), savedTxn);
                shardConnectionManager.replicateAccount(dest.getShardId(), dest);
            }
        }

        // 8. Track metrics for performance evaluation
        metricsService.recordRequest(true);
        if (source.getShardId() != null) metricsService.recordShardAccess(source.getShardId());
        if (dest.getShardId() != null) metricsService.recordShardAccess(dest.getShardId());

        return savedTxn;
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    public List<Transaction> getAccountTransactions(String accountNumber) {
        // Ensure account exists
        getAccount(accountNumber);
        return transactionRepository.findBySourceAccountOrDestinationAccountOrderByTimestampDesc(
                accountNumber, accountNumber
        );
    }
}
