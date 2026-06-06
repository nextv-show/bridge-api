package com.sanshuiyuan.water.session.application;

import com.sanshuiyuan.water.common.BizException;
import com.sanshuiyuan.water.common.ErrorCode;
import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.device.domain.DevicePermission;
import com.sanshuiyuan.water.device.infra.DevicePermissionRepository;
import com.sanshuiyuan.water.device.infra.IotGatewayClient;
import com.sanshuiyuan.water.session.domain.WaterSession;
import com.sanshuiyuan.water.session.infra.WaterSessionRepository;
import com.sanshuiyuan.water.wallet.infra.ConsumerWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 开启取水会话。校验权限/在线/余额 → 落 ACTIVE 会话（uk_sn_active 兜底并发）→ 事务提交后下发 start 命令。
 * 下发失败则把会话置 ABORTED 并返回 504，避免幽灵会话。
 */
@Service
public class StartWaterSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartWaterSessionUseCase.class);

    private final H5UserResolver userResolver;
    private final DevicePermissionRepository permRepo;
    private final ConsumerWalletRepository walletRepo;
    private final WaterSessionRepository sessionRepo;
    private final PriceTierGateway priceTierGateway;
    private final IotGatewayClient iotGatewayClient;
    private final TransactionTemplate txTemplate;
    private final long minStartAmountCents;

    public StartWaterSessionUseCase(H5UserResolver userResolver, DevicePermissionRepository permRepo,
                                    ConsumerWalletRepository walletRepo, WaterSessionRepository sessionRepo,
                                    PriceTierGateway priceTierGateway, IotGatewayClient iotGatewayClient,
                                    PlatformTransactionManager txManager,
                                    @Value("${water.min-start-amount-cents:50}") long minStartAmountCents) {
        this.userResolver = userResolver;
        this.permRepo = permRepo;
        this.walletRepo = walletRepo;
        this.sessionRepo = sessionRepo;
        this.priceTierGateway = priceTierGateway;
        this.iotGatewayClient = iotGatewayClient;
        this.txTemplate = new TransactionTemplate(txManager);
        this.minStartAmountCents = minStartAmountCents;
    }

    public StartResult start(String openid, String sn) {
        Long userId = userResolver.resolveUserId(openid);

        // 1. 出水权限
        DevicePermission perm = permRepo.findBySn(sn).orElse(null);
        if (perm == null || !perm.isCanDispense()) {
            throw new BizException(ErrorCode.DEVICE_LOCKED);
        }

        // 2. 设备在线（V1 stub：默认在线）
        if (!isOnline(sn)) {
            throw new BizException(ErrorCode.DEVICE_OFFLINE);
        }

        // 3. 余额门槛
        long balance = walletRepo.findByUserId(userId).map(w -> w.getBalanceCents()).orElse(0L);
        if (balance < minStartAmountCents) {
            throw new BizException(ErrorCode.LOW_BALANCE);
        }

        // 4. 单价 + 最大可出水量（余额可买量的 90%）
        int price = priceTierGateway.getPricePerLiterCents(sn);
        long maxLitersMilli = (long) (Math.floor(balance / (double) price) * 1000 * 0.9);

        // 5. 落 ACTIVE 会话（uk_sn_active 并发兜底）
        WaterSession session;
        try {
            session = txTemplate.execute(status ->
                    sessionRepo.save(WaterSession.create(sn, userId, price)));
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.DEVICE_IN_USE);
        }

        // 6. 事务提交后下发 start；失败则中止会话
        try {
            iotGatewayClient.start(sn, session.getId(), maxLitersMilli);
        } catch (Exception e) {
            log.error("下发 start 失败，置会话 ABORTED sessionId={} sn={}", session.getId(), sn, e);
            Long abortId = session.getId();
            txTemplate.executeWithoutResult(s -> sessionRepo.markAborted(abortId));
            throw new BizException(ErrorCode.IOT_COMMAND_FAILED);
        }

        log.info("开启取水会话成功 sessionId={} sn={} userId={} price={} maxLiters={}",
                session.getId(), sn, userId, price, maxLitersMilli);
        return new StartResult(session.getId(), maxLitersMilli, price);
    }

    /** 设备在线判断（V1 stub）。后续接 iot-gateway device_status 跨库/接口查询。 */
    private boolean isOnline(String sn) {
        return true;
    }

    /** 开启结果：会话 id + 最大可出水量（毫升）+ 每升单价（分）。 */
    public record StartResult(Long sessionId, long maxLitersMilli, int pricePerLiterCents) {}
}
