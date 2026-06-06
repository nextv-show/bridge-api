package com.sanshuiyuan.water.wallet.api;

import com.sanshuiyuan.water.common.ApiResponse;
import com.sanshuiyuan.water.common.ErrorCode;
import com.sanshuiyuan.water.wallet.application.TopupCallbackUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Dev 联调用：模拟一次成功的充值回调（伪造 wxTransactionId），直接走 {@link TopupCallbackUseCase} 落账逻辑。
 * 仅 dev profile 启用。
 */
@RestController
@RequestMapping("/api/w/wallet/topup")
@Profile("dev")
public class SimulateTopupCallbackController {

    private final TopupCallbackUseCase callbackUseCase;

    public SimulateTopupCallbackController(TopupCallbackUseCase callbackUseCase) {
        this.callbackUseCase = callbackUseCase;
    }

    @PostMapping("/simulate-callback")
    public Map<String, Object> simulate(@RequestBody Map<String, String> req) {
        String outTradeNo = req.get("outTradeNo");
        if (outTradeNo == null || outTradeNo.isBlank()) {
            return ApiResponse.error(ErrorCode.VALIDATION_FAILED, "outTradeNo required");
        }
        String fakeTxnId = "SIM-" + UUID.randomUUID();
        String code = callbackUseCase.applyPaid(outTradeNo, fakeTxnId, "{\"simulated\":true}");
        return ApiResponse.ok(Map.of("outTradeNo", outTradeNo, "transactionId", fakeTxnId, "result", code));
    }
}
