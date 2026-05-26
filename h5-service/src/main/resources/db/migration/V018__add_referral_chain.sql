-- V018 关系链下单时刻快照列（008a-referral-schema-codec）。
-- h5_orders 在创建订单时快照当前用户的 L1/L2 邀请人 user_id，供后续返利核算追溯。
-- 仅快照、可空（兼容存量自然流量订单）；严禁以 grand_inviter_id 为条件做向上递归查询（L3+ 物理隔离）。
-- 注：canonical users 关系链列归 user-service(user_db)，见 V004__add_referral_chain.sql 与 T8a.1 校验文档。
ALTER TABLE h5_orders ADD COLUMN inviter_id BIGINT NULL COMMENT '下单时刻快照：L1 邀请人 user_id（自然流量为 null）';
ALTER TABLE h5_orders ADD COLUMN grand_inviter_id BIGINT NULL COMMENT '下单时刻快照：L2 间接邀请人 user_id（可 null）';
