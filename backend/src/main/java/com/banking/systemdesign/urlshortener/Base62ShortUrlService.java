package com.banking.systemdesign.urlshortener;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trivial baseline implementation of ShortUrlService using Base62 encoding on an atomic counter.
 */
@Component
public class Base62ShortUrlService implements ShortUrlService {

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final AtomicLong counter = new AtomicLong(100000); // Start with 6-char range
    private final Map<String, String> codeToUrl = new ConcurrentHashMap<>();
    private final Map<String, String> urlToCode = new ConcurrentHashMap<>();

    @Override
    public String shorten(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        return urlToCode.computeIfAbsent(longUrl, url -> {
            long id = counter.incrementAndGet();
            String code = encodeBase62(id);
            codeToUrl.put(code, url);
            return code;
        });
    }

    @Override
    public String resolve(String shortCode) {
        if (shortCode == null) {
            return null;
        }
        return codeToUrl.get(shortCode);
    }

    private String encodeBase62(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62.charAt((int) (num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }
}
