-- 一证一号：身份证号确定性哈希（HMAC-SHA256），用于跨 openid 唯一性查询。
-- AES-GCM 用随机 IV 无法等值比对密文，故新增哈希列作为查询键。
-- 历史行（本迁移前）无哈希，保持 NULL；新发起的实名流程会写入。
ALTER TABLE kyc_records
  ADD COLUMN id_card_hash VARCHAR(64) NULL AFTER id_card_no_mask;

-- 仅建普通索引用于查询；唯一性是「同身份证不可跨不同 openid 重复 PASS」的业务规则，
-- 在应用层校验（INIT/FAIL/SUPERSEDED 及同 openid 重试合法共享哈希，不能用 DB 唯一约束）。
CREATE INDEX idx_kyc_id_card_hash ON kyc_records (id_card_hash);
