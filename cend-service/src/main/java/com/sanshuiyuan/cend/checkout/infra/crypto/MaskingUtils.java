package com.sanshuiyuan.cend.checkout.infra.crypto;

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

    /** 手机号脱敏：138****8888 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
