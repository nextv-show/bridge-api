package com.sanshuiyuan.ess.exception;

/**
 * 签署流程异常。
 */
public class EssFlowException extends RuntimeException {

    private final String contractId;
    private final String flowId;

    public EssFlowException(String contractId, String message) {
        super(String.format("签署流程异常 [contractId=%s]: %s", contractId, message));
        this.contractId = contractId;
        this.flowId = null;
    }

    public EssFlowException(String contractId, String flowId, String message) {
        super(String.format("签署流程异常 [contractId=%s, flowId=%s]: %s", contractId, flowId, message));
        this.contractId = contractId;
        this.flowId = flowId;
    }

    public String getContractId() { return contractId; }
    public String getFlowId() { return flowId; }
}
