package com.banking.repository;

import com.banking.model.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    Optional<ShortUrl> findByTargetUrl(String targetUrl);

    Optional<ShortUrl> findByTransactionId(String transactionId);

    boolean existsByShortCode(String shortCode);
}
