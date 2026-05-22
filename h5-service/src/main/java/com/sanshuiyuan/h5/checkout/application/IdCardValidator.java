package com.sanshuiyuan.h5.checkout.application;

/** 中国大陆二代身份证号校验（18 位 + 末位校验码）。 */
public final class IdCardValidator {

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private IdCardValidator() {}

    public static boolean isValid(String idNo) {
        if (idNo == null || idNo.length() != 18) {
            return false;
        }
        String upper = idNo.toUpperCase();
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = upper.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            sum += (c - '0') * WEIGHTS[i];
        }
        char expected = CHECK_CODES[sum % 11];
        return expected == upper.charAt(17);
    }
}
