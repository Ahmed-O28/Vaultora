package com.banking.repository;

import com.banking.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findBySourceAccountOrDestinationAccountOrderByTimestampDesc(
            String sourceAccount, String destinationAccount
    );

    java.util.Optional<Transaction> findByTransactionId(String transactionId);
}
