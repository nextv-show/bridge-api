package com.sanshuiyuan.asset.application;

/**
 * Thrown when a user attempts to access an order that belongs to another user.
 * Mapped to HTTP 403 FORBIDDEN by {@link com.sanshuiyuan.asset.api.GlobalExceptionHandler}.
 */
public class NotOrderOwnerException extends RuntimeException {
    public NotOrderOwnerException(String message) {
        super(message);
    }
}
