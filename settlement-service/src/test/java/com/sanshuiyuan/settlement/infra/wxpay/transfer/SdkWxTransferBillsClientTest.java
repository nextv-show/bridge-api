package com.sanshuiyuan.settlement.infra.wxpay.transfer;

import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SdkWxTransferBillsClientTest {

    private final HttpClient http = mock(HttpClient.class);
    private final SdkWxTransferBillsClient client = new SdkWxTransferBillsClient(
            http, "wxMP123", "1005", "水机运营服务商", "水机运营服务分成",
            "https://h5.sanshuiyuan.com/api/s/payout/callback");

    @Test
    void buildInitiateBody_containsAllRequiredFields() {
        String json = client.buildInitiateBody(new WxTransferBillsClient.TransferCommand(
                "OB_1", "openidA", 50, "水机运营服务分成（6月）", null));
        assertThat(json)
                .contains("\"appid\":\"wxMP123\"")
                .contains("\"out_bill_no\":\"OB_1\"")
                .contains("\"transfer_scene_id\":\"1005\"")
                .contains("\"openid\":\"openidA\"")
                .contains("\"transfer_amount\":50")
                .contains("\"transfer_remark\":\"水机运营服务分成（6月）\"")
                .contains("岗位类型").contains("水机运营服务商")
                .contains("报酬说明")
                .contains("notify_url");
    }

    @Test
    void initiate_parsesWaitUserConfirmResult() {
        SdkWxTransferBillsClient.TransferBillsResp d = new SdkWxTransferBillsClient.TransferBillsResp();
        d.outBillNo = "OB_1";
        d.transferBillNo = "TB_9";
        d.state = "WAIT_USER_CONFIRM";
        d.packageInfo = "PKG_X";
        @SuppressWarnings("unchecked")
        HttpResponse<SdkWxTransferBillsClient.TransferBillsResp> resp = mock(HttpResponse.class);
        when(resp.getServiceResponse()).thenReturn(d);
        when(http.execute(any(), eq(SdkWxTransferBillsClient.TransferBillsResp.class))).thenReturn(resp);

        WxTransferBillsClient.InitiateResult r = client.initiate(
                new WxTransferBillsClient.TransferCommand("OB_1", "openidA", 50, null, null));

        assertThat(r.accepted()).isTrue();
        assertThat(r.state()).isEqualTo("WAIT_USER_CONFIRM");
        assertThat(r.packageInfo()).isEqualTo("PKG_X");
        assertThat(r.transferBillNo()).isEqualTo("TB_9");
    }

    @Test
    void initiate_rejectsAmountOver2000_withoutCallingApi() {
        WxTransferBillsClient.InitiateResult r = client.initiate(
                new WxTransferBillsClient.TransferCommand("OB_2", "openidA", 200_000, null, null));
        assertThat(r.accepted()).isFalse();
        assertThat(r.errorCode()).isEqualTo("AMOUNT_OVER_LIMIT_V1");
        verifyNoInteractions(http);
    }

    @Test
    void query_parsesState() {
        SdkWxTransferBillsClient.TransferBillsResp d = new SdkWxTransferBillsClient.TransferBillsResp();
        d.state = "SUCCESS";
        d.transferBillNo = "TB_9";
        @SuppressWarnings("unchecked")
        HttpResponse<SdkWxTransferBillsClient.TransferBillsResp> resp = mock(HttpResponse.class);
        when(resp.getServiceResponse()).thenReturn(d);
        when(http.execute(any(), eq(SdkWxTransferBillsClient.TransferBillsResp.class))).thenReturn(resp);

        WxTransferBillsClient.QueryResult r = client.queryByOutBillNo("OB_1");

        assertThat(r.found()).isTrue();
        assertThat(r.state()).isEqualTo("SUCCESS");
        assertThat(r.transferBillNo()).isEqualTo("TB_9");
    }
}
