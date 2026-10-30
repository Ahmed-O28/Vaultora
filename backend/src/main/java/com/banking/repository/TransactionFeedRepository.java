package com.banking.repository;

import com.banking.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only "feed" view over the existing transactions ledger, used by the notification feed to discover
 * newly committed transactions without touching the transfer / bill-payment services.
 */
@Repository
public interface TransactionFeedRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByIdGreaterThanOrderByIdAsc(Long id);

    Optional<Transaction> findTopByOrderByIdDesc();
}
