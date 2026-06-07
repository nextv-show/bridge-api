package com.sanshuiyuan.settlement.application.guard;

public class DailyLimitExceededException extends RuntimeException {
    private final Long userId;
    private final Long currentTotal;
    private final Long newAmount;
    private final Long maxAllowed;

    public DailyLimitExceededException(Long userId, Long currentTotal, Long newAmount, Long maxAllowed) {
        super("Daily withdrawal total " + (currentTotal + newAmount) + " exceeds max " + maxAllowed);
        this.userId = userId;
        this.currentTotal = currentTotal;
        this.newAmount = newAmount;
        this.maxAllowed = maxAllowed;
    }

    public Long getUserId() { return userId; }
    public Long getCurrentTotal() { return currentTotal; }
    public Long getNewAmount() { return newAmount; }
    public Long getMaxAllowed() { return maxAllowed; }
}
