package com.sanshuiyuan.water.wallet.application;

import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.wallet.domain.WalletTransaction;
import com.sanshuiyuan.water.wallet.infra.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询钱包流水（按时间倒序，游标分页）。游标取上一页末条的 createdAt，向更早翻页。
 */
@Service
public class GetWalletTransactionsUseCase {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final H5UserResolver userResolver;
    private final WalletTransactionRepository txnRepo;

    public GetWalletTransactionsUseCase(H5UserResolver userResolver, WalletTransactionRepository txnRepo) {
        this.userResolver = userResolver;
        this.txnRepo = txnRepo;
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactions(String openid, String cursor, int limit) {
        Long userId = userResolver.resolveUserId(openid);
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        LocalDateTime cursorTime = parseCursor(cursor);

        // 仓储层无 paging 方法，按用户取全量倒序后在内存按游标过滤+截断。
        // 单用户流水规模有限；后续如需可下沉为派生查询。
        return txnRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(t -> cursorTime == null || t.getCreatedAt() == null
                        || t.getCreatedAt().isBefore(cursorTime))
                .limit(effectiveLimit)
                .toList();
    }

    private LocalDateTime parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(cursor);
        } catch (Exception e) {
            return null;
        }
    }
}
