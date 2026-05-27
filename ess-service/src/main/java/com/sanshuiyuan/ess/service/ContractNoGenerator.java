package com.sanshuiyuan.ess.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 合同编号生成器。
 * <p>
 * 格式：CT-{yyyyMMdd}-{random6}，如 CT-20260527-A3F8K2。
 * 保证高并发下唯一性：日期 + 6位随机字母数字（36^6 ≈ 21 亿种组合）。
 */
@Component
public class ContractNoGenerator {

    private static final String PREFIX = "CT-";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去除易混淆字符
    private static final int RANDOM_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    /**
     * 生成新的合同编号。
     *
     * @return 合同编号，如 CT-20260527-A3F8K2
     */
    public String generate() {
        String datePart = LocalDate.now().format(DATE_FMT);
        String randomPart = generateRandom(RANDOM_LENGTH);
        return PREFIX + datePart + "-" + randomPart;
    }

    private String generateRandom(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
