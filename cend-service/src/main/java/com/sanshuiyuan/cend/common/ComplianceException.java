package com.sanshuiyuan.cend.common;

import java.util.List;

/**
 * 合规校验异常（金融监管铁律）。命中违禁词时抛出，携带命中的词列表用于告警/排障。
 * 出口校验（102 LandingConfigService）捕获后回退「上一份合规快照」，不外泄给 C 端；
 * 入口校验（105 写接口预留）将直接拒绝保存。
 */
public class ComplianceException extends BizException {

    private final List<String> hits;

    public ComplianceException(List<String> hits) {
        super(ErrorCode.COMPLIANCE_VIOLATION, "文案命中违禁词：" + String.join(", ", hits));
        this.hits = List.copyOf(hits);
    }

    public List<String> hits() {
        return hits;
    }
}
