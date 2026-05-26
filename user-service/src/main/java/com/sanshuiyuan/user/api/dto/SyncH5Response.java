package com.sanshuiyuan.user.api.dto;

/**
 * H5 账号同步出参（spec 012）。
 *
 * @param userId       统一用户体系中的 user_id。
 * @param isNew        本次调用是否新建了用户（幂等：重复调用为 false）。
 * @param inviterBound 该用户是否已绑定 L1 邀请人（仅反映已落库的关系链，不代表本次写入）。
 */
public record SyncH5Response(
        Long userId,
        boolean isNew,
        boolean inviterBound) {
}
