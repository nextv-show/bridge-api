package com.sanshuiyuan.water.common;

/**
 * 业务异常（钱包/充值公共层）。携带 {@link ErrorCode}，由 {@link GlobalExceptionHandler} 统一映射。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
