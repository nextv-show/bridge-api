-- C 端用户管理：admin-service 在 asset_db 拥有的去规范化 users 表 + 种子数据。
-- 真实用户存于 user_db（admin-service 不可达），此表镜像原型样例，供用户管理列表/详情展示。
-- GMV/订单/设备数通过 orders / device_assets（均有 user_id）实时聚合。
CREATE TABLE IF NOT EXISTS users (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid           VARCHAR(64),
  nickname         VARCHAR(64),
  avatar_url       VARCHAR(512),
  gender           VARCHAR(8),
  age              INT,
  phone_mask       VARCHAR(32),
  real_name_mask   VARCHAR(32),
  channel          VARCHAR(16) NOT NULL DEFAULT 'WECHAT_MP',
  tier             VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  tags             VARCHAR(256) NOT NULL DEFAULT '',
  city             VARCHAR(64),
  status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  frozen_reason    VARCHAR(256),
  frozen_duration  VARCHAR(16),
  frozen_at        DATETIME,
  frozen_by        VARCHAR(64),
  kyc_status       VARCHAR(16) NOT NULL DEFAULT 'NONE',
  kyc_verified_at  DATETIME,
  note             VARCHAR(512),
  last_active_at   DATETIME,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  KEY idx_status (status),
  KEY idx_kyc_status (kyc_status),
  KEY idx_channel (channel),
  KEY idx_created_at (created_at)
);

-- ===== 种子用户（镜像原型样例，ids 14808..14821）=====
INSERT IGNORE INTO users
  (id, openid, nickname, avatar_url, gender, age, phone_mask, real_name_mask, channel, tier, tags, city, status, frozen_reason, frozen_duration, frozen_at, frozen_by, kyc_status, kyc_verified_at, note, last_active_at, created_at)
