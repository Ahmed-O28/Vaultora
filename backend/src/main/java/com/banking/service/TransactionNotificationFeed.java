package com.banking.service;

import com.banking.model.Account;
import com.banking.model.Transaction;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionFeedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches the transactions ledger and turns every newly committed SUCCESS transaction into push
 * notifications for the customers involved:
 * <ul>
 *   <li>TRANSFER: debit alert for the sender, credit alert for the receiver</li>
 *   <li>BILL_PAYMENT: receipt for the paying customer</li>
 * </ul>
 * Because it reads from the ledger after commit, notifications are only ever sent for transactions that really
 * succeeded, and the transfer / bill-payment services need no changes.
 */
@Component
public class TransactionNotificationFeed {

    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationFeed.class);
    private static final long DEBOUNCE_WINDOW_MS = 5000L;

    private final TransactionFeedRepository transactionFeedRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    /** Highest transaction row id already processed; null until the first poll initialises it. */
    private Long watermark = null;

    /** Tracks recently alerted keys to debounce duplicate transactions submitted within milliseconds. */
    private final Map<String, Long> recentAlerts = new ConcurrentHashMap<>();

    public TransactionNotificationFeed(TransactionFeedRepository transactionFeedRepository,
                                       AccountRepository accountRepository,
                                       NotificationService notificationService) {
        this.transactionFeedRepository = transactionFeedRepository;
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(initialDelay = 2000L, fixedDelay = 1000L)
    public void scheduledPoll() {
        try {
            pollOnce();
        } catch (RuntimeException e) {
            log.warn("Notification feed poll failed: {}", e.toString());
        }
    }

    /**
     * Processes transactions created since the previous call.
     * The very first call only records the current position, so historical transactions never
     * trigger notifications. Returns the number of transactions processed.
     */
    public synchronized int pollOnce() {
        if (watermark == null) {
            watermark = transactionFeedRepository.findTopByOrderByIdDesc()
                    .map(Transaction::getId)
                    .orElse(0L);
            return 0;
        }

        List<Transaction> fresh = transactionFeedRepository.findByIdGreaterThanOrderByIdAsc(watermark);
        for (Transaction txn : fresh) {
            try {
                notifyFor(txn);
            } catch (RuntimeException e) {
                log.warn("Could not create notification for transaction {}: {}", txn.getTransactionId(), e.toString());
            }
            if (txn.getId() != null && txn.getId() > watermark) {
                watermark = txn.getId();
            }
        }
        return fresh.size();
    }

    private boolean isDuplicateAlert(String key, long now) {
        Long prev = recentAlerts.get(key);
        if (prev != null && (now - prev) < DEBOUNCE_WINDOW_MS) {
            return true;
        }
        recentAlerts.put(key, now);
        return false;
    }

    private void notifyFor(Transaction txn) {
        if (!"SUCCESS".equalsIgnoreCase(txn.getStatus())) {
            return;
        }
        String amount = String.format(Locale.US, "$%,.2f", txn.getAmount());
        String type = txn.getType() == null ? "" : txn.getType();
        long now = System.currentTimeMillis();

        if (recentAlerts.size() > 500) {
            recentAlerts.entrySet().removeIf(e -> (now - e.getValue()) > 60000L);
        }

        if ("TRANSFER".equalsIgnoreCase(type)) {
            Long senderId = customerIdOf(txn.getSourceAccount());
            Long receiverId = customerIdOf(txn.getDestinationAccount());
            if (senderId != null && senderId.equals(receiverId)) {
                // Internal transfer between accounts belonging to the same customer: send single notification
                String key = senderId + ":INTERNAL:" + txn.getSourceAccount() + ":" + txn.getDestinationAccount() + ":" + amount;
                if (!isDuplicateAlert(key, now)) {
                    notificationService.notifyCustomer(senderId, "TRANSFER", "Account transfer successful",
                            amount + " was transferred from " + txn.getSourceAccount()
                                    + " to " + txn.getDestinationAccount() + ". Ref: " + txn.getTransactionId(),
                            txn.getTransactionId());
                } else {
                    log.info("Duplicate internal transfer notification suppressed for customer {} within 5s: {}", senderId, txn.getTransactionId());
                }
            } else {
                if (senderId != null) {
                    String key = senderId + ":DEBIT:" + txn.getSourceAccount() + ":" + txn.getDestinationAccount() + ":" + amount;
                    if (!isDuplicateAlert(key, now)) {
                        notificationService.notifyCustomer(senderId, "TRANSFER_DEBIT", "Money sent",
                                amount + " was debited from " + txn.getSourceAccount()
                                        + " and sent to " + txn.getDestinationAccount() + ". Ref: " + txn.getTransactionId(),
                                txn.getTransactionId());
                    } else {
                        log.info("Duplicate debit notification suppressed for customer {} within 5s: {}", senderId, txn.getTransactionId());
                    }
                }
                if (receiverId != null) {
                    String key = receiverId + ":CREDIT:" + txn.getSourceAccount() + ":" + txn.getDestinationAccount() + ":" + amount;
                    if (!isDuplicateAlert(key, now)) {
                        notificationService.notifyCustomer(receiverId, "TRANSFER_CREDIT", "Money received",
                                amount + " was credited to " + txn.getDestinationAccount()
                                        + " from " + txn.getSourceAccount() + ". Ref: " + txn.getTransactionId(),
                                txn.getTransactionId());
                    } else {
                        log.info("Duplicate credit notification suppressed for customer {} within 5s: {}", receiverId, txn.getTransactionId());
                    }
                }
            }
        } else if ("BILL_PAYMENT".equalsIgnoreCase(type)) {
            Long payerId = customerIdOf(txn.getSourceAccount());
            if (payerId != null) {
                String key = payerId + ":BILL:" + txn.getSourceAccount() + ":" + txn.getDestinationAccount() + ":" + amount;
                if (!isDuplicateAlert(key, now)) {
                    notificationService.notifyCustomer(payerId, "BILL_PAYMENT", "Bill payment successful",
                            amount + " was paid to " + txn.getDestinationAccount()
                                    + " from " + txn.getSourceAccount() + ". Ref: " + txn.getTransactionId(),
                            txn.getTransactionId());
                } else {
                    log.info("Duplicate bill payment notification suppressed for customer {} within 5s: {}", payerId, txn.getTransactionId());
                }
            }
        }
    }

    private Long customerIdOf(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        Optional<Account> account = accountRepository.findByAccountNumber(accountNumber);
        return account.map(Account::getCustomerId).orElse(null);
    }
}
