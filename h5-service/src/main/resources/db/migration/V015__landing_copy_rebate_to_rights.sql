-- V015 文案调整：弱化「返利」类金融联想，对齐运营口径。
--   simulator.outputLabel：预估年度服务返利 → 预估基础水务盈利
--   footer.disclaimer / landing_feature.title：服务返利 → 服务权益
-- 保持 Flyway 历史不可变，不回改 V004；以 UPDATE 修正已发布（PUBLISHED）配置。

UPDATE landing_config
SET simulator_json = JSON_SET(simulator_json, '$.outputLabel', '预估基础水务盈利'),
    footer_json    = JSON_SET(
                       footer_json,
                       '$.disclaimer',
                       REPLACE(JSON_UNQUOTE(JSON_EXTRACT(footer_json, '$.disclaimer')), '服务返利', '服务权益')
                     )
WHERE status = 'PUBLISHED';

UPDATE landing_feature
SET title = '服务权益'
WHERE title = '服务返利';
