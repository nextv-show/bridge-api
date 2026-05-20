package com.sanshuiyuan.asset.infra.wxpay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * D.2.5: Wires the {@link WxPayCallbackVerifier} seam.
 *
 * <p>The two beans use {@code @Bean}-level conditions (rather than conditions on scanned
 * {@code @Component}s) because {@code @ConditionalOnMissingBean} only evaluates reliably against
 * beans already registered at the point of evaluation — {@code @Bean} methods in a single
 * {@code @Configuration} are processed top-to-bottom, so the SDK bean is considered before the
 * fallback's missing-bean check runs.
 *
 * <ul>
 *   <li>{@link SdkWxPayCallbackVerifier} — created only when {@code wxpay.api-v3-key} is present
 *       (prod with real credentials).</li>
 *   <li>{@link UnconfiguredWxPayCallbackVerifier} — created when no other verifier exists
 *       (dev/CI without credentials); fails closed.</li>
 * </ul>
 */
@Configuration
public class WxPayVerifierConfig {

    @Bean
    @ConditionalOnProperty(prefix = "wxpay", name = "api-v3-key", matchIfMissing = false)
    public WxPayCallbackVerifier sdkWxPayCallbackVerifier(
            @Value("${wxpay.merchant-id}") String merchantId,
            @Value("${wxpay.api-key-path}") String privateKeyPath,
            @Value("${wxpay.merchant-serial-number}") String merchantSerialNumber,
            @Value("${wxpay.api-v3-key}") String apiV3Key) {
        return new SdkWxPayCallbackVerifier(merchantId, privateKeyPath, merchantSerialNumber, apiV3Key);
    }

    @Bean
    @ConditionalOnMissingBean(WxPayCallbackVerifier.class)
    public WxPayCallbackVerifier unconfiguredWxPayCallbackVerifier() {
        return new UnconfiguredWxPayCallbackVerifier();
    }
}
