package com.sanshuiyuan.settlement.infra.wxpay.transfer;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.http.HttpHeaders;
import com.wechat.pay.java.core.http.HttpMethod;
import com.wechat.pay.java.core.http.HttpRequest;
import com.wechat.pay.java.core.http.HttpResponse;
import com.wechat.pay.java.core.http.JsonRequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实「商家转账」V3 实现：用微信支付 SDK 的签名 HttpClient（公钥模式，自动签名+验签）调
 * /v3/fund-app/mch-transfer/transfer-bills。不升级 SDK（0.2.14 无该接口），手写请求体。
 *
 * <p>V1 限额 <¥2000，不走 user_name 实名加密分支（≥¥2000 直接拒绝，由 WithdrawalPolicy 单笔上限兜底）。
 */
public class SdkWxTransferBillsClient implements WxTransferBillsClient {

    private static final Logger log = LoggerFactory.getLogger(SdkWxTransferBillsClient.class);
    private static final String HOST = "https://api.mch.weixin.qq.com";
    private static final String INITIATE_PATH = "/v3/fund-app/mch-transfer/transfer-bills";
    private static final long REAL_NAME_THRESHOLD_CENTS = 200_000L; // ¥2000

    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final String appId;
    private final String sceneId;
    private final String reportPostType;
    private final String reportRemark;
    private final String notifyUrl;

    public SdkWxTransferBillsClient(HttpClient httpClient, String appId, String sceneId,
                                    String reportPostType, String reportRemark, String notifyUrl) {
        this.httpClient = httpClient;
        this.appId = appId;
        this.sceneId = sceneId;
        this.reportPostType = reportPostType;
        this.reportRemark = reportRemark;
        this.notifyUrl = notifyUrl;
    }

    @Override
    public InitiateResult initiate(TransferCommand cmd) {
        if (cmd.amountCents() >= REAL_NAME_THRESHOLD_CENTS || cmd.userName() != null) {
            // V1 不支持 ≥¥2000 的 user_name 实名加密；应由提现单笔上限 (<¥2000) 拦在前面。
            log.warn("[payout] 拒绝转账：金额 {} 分 ≥¥2000 需实名加密，V1 未支持 outBillNo={}",
                    cmd.amountCents(), cmd.outBillNo());
            return InitiateResult.failed("AMOUNT_OVER_LIMIT_V1", "单笔≥¥2000 需实名加密，当前版本不支持");
        }
        String body = buildInitiateBody(cmd);
        HttpHeaders headers = new HttpHeaders();
        headers.addHeader("Accept", "application/json");
        headers.addHeader("Content-Type", "application/json");
        HttpRequest request = new HttpRequest.Builder()
                .httpMethod(HttpMethod.POST)
                .url(HOST + INITIATE_PATH)
                .headers(headers)
                .body(new JsonRequestBody.Builder().body(body).build())
                .build();
        try {
            HttpResponse<TransferBillsResp> resp = httpClient.execute(request, TransferBillsResp.class);
            TransferBillsResp d = resp.getServiceResponse();
            log.info("[payout] transfer-bills 受理 outBillNo={} transferBillNo={} state={}",
                    d.outBillNo, d.transferBillNo, d.state);
            return InitiateResult.ok(d.outBillNo, d.transferBillNo, d.state, d.packageInfo);
        } catch (ServiceException e) {
            // 4xx/5xx 业务错误：勿立即换单重试，需先查单确认（官方铁律）。
            log.warn("[payout] transfer-bills 业务失败 outBillNo={} code={} msg={}",
                    cmd.outBillNo(), e.getErrorCode(), e.getErrorMessage());
            return InitiateResult.failed(e.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            log.error("[payout] transfer-bills 调用异常 outBillNo={}: {}", cmd.outBillNo(), e.toString());
            return InitiateResult.failed("REQUEST_ERROR", e.getMessage());
        }
    }

    @Override
    public QueryResult queryByOutBillNo(String outBillNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.addHeader("Accept", "application/json");
        HttpRequest request = new HttpRequest.Builder()
                .httpMethod(HttpMethod.GET)
                .url(HOST + INITIATE_PATH + "/out-bill-no/" + outBillNo)
                .headers(headers)
                .build();
        try {
            HttpResponse<TransferBillsResp> resp = httpClient.execute(request, TransferBillsResp.class);
            TransferBillsResp d = resp.getServiceResponse();
            return new QueryResult(true, d.state, d.failReason, d.transferBillNo);
        } catch (ServiceException e) {
            if (e.getHttpStatusCode() == 404) {
                return QueryResult.notFound();
            }
            log.warn("[payout] 查单业务失败 outBillNo={} code={} msg={}",
                    outBillNo, e.getErrorCode(), e.getErrorMessage());
            return QueryResult.error(e.getErrorCode());
        } catch (Exception e) {
            log.error("[payout] 查单异常 outBillNo={}: {}", outBillNo, e.toString());
            return QueryResult.error("QUERY_ERROR");
        }
    }

    /** 构造 transfer-bills 请求体 JSON（包可见，便于单测断言字段）。 */
    String buildInitiateBody(TransferCommand cmd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appid", appId);
        body.put("out_bill_no", cmd.outBillNo());
        body.put("transfer_scene_id", sceneId);
        body.put("openid", cmd.openid());
        body.put("transfer_amount", cmd.amountCents());
        body.put("transfer_remark", (cmd.transferRemark() != null && !cmd.transferRemark().isBlank())
                ? cmd.transferRemark() : reportRemark);
        body.put("transfer_scene_report_infos", List.of(
                Map.of("info_type", "岗位类型", "info_content", reportPostType),
                Map.of("info_type", "报酬说明", "info_content", reportRemark)));
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            body.put("notify_url", notifyUrl);
        }
        return gson.toJson(body);
    }

    /** transfer-bills 发起/查单响应（字段并集）。Gson 反序列化，字段包可见便于单测。 */
    static class TransferBillsResp {
        @SerializedName("out_bill_no") String outBillNo;
        @SerializedName("transfer_bill_no") String transferBillNo;
        @SerializedName("state") String state;
        @SerializedName("package_info") String packageInfo;
        @SerializedName("fail_reason") String failReason;
    }
}
