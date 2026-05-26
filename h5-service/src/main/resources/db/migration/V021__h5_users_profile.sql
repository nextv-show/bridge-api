-- V021 H5 用户资料快照（014-invite-confirmation）。
-- 为「邀请确认页」展示推荐人脱敏昵称+头像，h5_users 增加 nickname/avatar_url 两列。
-- 数据来源：微信网页授权登录成功后写入（仅资料快照，与 inviter_id/grand_inviter_id 关系链无关）。
-- 合规：resolve-inviter 接口仅返回脱敏昵称+头像，零可定位 PII；这两列不参与任何关系链递归。
ALTER TABLE h5_users ADD COLUMN nickname VARCHAR(64) NULL COMMENT '微信昵称快照（登录时写入）' AFTER openid;
ALTER TABLE h5_users ADD COLUMN avatar_url VARCHAR(512) NULL COMMENT '微信头像 URL 快照（登录时写入）' AFTER nickname;
