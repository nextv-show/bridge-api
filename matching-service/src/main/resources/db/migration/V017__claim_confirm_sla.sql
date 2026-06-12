-- 002 P1-2 claim 后 SLA 确认 + 每日配额（design-preference-matching.md §4 §5.3）。
-- claim_confirmed_at 软标记：NULL=待确认；不新增 RequestStatus 枚举值（status 仍 LOCKED）。
ALTER TABLE matching_requests
  ADD COLUMN claim_confirmed_at DATETIME NULL AFTER locked_at;

-- SLA / 提醒 / 每日配额配置（与既有 matching_config 同表，运营 SQL 直改）。
-- claim.daily.quota.activity 留空=用基准值 per.owner；活动期填数字覆盖。
INSERT INTO matching_config (config_key, config_value) VALUES
  ('claim.confirm.sla.hours',     '24'),
  ('claim.confirm.remind1.hours', '12'),
  ('claim.confirm.remind2.hours', '22'),
  ('claim.daily.quota.per.owner', '10'),
  ('claim.daily.quota.activity',  '');
