package com.banking.service;

import com.banking.dto.BillPaymentRequest;
import com.banking.exception.AccountNotFoundException;
import com.banking.exception.InsufficientFundsException;
import com.banking.exception.InvalidTransferException;
import com.banking.model.Account;
import com.banking.model.BillPayment;
import com.banking.model.Transaction;
import com.banking.repository.AccountRepository;
import com.banking.repository.BillPaymentRepository;
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
public class BillPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BillPaymentService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final com.banking.repository.BillerRepository billerRepository;
    private final UniqueIdGenerator idGenerator;
    private final MetricsService metricsService;
    private final ShardConnectionManager shardConnectionManager;

    private final Map<String, BillPayment> recentPayments = new ConcurrentHashMap<>();
    private final Map<String, Long> recentPaymentTimestamps = new ConcurrentHashMap<>();

    public BillPaymentService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              BillPaymentRepository billPaymentRepository,
                              @Autowired(required = false) com.banking.repository.BillerRepository billerRepository,
                              UniqueIdGenerator idGenerator,
                              MetricsService metricsService,
                              @Autowired(required = false) ShardConnectionManager shardConnectionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.billerRepository = billerRepository;
        this.idGenerator = idGenerator;
        this.metricsService = metricsService;
        this.shardConnectionManager = shardConnectionManager;
    }

    /**
     * Executes a simulated bill payment with concurrency safety:
     * 1. Validates biller active status.
     * 2. Acquires pessimistic write lock on the paying account.
     * 3. Validates positive payment amount and sufficient balance.
     * 4. Debits the account and saves updated balance.
     * 5. Writes an associated immutable record in the central 'transactions' ledger.
     * 6. Saves the bill payment record.
     */
    @Transactional
    public BillPayment payBill(BillPaymentRequest request) {
        String accNum = request.getAccountNumber();
        BigDecimal amount = request.getAmount();
        String billerId = request.getBillerId();

        if (billerId == null || billerId.isBlank()) {
            metricsService.recordRequest(false);
            throw new InvalidTransferException("Biller ID is required");
        }

        // Validate that the target biller is ACTIVE
        if (billerRepository != null) {
            com.banking.model.Biller biller = billerRepository.findById(billerId.trim()).orElse(null);
            if (biller != null && "INACTIVE".equalsIgnoreCase(biller.getStatus())) {
                metricsService.recordRequest(false);
                throw new InvalidTransferException(String.format(
                        "Cannot process payment: Biller %s (%s) is currently INACTIVE. Please select an active partner biller.",
                        biller.getName(), billerId));
            }
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            metricsService.recordRequest(false);
            throw new InvalidTransferException("Payment amount must be greater than zero");
        }

        // 1. Lock paying account
        Account account = accountRepository.findByAccountNumberWithLock(accNum)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accNum));

        // Deduplication safeguard: If an identical bill payment was completed within the last 2.5 seconds,
        // return the existing payment to prevent duplicate debits.
        String dedupKey = accNum + "->" + billerId + ":" + amount.stripTrailingZeros().toPlainString();
        long now = System.currentTimeMillis();
        Long lastExecutionTime = recentPaymentTimestamps.get(dedupKey);
        if (lastExecutionTime != null && (now - lastExecutionTime) < 2500L) {
            BillPayment existing = recentPayments.get(dedupKey);
            if (existing != null) {
                log.warn("Duplicate bill payment detected within {}ms for key {}. Returning existing payment {}",
                        (now - lastExecutionTime), dedupKey, existing.getPaymentId());
                return existing;
            }
        }

        // 2. Validate funds
        if (account.getBalance().compareTo(amount) < 0) {
            metricsService.recordRequest(false);
            throw new InsufficientFundsException(String.format(
                    "Insufficient funds for bill payment in account %s. Current balance: %s, Required: %s",
                    accNum, account.getBalance(), amount));
        }

        // 3. Debit balance
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        // 4. Central transactions ledger entry
        String txnId = idGenerator.generateId("TXN");
        String paymentId = idGenerator.generateId("BILL");
        String desc = String.format("Bill payment to %s", request.getBillerId());

        Transaction transaction = new Transaction(
                txnId,
                accNum,
                request.getBillerId(),
                amount,
                "BILL_PAYMENT",
                "SUCCESS",
                desc,
                Instant.now()
        );
        transactionRepository.save(transaction);

        // 5. Bill payment entity
        BillPayment payment = new BillPayment(
                paymentId,
                accNum,
                request.getBillerId(),
                amount,
                "SUCCESS",
                txnId,
                Instant.now()
        );
        BillPayment savedPayment = billPaymentRepository.save(payment);

        recentPayments.put(dedupKey, savedPayment);
        recentPaymentTimestamps.put(dedupKey, System.currentTimeMillis());
        if (recentPaymentTimestamps.size() > 500) {
            long cutoff = System.currentTimeMillis() - 60000L;
            recentPaymentTimestamps.entrySet().removeIf(e -> e.getValue() < cutoff);
            recentPayments.keySet().removeIf(k -> !recentPaymentTimestamps.containsKey(k));
        }

        if (shardConnectionManager != null && account.getShardId() != null) {
            shardConnectionManager.replicateBillPayment(account.getShardId(), savedPayment);
            shardConnectionManager.replicateTransaction(account.getShardId(), transaction);
            shardConnectionManager.replicateAccount(account.getShardId(), account);
        }

        metricsService.recordRequest(true);
        if (account.getShardId() != null) {
            metricsService.recordShardAccess(account.getShardId());
        }

        return savedPayment;
    }

    public List<BillPayment> getBillPaymentsByAccount(String accountNumber) {
        return billPaymentRepository.findByAccountNumberOrderByCreatedAtDesc(accountNumber);
    }
}
