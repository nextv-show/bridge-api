-- V015 历史文案+备案号修正：弱化当时线上「返利」类金融联想，对齐旧版运营口径。
--   simulator.outputLabel：预估年度服务返利 → 预估基础水务盈利
--   footer.disclaimer / landing_feature.title：服务返利 → 服务权益
--   footer.icpNumber：粤 ICP 备 2024XXXXXX 号 · 增值电信业务许可证 → 津ICP备2024014900号-2
-- V004 seed 恢复原位（匹配线上 flyway_schema_history 校验和），全部以 UPDATE 修正。
-- 注意：本迁移保留历史替换字面量，用于兼容已发布版本中的旧文案数据。

UPDATE landing_config
SET simulator_json = JSON_SET(simulator_json, '$.outputLabel', '预估基础水务盈利'),
    footer_json    = JSON_SET(
                       footer_json,
                       '$.disclaimer',
                       REPLACE(JSON_UNQUOTE(JSON_EXTRACT(footer_json, '$.disclaimer')), '服务返利', '服务权益'),
                       '$.icpNumber', '津ICP备2024014900号-2'
                     )
WHERE status = 'PUBLISHED';

UPDATE landing_feature
SET title = '服务权益'
WHERE title = '服务返利';
