-- V006 创建 contract_sn_bindings 表
-- 合同与设备 SN 码绑定关系表

CREATE TABLE contract_sn_bindings (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id           BIGINT         NOT NULL COMMENT '合同 ID',
    device_sn             VARCHAR(64)    NOT NULL COMMENT '设备 SN 码',
    binding_type          VARCHAR(32)    NOT NULL DEFAULT 'PRE_ALLOCATED' COMMENT '绑定类型: PRE_ALLOCATED/CONFIRMED/RELEASED',
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_contract_id (contract_id),
    INDEX idx_device_sn (device_sn),
    INDEX idx_binding_type (binding_type),
    UNIQUE KEY uk_contract_sn (contract_id, device_sn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='合同与设备 SN 码绑定关系表';
