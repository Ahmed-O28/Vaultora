package com.banking.exception;

/**
 * Thrown when MPIN verification fails. This is a business validation error
 * (HTTP 400), NOT an authentication failure (HTTP 401). Distinguishing these
 * prevents the frontend from treating a wrong MPIN as session expiration.
 */
public class MpinVerificationException extends RuntimeException {
    public MpinVerificationException(String message) {
        super(message);
    }
}
