ALTER TABLE kyc_records
  ADD COLUMN phone_enc VARBINARY(256) COMMENT '手机号(AES-GCM加密)' AFTER id_card_no_enc,
  ADD COLUMN phone_mask VARCHAR(16) COMMENT '手机号脱敏(如138****8888)' AFTER phone_enc;
