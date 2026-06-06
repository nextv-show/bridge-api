package com.sanshuiyuan.settlement.application.guard;

public class SingleLimitExceededException extends RuntimeException {
    private final Long userId;
    private final Long amount;
    private final Long maxAllowed;

    public SingleLimitExceededException(Long userId, Long amount, Long maxAllowed) {
        super("Single withdrawal " + amount + " exceeds max " + maxAllowed);
        this.userId = userId;
        this.amount = amount;
        this.maxAllowed = maxAllowed;
    }

    public Long getUserId() { return userId; }
    public Long getAmount() { return amount; }
    public Long getMaxAllowed() { return maxAllowed; }
}
