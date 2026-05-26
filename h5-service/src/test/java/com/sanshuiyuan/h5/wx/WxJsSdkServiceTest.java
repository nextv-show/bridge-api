package com.sanshuiyuan.h5.wx;

import com.sanshuiyuan.h5.wx.api.dto.JsSdkSignatureResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T9.3：JS-SDK 签名生成。用微信官方文档的测试向量校验 SHA1 拼接正确，
 * 并验证 ticket 不可用（stub）时降级为空 signature。
 */
class WxJsSdkServiceTest {

    @Test
    void buildSignature_matchesWechatOfficialVector() {
        // 微信 JS-SDK 文档「附录1-JS-SDK使用权限签名算法」给出的标准测试向量。
        String ticket = "sM4AOVdWfPE4DxkXGEs8VMCPGGVi4C3VM0P37wVUCFvkVAy_90u5h9nbSlYy3-Sl-HhTdfl2fzFy1AOcHKP7qg";
        String nonceStr = "Wm3WZYTPz0wzccnW";
        long timestamp = 1414587457L;
        String url = "http://mp.weixin.qq.com?params=value";

        String signature = WxJsSdkService.buildSignature(ticket, nonceStr, timestamp, url);

        assertThat(signature).isEqualTo("0f9de62fce790f9a083d5c99e95740ceb90c27ed");
    }

    @Test
    void sign_withoutTicket_returnsEmptySignatureButValidEnvelope() {
        WxJsapiTicketService ticketService = Mockito.mock(WxJsapiTicketService.class);
        Mockito.when(ticketService.getTicket()).thenReturn("");
        WxJsSdkService service = new WxJsSdkService(ticketService, "wxTestAppId");

        JsSdkSignatureResponse resp = service.sign("https://h5.sanshuiyuan.com/");

        assertThat(resp.appId()).isEqualTo("wxTestAppId");
        assertThat(resp.timestamp()).isNotBlank();
        assertThat(resp.nonceStr()).isNotBlank();
        // ticket 缺失 → signature 为空，前端据此跳过 wx.config。
        assertThat(resp.signature()).isEmpty();
    }

    @Test
    void sign_withTicket_producesNonEmptySignature() {
        WxJsapiTicketService ticketService = Mockito.mock(WxJsapiTicketService.class);
        Mockito.when(ticketService.getTicket()).thenReturn("a-real-jsapi-ticket-value");
        WxJsSdkService service = new WxJsSdkService(ticketService, "wxTestAppId");

        JsSdkSignatureResponse resp = service.sign("https://h5.sanshuiyuan.com/page");

        assertThat(resp.signature()).hasSize(40); // SHA1 hex = 40 字符
    }
}
