-- 加每日次数上限 + 最低金额
ALTER TABLE withdrawal_policies
  ADD COLUMN daily_max_count INT NOT NULL DEFAULT 3 AFTER daily_max_cents,
  ADD COLUMN min_cents BIGINT NOT NULL DEFAULT 100 AFTER daily_max_count;

-- P0 修复：原 single_max_cents=5000000(¥5000) 与代码硬编码 ≥¥2000 拒绝矛盾
-- 改为 ¥1999(199900 cents) 严格小于 ¥2000 实名加密阈值
-- daily_max_cents 改为 599700(¥5997) = 3次 × ¥1999，逻辑自洽
-- 行选择与运行时 findTopByOrderByEffectiveFromDesc() 语义一致：取最新 effective_from 的记录
UPDATE withdrawal_policies
  SET single_max_cents = 199900,
      daily_max_cents = 599700
  ORDER BY effective_from DESC LIMIT 1;
