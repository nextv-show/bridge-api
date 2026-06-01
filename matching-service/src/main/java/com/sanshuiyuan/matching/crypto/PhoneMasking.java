package com.sanshuiyuan.matching.crypto;

/** 手机号工具：规范化 + 脱敏。 */
public final class PhoneMasking {

    private PhoneMasking() {}

    /** 规范化：去空格、连字符、中文/英文括号。 */
    public static String normalize(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[\\s\\-()（）]", "").trim();
    }

    /** 脱敏：保留前3后4，中间 ****（138****5678）。非11位返回 ***。 */
    public static String mask(String phone) {
        if (phone == null) return "***";
        String p = phone.trim();
        if (p.length() != 11) return "***";
        return p.substring(0, 3) + "****" + p.substring(7);
    }
}
