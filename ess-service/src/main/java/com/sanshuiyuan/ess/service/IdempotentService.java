package com.sanshuiyuan.ess.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局幂等处理服务。
 * <p>
 * 基于 contract_id + operation 组合键实现幂等控制。
 * 幂等键格式: {contractId}:{operation}
 * <p>
 * 当前使用内存存储，生产环境可替换为 Redis。
 */
@Service
public class IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);

    /** 幂等结果存储：key = contractId:operation, value = result */
    private final ConcurrentHashMap<String, IdempotentEntry> store = new ConcurrentHashMap<>();

    /** 幂等记录有效期（毫秒），默认 24 小时 */
    private static final long ENTRY_TTL_MS = 24 * 60 * 60 * 1000L;

    /**
     * 检查幂等键是否已存在，若存在则返回已有结果。
     *
     * @param key 幂等键（contractId:operation）
     * @return 已有结果（null 表示首次请求）
     */
    public String getExistingResult(String key) {
        IdempotentEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }

        // 检查是否过期
        if (System.currentTimeMillis() - entry.timestamp() > ENTRY_TTL_MS) {
            store.remove(key);
            return null;
        }

        log.debug("幂等命中 [key={}, result={}]", key, entry.result());
        return entry.result();
    }

    /**
     * 记录幂等结果。
     *
     * @param key    幂等键
     * @param result 操作结果
     */
    public void recordResult(String key, String result) {
        store.put(key, new IdempotentEntry(result, System.currentTimeMillis()));
        log.debug("记录幂等结果 [key={}, result={}]", key, result);
    }

    /**
     * 检查并执行（如果未执行过）。
     * <p>
     * 原子操作：如果幂等键不存在，执行 action 并记录结果。
     *
     * @param key    幂等键
     * @param action 要执行的操作
     * @return 操作结果（或已有结果）
     */
    public String executeIfAbsent(String key, java.util.function.Supplier<String> action) {
        String existing = getExistingResult(key);
        if (existing != null) {
            return existing;
        }

        String result = action.get();
        recordResult(key, result);
        return result;
    }

    /**
     * 清理过期记录（供定时任务调用）。
     */
    public int cleanExpiredEntries() {
        long now = System.currentTimeMillis();
        int[] count = {0};
        store.entrySet().removeIf(entry -> {
            if (now - entry.getValue().timestamp() > ENTRY_TTL_MS) {
                count[0]++;
                return true;
            }
            return false;
        });
        if (count[0] > 0) {
            log.info("清理过期幂等记录 {} 条", count[0]);
        }
        return count[0];
    }

    /**
     * 幂等记录条目。
     */
    private record IdempotentEntry(String result, long timestamp) {}
}
