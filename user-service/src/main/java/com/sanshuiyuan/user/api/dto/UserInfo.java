package com.sanshuiyuan.user.api.dto;

import java.util.List;

public class UserInfo {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String activeRole;
    private List<String> roles;
    /** L1 邀请人 user_id（只读输出，自然流量为 null）。关系链仅首次注册写入，无任何 API 可改（008b）。 */
    private Long inviterId;
    /** L2 间接邀请人 user_id（只读输出，可 null）。仅单条快照展示，严禁向上递归（L3+ 物理隔离）。 */
    private Long grandInviterId;

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

    public Long getInviterId() { return inviterId; }
    public void setInviterId(Long inviterId) { this.inviterId = inviterId; }

    public Long getGrandInviterId() { return grandInviterId; }
    public void setGrandInviterId(Long grandInviterId) { this.grandInviterId = grandInviterId; }
}
