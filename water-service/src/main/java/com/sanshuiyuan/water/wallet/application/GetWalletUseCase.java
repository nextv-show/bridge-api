package com.sanshuiyuan.water.wallet.application;

import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.wallet.domain.ConsumerWallet;
import com.sanshuiyuan.water.wallet.infra.ConsumerWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询钱包余额。钱包不存在时按零余额惰性创建，保证 C 端首次访问也有稳定结果。
 */
@Service
public class GetWalletUseCase {

    private final H5UserResolver userResolver;
    private final ConsumerWalletRepository walletRepo;

    public GetWalletUseCase(H5UserResolver userResolver, ConsumerWalletRepository walletRepo) {
        this.userResolver = userResolver;
        this.walletRepo = walletRepo;
    }

    @Transactional
    public WalletInfo getWallet(String openid) {
        Long userId = userResolver.resolveUserId(openid);
        ConsumerWallet wallet = walletRepo.findByUserId(userId)
                .orElseGet(() -> walletRepo.save(ConsumerWallet.create(userId, 0L)));
        return new WalletInfo(wallet.getBalanceCents(), "CNY");
    }

    public record WalletInfo(Long balanceCents, String currency) {}
}