VALUES
  (14821, 'oWxMp_8a1f21', '王*杰', NULL, 'M',   34, '138****5821', '王*杰', 'WECHAT_MP', 'VIP',    'REPEAT,HIGH_GMV', '上海 · 浦东', 'ACTIVE', NULL, NULL, NULL, NULL, 'PASS',    '2025-11-02 10:21:00', NULL,                       '2026-05-24 21:13:00', '2024-03-11 09:00:00'),
  (14820, 'oWxMp_3c9d04', '李*宁', NULL, 'F', 29, '139****4720', '李*宁', 'IOS',       'GOLD',   'REPEAT,REFERRER', '北京 · 朝阳', 'ACTIVE', NULL, NULL, NULL, NULL, 'PASS',    '2025-12-18 14:05:00', NULL,                       '2026-05-25 08:42:00', '2024-06-22 13:30:00'),
  (14819, 'oWxMp_77b310', '张*峰', NULL, 'M',   41, '136****1093', '张*峰', 'ANDROID',   'VIP',    'HIGH_GMV',        '深圳 · 南山', 'ACTIVE', NULL, NULL, NULL, NULL, 'PASS',    '2025-09-30 16:48:00', '重点客户，季度复购',         '2026-05-23 19:55:00', '2024-01-08 10:15:00'),
  (14818, 'oWxMp_b5e8c2', '陈*怡', NULL, 'F', 26, '137****6620', '陈*怡', 'DOUYIN',    'NORMAL', 'REFERRER',        '杭州 · 西湖', 'ACTIVE', NULL, NULL, NULL, NULL, 'PENDING', NULL,                  NULL,                       '2026-05-22 11:09:00', '2024-09-14 18:20:00'),
  (14817, 'oWxMp_19af6d', '刘*洋', NULL, 'M',   38, '135****8841', '刘*洋', 'WECHAT_MP', 'GOLD',   'REPEAT',          '广州 · 天河', 'ACTIVE', NULL, NULL, NULL, NULL, 'PASS',    '2025-08-12 09:33:00', NULL,                       '2026-05-20 22:01:00', '2024-02-19 08:45:00'),
  (14816, 'oWxMp_42cc88', '赵*敏', NULL, 'F', 31, '188****3372', '赵*敏', 'H5',        'NORMAL', '',                '成都 · 高新', 'ACTIVE', NULL, NULL, NULL, NULL, 'NONE',    NULL,                  NULL,                       '2026-05-19 07:30:00', '2025-01-25 20:10:00'),
  (14815, 'oWxMp_6d0e15', '孙*豪', NULL, 'M',   45, '186****2204', '孙*豪', 'IOS',       'VIP',    'HIGH_GMV,REPEAT', '南京 · 鼓楼', 'ACTIVE', NULL, NULL, NULL, NULL, 'PASS',    '2025-07-07 15:12:00', '高客单，关注续约',           '2026-05-18 16:44:00', '2024-04-02 11:05:00'),
  (14814, 'oWxMp_aa73f9', '周*欣', NULL, 'F', 23, '150****9918', '周*欣', 'DOUYIN',    'NEW',    '',                '武汉 · 武昌', 'ACTIVE', NULL, NULL, NULL, NULL, 'PENDING', NULL,                  NULL,                       '2026-05-17 10:26:00', '2026-03-30 19:40:00'),
  (14813, 'oWxMp_0ffb31', '吴*强', NULL, 'M',   36, '159****4407', '吴*强', 'ANDROID',   'NORMAL', 'RISK',            '重庆 · 渝中', 'FROZEN', '实名认证连续 3 次失败 · 风控人工冻结', '7d', '2026-05-16 12:00:00', 'risk_ops', 'REJECT', NULL, '风控复核中',                 '2026-05-16 11:50:00', '2025-05-10 09:18:00'),
  (14812, 'oWxMp_71d2a0', '郑*萱', NULL, 'F', 33, '152****7765', '郑*萱', 'WECHAT_MP', 'GOLD',   'REPEAT,HIGH_GMV', '西安 · 雁塔', 'ACTIVE', NULL, NULL, NULL, NULL, 'PASS',    '2025-06-21 13:40:00', NULL,                       '2026-05-15 21:18:00', '2024-05-28 14:55:00'),
  (14811, 'oWxMp_c4810e', '冯*康', NULL, 'M',   28, '133****3098', '冯*康', 'IOS',       'NORMAL', 'DORMANT',         '苏州 · 工业园','ACTIVE', NULL, NULL, NULL, NULL, 'PASS',    '2025-02-14 08:50:00', NULL,                       '2025-12-01 09:12:00', '2024-08-17 16:30:00'),
  (14810, 'oWxMp_e2f6b7', '何*璐', NULL, 'F', 30, '189****5512', '何*璐', 'H5',        'NORMAL', 'REFERRER,RISK',   '天津 · 和平', 'ACTIVE', NULL, NULL, NULL, NULL, 'PENDING', NULL,                  NULL,                       '2026-05-12 17:33:00', '2025-07-09 10:22:00'),
  (14809, 'oWxMp_5b9c44', '黄*俊', NULL, 'M',   40, '177****8830', '黄*俊', 'ANDROID',   'NORMAL', 'RISK',            '长沙 · 岳麓', 'BANNED', '涉嫌恶意刷单 · 风控团队封禁', NULL, '2026-04-28 18:30:00', 'risk_team', 'NONE', NULL, '已封禁',                     '2026-04-28 18:25:00', '2024-11-03 12:40:00'),
  (14808, 'oWxMp_9001ab', '林*晴', NULL, 'F', 27, '131****2255', '林*晴', 'WECHAT_MP', 'NEW',    '',                '青岛 · 市南', 'ACTIVE', NULL, NULL, NULL, NULL, 'NONE',    NULL,                  NULL,                       '2026-05-10 09:01:00', '2026-04-15 11:11:00');

