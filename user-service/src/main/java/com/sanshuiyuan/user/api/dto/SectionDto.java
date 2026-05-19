package com.sanshuiyuan.user.api.dto;

import java.util.Map;

public class SectionDto {
    private String key;
    private String title;
    private int order;
    private Map<String, Object> props;

    public SectionDto() {}

    public SectionDto(String key, String title, int order, Map<String, Object> props) {
        this.key = key;
        this.title = title;
        this.order = order;
        this.props = props;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public Map<String, Object> getProps() { return props; }
    public void setProps(Map<String, Object> props) { this.props = props; }
}
