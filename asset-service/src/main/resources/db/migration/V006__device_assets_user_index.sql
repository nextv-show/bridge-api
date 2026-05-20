-- D.3.1: /assets/mine 按 user_id 过滤并按 purchased_at 倒序分页。
-- 用复合索引 (user_id, purchased_at) 覆盖该访问模式（过滤 + 有序扫描），
-- 取代原仅 user_id 的单列索引 idx_user（已被复合索引前缀覆盖，移除以免冗余）。
ALTER TABLE device_assets DROP INDEX idx_user;
CREATE INDEX idx_user_purchased ON device_assets (user_id, purchased_at);