-- ===== 种子订单表（admin 面板去规范化，IF NOT EXISTS 防重复）=====
CREATE TABLE IF NOT EXISTS orders (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT NOT NULL,
  sku_id            BIGINT NOT NULL DEFAULT 1,
  qty               INT NOT NULL DEFAULT 1,
  amount_cents      BIGINT NOT NULL,
  status            VARCHAR(16) NOT NULL DEFAULT 'PENDING_PAY',
  wx_transaction_id VARCHAR(64),
  address_snapshot  JSON NOT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at           DATETIME,
  KEY idx_user_id (user_id)
);

-- ===== 种子设备资产表（admin 面板去规范化，IF NOT EXISTS 防重复）=====
CREATE TABLE IF NOT EXISTS device_assets (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id                  BIGINT NOT NULL,
  order_id                 BIGINT NOT NULL,
  sn                       VARCHAR(64) NOT NULL UNIQUE,
  model                    VARCHAR(64) NOT NULL,
  purchased_at             DATETIME NOT NULL,
  stage                    VARCHAR(16) NOT NULL DEFAULT 'STAGE_1',
  cumulative_income_cents  BIGINT NOT NULL DEFAULT 0,
  roi_bp                   INT NOT NULL DEFAULT 0,
  KEY idx_user_id (user_id),
  KEY idx_order_id (order_id)
);

-- ===== 种子订单（INSERT IGNORE，让 GMV/订单聚合真实非零）=====
INSERT IGNORE INTO orders
  (id, user_id, sku_id, qty, amount_cents, status, wx_transaction_id, address_snapshot, created_at, paid_at)
VALUES
  (90001, 14821, 1, 1, 299900, 'PAID', 'wx_tx_90001', '{}', '2024-03-12 10:00:00', '2024-03-12 10:05:00'),
  (90002, 14821, 1, 2, 599800, 'PAID', 'wx_tx_90002', '{}', '2024-09-01 14:00:00', '2024-09-01 14:03:00'),
  (90003, 14820, 1, 1, 299900, 'PAID', 'wx_tx_90003', '{}', '2024-06-23 09:30:00', '2024-06-23 09:34:00'),
  (90004, 14820, 1, 1, 299900, 'PAID', 'wx_tx_90004', '{}', '2025-01-15 11:00:00', '2025-01-15 11:02:00'),
  (90005, 14819, 1, 3, 899700, 'PAID', 'wx_tx_90005', '{}', '2024-01-09 10:20:00', '2024-01-09 10:25:00'),
  (90006, 14819, 1, 1, 299900, 'PAID', 'wx_tx_90006', '{}', '2025-03-04 16:10:00', '2025-03-04 16:12:00'),
  (90007, 14812, 1, 2, 599800, 'PAID', 'wx_tx_90007', '{}', '2024-05-29 15:00:00', '2024-05-29 15:04:00'),
  (90008, 14815, 1, 1, 299900, 'PAID', 'wx_tx_90008', '{}', '2024-04-03 11:30:00', '2024-04-03 11:33:00');

-- ===== 种子设备资产（INSERT IGNORE）=====
INSERT IGNORE INTO device_assets
  (id, user_id, order_id, sn, model, purchased_at, stage, cumulative_income_cents, roi_bp)
VALUES
  (80001, 14821, 90001, 'SN-A-14821-01', '三水元 Pro', '2024-03-12 10:05:00', 'STAGE_2', 152000, 5070),
  (80002, 14821, 90002, 'SN-A-14821-02', '三水元 Pro', '2024-09-01 14:03:00', 'STAGE_1', 48000, 1600),
  (80003, 14819, 90005, 'SN-A-14819-01', '三水元 Max', '2024-01-09 10:25:00', 'FUSED',   330000, 11000),
  (80004, 14820, 90003, 'SN-A-14820-01', '三水元 Pro', '2024-06-23 09:34:00', 'STAGE_1', 61000, 2030);
