package com.banking.systemdesign.urlshortener;

/**
 * Component 6: URL Shortener
 * 
 * Responsibility: Generate compact short links for transaction receipts, payment links,
 * and account confirmation notifications (e.g. https://bank.local/r/7xQz8).
 * 
 * Future Production Implementation:
 * Distributed Base62 counter or MD5 hash with collision resolution, backed by Redis and PostgreSQL.
 */
public interface ShortUrlService {

    /**
     * Shortens a long URL to a short code identifier.
     */
    String shorten(String longUrl);

    /**
     * Resolves a short code back to its original long URL.
     */
    String resolve(String shortCode);
}
