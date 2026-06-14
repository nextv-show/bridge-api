package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SelfUseRequest {
    @JsonProperty("ship_to_name")
    private String shipToName;
    @JsonProperty("ship_to_phone")
    private String shipToPhone;
    @JsonProperty("ship_to_address")
    private String shipToAddress;
    private double lat;
    private double lng;

    // getters and setters
    public String getShipToName() { return shipToName; }
    public void setShipToName(String v) { this.shipToName = v; }
    public String getShipToPhone() { return shipToPhone; }
    public void setShipToPhone(String v) { this.shipToPhone = v; }
    public String getShipToAddress() { return shipToAddress; }
    public void setShipToAddress(String v) { this.shipToAddress = v; }
    public double getLat() { return lat; }
    public void setLat(double v) { this.lat = v; }
    public double getLng() { return lng; }
    public void setLng(double v) { this.lng = v; }
}
