package com.sanshuiyuan.asset.application;

/**
 * Thrown when an order references a SKU that does not exist or is not ACTIVE.
 * Mapped to HTTP 409 CONFLICT by {@link com.sanshuiyuan.asset.api.GlobalExceptionHandler}.
 */
public class SkuUnavailableException extends RuntimeException {
    public SkuUnavailableException(String message) {
        super(message);
    }
}
