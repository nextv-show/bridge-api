package com.sanshuiyuan.ess.exception;

/**
 * 腾讯电子签 API 调用异常。
 * <p>
 * 签署链路故障时抛出，不得静默。
 */
public class EssApiException extends RuntimeException {

    private final String apiAction;
    private final Integer statusCode;

    public EssApiException(String apiAction, String message) {
        super(String.format("ESS API [%s] 调用失败: %s", apiAction, message));
        this.apiAction = apiAction;
        this.statusCode = null;
    }

    public EssApiException(String apiAction, Integer statusCode, String message) {
        super(String.format("ESS API [%s] HTTP %d 调用失败: %s", apiAction, statusCode, message));
        this.apiAction = apiAction;
        this.statusCode = statusCode;
    }

    public EssApiException(String apiAction, String message, Throwable cause) {
        super(String.format("ESS API [%s] 调用失败: %s", apiAction, message), cause);
        this.apiAction = apiAction;
        this.statusCode = null;
    }

    public String getApiAction() { return apiAction; }
    public Integer getStatusCode() { return statusCode; }
}
