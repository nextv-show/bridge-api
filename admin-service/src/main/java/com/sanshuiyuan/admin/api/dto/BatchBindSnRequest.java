package com.sanshuiyuan.admin.api.dto;

import java.util.List;

/**
 * 批量绑定 SN 请求。
 *
 * @param sns            SN 列表
 * @param deviceAssetIds 指定设备 ID 列表；为 null/空时走自动分配模式
 */
public record BatchBindSnRequest(
        List<String> sns,
        List<Long> deviceAssetIds
) {}
