-- 002 P1-2 claim 后 SLA 确认 + 每日配额（design-preference-matching.md §4 §5.3）。
-- claim_confirmed_at 软标记：NULL=待确认；不新增 RequestStatus 枚举值（status 仍 LOCKED）。
ALTER TABLE matching_requests
  ADD COLUMN claim_confirmed_at DATETIME NULL AFTER locked_at;

-- 灰度安全：存量 LOCKED 行（接单确认流程上线前创建）一律视为「已确认」，
-- 否则首次部署时这些在途锁定单会被 SLA 误判为「未确认」而自动释放/误发提醒。
-- 新接单（上线后）由 ClaimRequestUseCase 重置为 NULL，正常进入待确认。
UPDATE matching_requests
  SET claim_confirmed_at = COALESCE(locked_at, created_at)
  WHERE status = 'LOCKED' AND claim_confirmed_at IS NULL;

-- SLA / 提醒 / 每日配额配置（与既有 matching_config 同表，运营 SQL 直改）。
-- claim.daily.quota.activity 留空=用基准值 per.owner；活动期填数字覆盖。
INSERT INTO matching_config (config_key, config_value) VALUES
  ('claim.confirm.sla.hours',     '24'),
  ('claim.confirm.remind1.hours', '12'),
  ('claim.confirm.remind2.hours', '22'),
  ('claim.daily.quota.per.owner', '10'),
  ('claim.daily.quota.activity',  '');
