package com.sanshuiyuan.water.wallet.application;

import com.sanshuiyuan.water.common.BizException;
import com.sanshuiyuan.water.common.ErrorCode;
import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.wallet.domain.WalletTopup;
import com.sanshuiyuan.water.wallet.infra.WalletTopupRepository;
import com.sanshuiyuan.water.wallet.infra.wxpay.WxPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 发起充值：校验金额 → 生成商户订单号 → 微信 JSAPI 下单 → 落 PENDING 充值单 → 返回前端拉起支付所需参数。
 */
@Service
public class TopupUseCase {

    private static final Logger log = LoggerFactory.getLogger(TopupUseCase.class);

    private final H5UserResolver userResolver;
    private final WxPayClient wxPayClient;
    private final WalletTopupRepository topupRepo;
    private final long minTopupCents;

    public TopupUseCase(H5UserResolver userResolver, WxPayClient wxPayClient,
                        WalletTopupRepository topupRepo,
                        @Value("${wallet.min-topup-cents:500}") long minTopupCents) {
        this.userResolver = userResolver;
        this.wxPayClient = wxPayClient;
        this.topupRepo = topupRepo;
        this.minTopupCents = minTopupCents;
    }

    @Transactional
    public TopupResult topup(String openid, Long amountCents) {
        if (amountCents == null || amountCents <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "充值金额无效");
        }
        if (amountCents < minTopupCents) {
            throw new BizException(ErrorCode.TOPUP_MIN_AMOUNT,
                    "充值金额不得低于 " + (minTopupCents / 100) + " 元");
        }
        Long userId = userResolver.resolveUserId(openid);

        String outTradeNo = "WT_" + userId + "_" + System.currentTimeMillis()
                + "_" + UUID.randomUUID().toString().substring(0, 8);

        WxPayClient.PrepayResult prepay = wxPayClient.jsapiPrepay(outTradeNo, openid, amountCents, "钱包充值");

        WalletTopup topup = WalletTopup.create(userId, amountCents, outTradeNo, prepay.prepayId());
        topup = topupRepo.save(topup);

        log.info("充值下单成功 topupId={} userId={} amount={} outTradeNo={}",
                topup.getId(), userId, amountCents, outTradeNo);
        return new TopupResult(topup.getId(), prepay);
    }

    /** 充值下单结果：充值单 id + 前端拉起微信支付所需参数。 */
    public record TopupResult(Long topupId, WxPayClient.PrepayResult wxPrepayParams) {}
}
