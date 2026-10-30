package com.banking.repository;

import com.banking.model.Biller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillerRepository extends JpaRepository<Biller, String> {
    List<Biller> findByStatusIgnoreCase(String status);
}
