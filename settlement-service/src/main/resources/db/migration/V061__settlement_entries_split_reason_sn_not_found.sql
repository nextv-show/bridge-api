-- 新增分账原因 SN_NOT_FOUND：账单 SN 不在 device_assets 时全额归平台（见 SettlePostingUseCase）。
ALTER TABLE settlement_entries
  MODIFY COLUMN split_reason
    ENUM('NORMAL','STAGE_STEP','BLOCKED_PRE_INSTALL','SN_NOT_FOUND') NOT NULL DEFAULT 'NORMAL';
