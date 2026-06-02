-- V017 首页主标题合并：把「成为可被信任的」「资产单元」两行合并为一行。
--   titleLines: ['让一台水机','成为可被信任的','资产单元'] → ['让一台水机','成为可被信任的资产单元']
-- V004 seed 恢复原位（匹配线上 flyway_schema_history 校验和），以 UPDATE 修正。
UPDATE landing_config
SET hero_json = JSON_SET(
                  hero_json,
                  '$.titleLines',
                  JSON_ARRAY('让一台水机', '成为可被信任的资产单元')
                )
WHERE status = 'PUBLISHED';
