package com.sanshuiyuan.evidence.infra.antchain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 蚂蚁链客户端（默认启用 antchain.mock=true）。
 * 返回确定性的伪交易哈希，便于本地/测试联调；接入真实 SDK 时切换为 prod profile。
 */
@Component
@ConditionalOnProperty(name = "antchain.mock", havingValue = "true", matchIfMissing = true)
public class MockAntChainClient implements AntChainClient {

    @Override
    public String submit(String payloadHash) {
        return "mock_tx_" + payloadHash.substring(0, 16);
    }
}
