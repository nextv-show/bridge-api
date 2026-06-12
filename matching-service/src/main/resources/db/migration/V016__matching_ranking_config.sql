-- 002 P1-1 撮合「偏好排序」配置（design-preference-matching.md §5.3）。
-- 分桶边界（CSV，降序桶=收益 / 升序桶=距离·新鲜）+ 四分量权重 + 同桶抖动 + 候选上限。
-- 与既有 matching_config 同表，运营 SQL 直改、Caffeine 60s 缓存生效。
INSERT INTO matching_config (config_key, config_value) VALUES
  ('match.weight.revenue',      '0.45'),
  ('match.weight.distance',     '0.30'),
  ('match.weight.scene',        '0.15'),
  ('match.weight.fresh',        '0.10'),
  ('match.revenue.buckets',     '3000,1800,1000,500'),
  ('match.distance.buckets.km', '2,5,15,50'),
  ('match.fresh.buckets.hours', '24,72,168'),
  ('match.jitter.epsilon',      '2'),
  ('nearby.candidate.limit',    '2000');
