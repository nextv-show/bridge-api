-- TEST-ONLY fixture（仅 src/test/resources，绝不打入运行时 jar）。
--
-- device_assets 是 admin-service 拥有的表（admin V074，prod core_db 已存在），matching-service
-- 不迁移它、仅以 native SQL 读写。但 V018(027) 引入了 `ALTER TABLE device_assets MODIFY stage ENUM`，
-- 该 ALTER 在 Flyway/上下文初始化阶段执行，早于各 IT 的 @BeforeEach 建表，故 Testcontainers 的
-- 全新 core_db 里 device_assets 缺失会让 V018 直接 SQLSyntaxError，拖垮 contextLoads 等全部 IT。
--
-- 本 fixture 以版本号 V009（早于首个真实迁移 V010）在测试库预建 device_assets，复刻 admin V074 形态
-- （stage 用 VARCHAR(16)，由 V018 真实 MODIFY 成 ENUM，验证转换在空表上成立），使测试环境贴合 prod。
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
  KEY idx_user_id (user_id)
);
