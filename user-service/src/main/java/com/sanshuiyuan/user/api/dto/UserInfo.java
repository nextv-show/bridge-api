package com.sanshuiyuan.user.api.dto;

import java.util.List;

public class UserInfo {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String activeRole;
    private List<String> roles;

    public UserInfo() {}

    public UserInfo(Long id, String nickname, String avatarUrl, String activeRole, List<String> roles) {
        this.id = id;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.activeRole = activeRole;
        this.roles = roles;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getActiveRole() { return activeRole; }
    public void setActiveRole(String activeRole) { this.activeRole = activeRole; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
