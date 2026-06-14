package com.sanshuiyuan.matching.request.api.dto;

public class SelfUseRequest {
    private String ship_to_name;
    private String ship_to_phone;
    private String ship_to_address;
    private double lat;
    private double lng;

    // getters and setters
    public String getShip_to_name() { return ship_to_name; }
    public void setShip_to_name(String v) { this.ship_to_name = v; }
    public String getShip_to_phone() { return ship_to_phone; }
    public void setShip_to_phone(String v) { this.ship_to_phone = v; }
    public String getShip_to_address() { return ship_to_address; }
    public void setShip_to_address(String v) { this.ship_to_address = v; }
    public double getLat() { return lat; }
    public void setLat(double v) { this.lat = v; }
    public double getLng() { return lng; }
    public void setLng(double v) { this.lng = v; }
}
