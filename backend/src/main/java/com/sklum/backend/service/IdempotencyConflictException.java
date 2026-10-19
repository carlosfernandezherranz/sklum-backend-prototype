package com.sklum.backend.service;

/** Thrown when a second request arrives with an Idempotency-Key whose first request is still
 *  mid-saga (genuinely concurrent duplicate, not a retry of a finished one). Mapped to 409 by
 *  GlobalExceptionHandler. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
