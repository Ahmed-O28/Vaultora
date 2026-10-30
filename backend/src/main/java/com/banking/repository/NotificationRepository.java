package com.banking.repository;

import com.banking.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    long countByCustomerIdAndSeenFalse(Long customerId);

    List<Notification> findByCustomerIdAndSeenFalse(Long customerId);

    Optional<Notification> findByIdAndCustomerId(Long id, Long customerId);

    boolean existsByCustomerIdAndReferenceId(Long customerId, String referenceId);

    Optional<Notification> findTopByCustomerIdAndReferenceIdOrderByCreatedAtDesc(Long customerId, String referenceId);
}
