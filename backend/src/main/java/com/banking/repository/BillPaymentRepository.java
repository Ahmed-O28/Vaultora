package com.banking.repository;

import com.banking.model.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    List<BillPayment> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
