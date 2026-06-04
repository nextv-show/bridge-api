-- H5/小程序登录并号（sync-h5）失败兜底队列。
-- 背景：WxLoginController 登录后调 user-service /internal/users/sync-h5 并入统一用户体系，但该调用
-- 为「降级不阻断登录」（user-service 抖动/不可达时静默返回 null）。若用户恰在该窗口仅登录一次且不再
-- 复登，则永久不入 user_db → admin 花名册也拉不到。本表只在该调用失败时入队，由 ReconcileH5UserSyncJob
-- 周期重试，直至成功（sync-h5 幂等：按 unionid/openid 命中不重复建号、不改关系链）。
--
-- 仅记失败项，happy path 不写表（零写放大）。canonical_id 唯一，重复登录失败只刷新同一行。

CREATE TABLE IF NOT EXISTS h5_user_sync_outbox (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  canonical_id  VARCHAR(64)  NOT NULL COMMENT '登录统一身份键：unionid 优先，否则微信 openid（=传给 sync-h5 的首参）',
  unionid       VARCHAR(64)  NULL,
  inviter_id    BIGINT       NULL COMMENT '解码后的推广者 user_id，仅首次创建写关系链；重试需原样回放',
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE / GAVE_UP',
  attempts      INT          NOT NULL DEFAULT 0,
  last_error    VARCHAR(256) NULL,
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_canonical_id (canonical_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='H5/小程序并号 sync-h5 失败兜底队列';
