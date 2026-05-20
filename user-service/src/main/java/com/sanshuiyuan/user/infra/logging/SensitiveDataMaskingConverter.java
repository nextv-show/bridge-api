package com.sanshuiyuan.user.infra.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A.1.3 / NFR 合规：对日志消息中的 unionid / openid(_wx/_app) 值做脱敏，
 * 保证微信身份标识不以明文出库到日志。支持 JSON（"unionid":"x"）与 key=value（unionid=x）两种形式。
 *
 * <p>在 logback-spring.xml 中以 conversionRule `maskedMsg` 接入，替换默认 `%m`。
 */
public class SensitiveDataMaskingConverter extends MessageConverter {

    // 键（unionid / openid / openid_wx / openidApp ...）后跟 : 或 =，捕获其值
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(\"?(?:unionid|openid(?:_?[a-z]+)?)\"?\\s*[:=]\\s*\"?)([A-Za-z0-9_\\-]+)");

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        return mask(message);
    }

    /** 脱敏整条消息中所有敏感键的值。包级可见以便单测。 */
    static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        Matcher m = SENSITIVE.matcher(message);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (m.find()) {
            found = true;
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + maskValue(m.group(2))));
        }
        if (!found) {
            return message;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskValue(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
