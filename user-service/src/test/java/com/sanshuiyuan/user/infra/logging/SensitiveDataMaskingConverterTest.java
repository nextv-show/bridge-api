package com.sanshuiyuan.user.infra.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F.2: 验证 unionid / openid 在日志消息中被脱敏，明文不再出现。
 */
class SensitiveDataMaskingConverterTest {

    private final SensitiveDataMaskingConverter converter = new SensitiveDataMaskingConverter();

    private String convert(String rawMessage) {
        Logger logger = (Logger) LoggerFactory.getLogger(SensitiveDataMaskingConverterTest.class);
        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(), logger, ch.qos.logback.classic.Level.INFO, rawMessage, null, null);
        return converter.convert(event);
    }

    @Test
    void masksJsonStyleUnionidAndOpenid() {
        String out = convert("login {\"unionid\":\"oABCDEF12345\",\"openid_wx\":\"wxQRSTUV6789\"}");
        assertThat(out).doesNotContain("oABCDEF12345");
        assertThat(out).doesNotContain("wxQRSTUV6789");
        assertThat(out).contains("oA****45");
        assertThat(out).contains("wx****89");
    }

    @Test
    void masksKeyValueStyle() {
        String out = convert("resolved unionid=oZZZZZZ999 for request");
        assertThat(out).doesNotContain("oZZZZZZ999");
        assertThat(out).contains("oZ****99");
    }

    @Test
    void masksCamelCaseOpenidApp() {
        String out = convert("openidApp=appTOKEN1234 bound");
        assertThat(out).doesNotContain("appTOKEN1234");
    }

    @Test
    void leavesNonSensitiveMessageUntouched() {
        String msg = "user 42 switched active role to OWNER";
        assertThat(convert(msg)).isEqualTo(msg);
    }

    @Test
    void shortValueFullyMasked() {
        assertThat(SensitiveDataMaskingConverter.mask("openid=abc")).contains("openid=****");
    }
}
