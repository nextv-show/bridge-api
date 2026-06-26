-- V005 删除旧推荐链 grand_inviter 索引（user-service 推荐链下线收口）。
-- 背景：V004 曾为旧推荐链创建 idx_grand_inviter ON users (grand_inviter_id)；
-- 当前推荐链主链已迁移至 cend-service（h5_users.inviter_id / grand_inviter_id），
-- user-service 不再作为小程序推荐链主链。
-- 风险：保留 idx_grand_inviter 会让未来「按 L2 快照字段 grand_inviter_id 查询」更容易，
-- 从而形成「邀请人的邀请人的…」L3+ 链路追溯能力，违反 L3+ 物理隔离铁律。
-- 处置：删除该索引；grand_inviter_id 仅保留为单条记录的一次性快照列，绝不作为查询入口。
-- 注意：不修改历史迁移 V004，避免 Flyway checksum 漂移。
DROP INDEX idx_grand_inviter ON users;
