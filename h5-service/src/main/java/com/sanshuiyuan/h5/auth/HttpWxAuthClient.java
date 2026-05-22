package com.sanshuiyuan.h5.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * 调用微信 sns/oauth2/access_token 用 code 换 openid（公众号网页授权，snsapi_base）。
 * appId/appSecret 仅后端持有。openid 属敏感信息，绝不入日志。
 */
public class HttpWxAuthClient implements WxAuthClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWxAuthClient.class);
    private static final String OAUTH_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private final String appId;
    private final String appSecret;
    private final RestClient restClient;

    public HttpWxAuthClient(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.restClient = RestClient.create();
    }

    @Override
    public String code2openid(String code) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(OAUTH_URL + "?appid={appid}&secret={secret}&code={code}&grant_type=authorization_code",
                            appId, appSecret, code)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("微信网页授权请求失败", e);
            throw new BizException(ErrorCode.WX_AUTH_FAILED, "微信授权服务暂不可用");
        }
        if (body == null || body.hasNonNull("errcode") && body.get("errcode").asInt() != 0) {
            int errcode = body == null ? -1 : body.path("errcode").asInt(-1);
            log.warn("微信网页授权返回错误 errcode={}", errcode);
            throw new BizException(ErrorCode.WX_AUTH_FAILED, "微信授权失败，请重新进入");
        }
        String openid = body.path("openid").asText(null);
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.WX_AUTH_FAILED, "未获取到 openid");
        }
        return openid;
    }
}
