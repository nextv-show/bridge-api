package com.sanshuiyuan.settlement.application.guard;

public class DailyCountExceededException extends RuntimeException {
    private final Long userId;
    private final Long todayCount;
    private final Integer maxCount;

    public DailyCountExceededException(Long userId, Long todayCount, Integer maxCount) {
        super("Daily withdrawal count " + todayCount + " reaches max " + maxCount);
        this.userId = userId;
        this.todayCount = todayCount;
        this.maxCount = maxCount;
    }

    public Long getUserId() { return userId; }
    public Long getTodayCount() { return todayCount; }
    public Integer getMaxCount() { return maxCount; }
}
