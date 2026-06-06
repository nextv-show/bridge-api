package com.sanshuiyuan.settlement.application.guard;

public class KycNotVerifiedException extends RuntimeException {
    private final Long userId;

    public KycNotVerifiedException(Long userId) {
        super("KYC not verified for user " + userId);
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
}
