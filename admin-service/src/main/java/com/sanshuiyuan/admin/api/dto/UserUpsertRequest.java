package com.sanshuiyuan.admin.api.dto;

import java.util.List;

/** C 端用户新建 / 编辑请求体。 */
public record UserUpsertRequest(
        String phone,
        String name,
        String gender,
        Integer age,
        String channel,
        String tier,
        List<String> tags,
        String city,
        String note
) {}
