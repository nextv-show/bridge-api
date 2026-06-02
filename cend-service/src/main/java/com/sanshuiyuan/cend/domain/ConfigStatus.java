package com.sanshuiyuan.cend.domain;

/**
 * 落地页配置状态。本模块只读 PUBLISHED；DRAFT/ARCHIVED 由 105 写/发布/回滚流转。
 */
public enum ConfigStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
