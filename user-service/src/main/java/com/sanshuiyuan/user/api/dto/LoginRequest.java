package com.sanshuiyuan.user.api.dto;

public class LoginRequest {
    private String jsCode;
    private String wxAuthCode;

    public String getJsCode() { return jsCode; }
    public void setJsCode(String jsCode) { this.jsCode = jsCode; }

    public String getWxAuthCode() { return wxAuthCode; }
    public void setWxAuthCode(String wxAuthCode) { this.wxAuthCode = wxAuthCode; }
}
