-- 清理 V074__users.sql 引入的 14 条演示种子用户及其关联订单/设备，避免真实花名册混入假数据。
-- 背景：V074 用 id 14808–14821 写入脱敏演示用户（openid 占位前缀 'oWxMp'），并配套种子订单
-- 90001–90008、种子设备 80001–80004 让 GMV/设备聚合非零。UserSyncScheduler 上线后真实用户由
-- user-service 按真实 user_id upsert（不落 14808–14821 段），这些种子已无意义。
--
-- 幂等：均按精确 id 删除；订单 90001–90008 可能已被 V078 删除，此处 DELETE 为 no-op。
-- 安全护栏：删用户时叠加 openid 前缀判定，确保只命中合成种子、绝不误删真实用户。

DELETE FROM device_assets WHERE id BETWEEN 80001 AND 80004;

DELETE FROM orders WHERE id BETWEEN 90001 AND 90008;

DELETE FROM users
 WHERE id BETWEEN 14808 AND 14821
   AND openid LIKE 'oWxMp%';
