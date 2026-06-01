-- 002 撮合配置（plan §4 / Q5：表 + SQL 直改，无 UI）。
CREATE TABLE matching_config (
  config_key   VARCHAR(64) PRIMARY KEY,
  config_value VARCHAR(255) NOT NULL,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO matching_config (config_key, config_value) VALUES
  ('lock.ttl.days', '7'),
  ('lock.max.per.owner', '5'),
  ('nearby.default.radius.km', '50'),
  ('nearby.max.radius.km', '200');
