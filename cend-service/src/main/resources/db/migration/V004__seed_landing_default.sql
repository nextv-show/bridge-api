-- V004 合规默认配置 seed（一条 PUBLISHED）。文案对齐 spec §FR-2 / plan §5.1，已过合规审核。
-- 注：footer/simulator 的免责声明在「否定语境」下出现 投资/理财/收益 等词，由 ComplianceTextValidator
--     的合规短语白名单放行（Phase C）；营销主张正文严禁出现违禁词。105 上线后由运营接管写入。
INSERT INTO landing_config (version, status, hero_json, simulator_json, footer_json, updated_by, published_at)
VALUES (
  1,
  'PUBLISHED',
  JSON_OBJECT(
    'logoUrl', '/assets/logo-horizontal.svg',
    'titleLines', JSON_ARRAY('让一台水机', '成为可被信任的', '资产单元'),
    'subtitle', '通过 IoT 实物水机，把日常饮水行为升级为可被验证的高价值数字化资产。',
    'kpis', JSON_ARRAY(
      JSON_OBJECT('label', '日产水', 'value', '±1ppm', 'unit', 'TDS 监测'),
      JSON_OBJECT('label', 'SN 码', 'value', '唯一', 'unit', '一机一码'),
      JSON_OBJECT('label', 'IoT 通讯', 'value', '99.97%', 'unit', '在线率')
    ),
    'industries', JSON_ARRAY('家庭', '商用', '园区')
  ),
  JSON_OBJECT(
    'minLiters', 0,
    'maxLiters', 50,
    'defaultLiters', 46,
    'baseRateBp', 850,
    'networkBonusBp', 400,
    'bonusThresholdLiters', 12,
    'unit', '升 / 日',
    'outputLabel', '预估年度运营服务分成',
    'disclaimer', '※ 合规提示：模拟结果基于物理用水量模型测算，不构成任何收益保证，亦不代表本金回报或利息约定。'
  ),
  JSON_OBJECT(
    'disclaimer', '本平台为水机产品销售平台，运营服务分成来自设备运营场景下的真实分润，不构成投资建议或理财产品。',
    'icpNumber', '粤 ICP 备 2024XXXXXX 号 · 增值电信业务许可证'
  ),
  'system-seed',
  NOW()
);

SET @cfg := LAST_INSERT_ID();

INSERT INTO landing_feature (config_id, sort, title, subtitle, descr, icon_key) VALUES
  (@cfg, 0, '远程水质回传', 'REMOTE TDS',     '毫秒级脉冲流量与 TDS 实时回传，数据真实可验证。', 'water-return'),
  (@cfg, 1, '故障自检',     'SELF DIAGNOSIS', '设备健康自检与远程告警，主动运维。',             'self-check'),
  (@cfg, 2, '运营服务分成', 'SERVICE SHARE',  '按月返还运营场景下的真实服务分成。',             'rebate'),
  (@cfg, 3, '24h 冷静期',   'COOL-OFF',       '下单 24 小时内无理由退款。',                     'cooldown');

INSERT INTO landing_trust_badge (config_id, sort, title, subtitle, icon_key) VALUES
  (@cfg, 0, '唯一 SN 码',     '一机一码',  'sn'),
  (@cfg, 1, '13% 增值税专票', '一机一发票', 'invoice'),
  (@cfg, 2, '24h 冷静期',     '无理由退款', 'cooldown'),
  (@cfg, 3, '第三方资金托管', '全程加密',  'escrow');
