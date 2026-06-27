package com.sanshuiyuan.settlement.application.guard;

public class BelowMinimumException extends RuntimeException {
    private final Long userId;
    private final Long requestedCents;
    private final Long minCents;

    public BelowMinimumException(Long userId, Long requestedCents, Long minCents) {
        super("Withdrawal " + requestedCents + " below minimum " + minCents);
        this.userId = userId;
        this.requestedCents = requestedCents;
        this.minCents = minCents;
    }

    public Long getUserId() { return userId; }
    public Long getRequestedCents() { return requestedCents; }
    public Long getMinCents() { return minCents; }
}
