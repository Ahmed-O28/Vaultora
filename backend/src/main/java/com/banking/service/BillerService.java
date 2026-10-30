package com.banking.service;

import com.banking.model.Biller;
import com.banking.repository.BillerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class BillerService {

    private static final Logger log = LoggerFactory.getLogger(BillerService.class);

    private final BillerRepository billerRepository;

    public BillerService(BillerRepository billerRepository) {
        this.billerRepository = billerRepository;
    }

    public List<Biller> getAllBillers() {
        return billerRepository.findAll();
    }

    public List<Biller> getActiveBillers() {
        return billerRepository.findByStatusIgnoreCase("ACTIVE");
    }

    public Optional<Biller> getBillerById(String billerId) {
        return billerRepository.findById(billerId);
    }

    @Transactional
    public Biller updateBillerStatus(String billerId, String status) {
        if (billerId == null || billerId.isBlank()) {
            throw new IllegalArgumentException("Biller ID cannot be null or empty");
        }
        Biller biller = billerRepository.findById(billerId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Biller not found with ID: " + billerId));

        String validatedStatus = "INACTIVE".equalsIgnoreCase(status) ? "INACTIVE" : "ACTIVE";
        biller.setStatus(validatedStatus);
        biller.setLastUpdated(Instant.now());
        Biller saved = billerRepository.save(biller);
        log.info("Admin updated biller {} ('{}') status to {}", billerId, biller.getName(), validatedStatus);
        return saved;
    }
}
