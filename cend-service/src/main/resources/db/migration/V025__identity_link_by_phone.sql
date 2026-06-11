-- 跨端身份关联（微信手机号核验）：让返客在小程序"核验身份"后看到其在 H5（公众号）下的历史订单。
-- 背景：两端微信 openid 不互通、unionid 恒 null；订单按 openid 归属。用户在小程序一键"微信手机号快速验证"
-- 拿到微信级已验证手机号，与已实名（PASS）记录按号匹配同一自然人，建立"仅可见"关联（不授予认购资格）。

-- 1) kyc_records 增加手机号确定性哈希（HMAC-SHA256），供按号等值匹配（phone_enc 随机 IV 无法比对）。
ALTER TABLE kyc_records
  ADD COLUMN phone_hash VARCHAR(64) NULL COMMENT '手机号 HMAC-SHA256，跨端按号匹配同人' AFTER id_card_hash;
CREATE INDEX idx_kyc_phone_hash ON kyc_records (phone_hash);

-- 2) 身份关联表：openid → 自然人 id_card_hash 的"仅可见"绑定。
--    与 kyc_records(PASS) 一起被 IdentityResolver 用于按自然人聚合订单（读路径）；
--    绝不被 CreateOrderUseCase 的 KYC 闸口采纳（不授予认购/实名资格，仅解锁历史订单可见）。
CREATE TABLE IF NOT EXISTS identity_links (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  openid        VARCHAR(64)  NOT NULL COMMENT '本端会话 openid（canonicalId，= 订单归属值）',
  id_card_hash  VARCHAR(64)  NOT NULL COMMENT '匹配到的同一自然人身份证哈希（来自其 PASS 实名记录）',
  source        VARCHAR(16)  NOT NULL DEFAULT 'PHONE' COMMENT '关联来源：PHONE=微信手机号核验',
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_identity_links_openid (openid),
  KEY idx_identity_links_id_card_hash (id_card_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨端身份关联（仅订单可见，不授认购资格）';
