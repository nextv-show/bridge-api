package com.sanshuiyuan.evidence.infra.antchain;

/** 蚂蚁链存证客户端：提交载荷哈希上链，返回交易哈希。 */
public interface AntChainClient {

    /** Submit payload hash to blockchain, return tx hash. */
    String submit(String payloadHash);
}
