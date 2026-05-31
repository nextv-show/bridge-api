package com.sanshuiyuan.user.referral;

/**
 * 昵称脱敏工具（014/015 共用）。规则：首字 + {@code *} + 尾字；2 字及以下仅「首字 + *」；空昵称返回空串。
 * 以 code point 计数，正确处理中文 / 含 emoji 的昵称。
 */
public final class NicknameMasker {

    private NicknameMasker() {
    }

    public static String mask(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "";
        }
        int cpCount = nickname.codePointCount(0, nickname.length());
        String first = nickname.substring(0, nickname.offsetByCodePoints(0, 1));
        if (cpCount <= 2) {
            return first + "*";
        }
        String last = nickname.substring(nickname.offsetByCodePoints(0, cpCount - 1));
        return first + "*" + last;
    }
}
