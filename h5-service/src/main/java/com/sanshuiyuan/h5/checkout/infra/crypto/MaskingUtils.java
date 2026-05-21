package com.sanshuiyuan.h5.checkout.infra.crypto;

public final class MaskingUtils {

    private MaskingUtils() {}

    public static String maskIdCard(String idCardNo) {
        if (idCardNo == null || idCardNo.length() < 5) return "***";
        return idCardNo.substring(0, 3) + "*************" + idCardNo.substring(idCardNo.length() - 2);
    }

    public static String maskRealName(String realName) {
        if (realName == null || realName.isEmpty()) return "***";
        return realName.charAt(0) + " **";
    }
}
