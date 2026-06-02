-- V011 将 asset_db.skus 对齐真实水机目录（与 H5 device_specs / admin 商品管理一致），
-- 并按机型设置「一次性销售介绍费」固定金额（L1 直接 / L2 间接）。
--
-- 介绍费金额来源（合规说明）：
--   初值由各机型购机价 × 8%(L1) / 4%(L2) 折算，但**落库为按机型固定的金额**，
--   不是运行时的购机款百分比公式 —— 符合路径 2「一次性实物销售介绍费」定性。
--   运营后台后续可直接改这两列的固定数额，与购机款再无公式联动。
--   价格口径与 cend-service V009 device_specs / admin V077 skus 完全一致。
--
-- 真实机型与折算：
--   BR-H1 家庭版·标准型  ¥4600  → L1 36800 / L2 18400
--   BR-H2 家庭版·增强型  ¥6800  → L1 54400 / L2 27200
--   BR-C1 商用 A 型      ¥8600  → L1 68800 / L2 34400
--   BR-C2 商用 B 型      ¥12800 → L1 102400 / L2 51200
--   BR-T1 测试专用 ¥1            → 介绍费 0（联调单不产生返利）
--
-- 幂等：FROM DUAL WHERE NOT EXISTS，按 name 判存在才插入；可重复执行不产生重复行。

-- 1) 旧示例 SKU 下线（非真实机型，保留数据但不再展示/可购）。
UPDATE skus SET status = 'INACTIVE'
 WHERE name IN ('基础饮水机', '高级净水器', '商用直饮机');

-- 2) 种入真实机型 + 固定介绍费（仅当同名机型不存在时）。
INSERT INTO skus (name, price_cents, deposit_cents, benefits_md, image_url, status,
                  referral_fee_l1_cents, referral_fee_l2_cents)
SELECT '家庭版·标准型', 460000, 0,
  '### 家庭版·标准型\n日产水 200L · 适配 3-4 口之家\n\n- 高精度涡轮流量计\n- 内置 NB-IoT 通讯模组\n- 5 年滤芯更换服务',
  NULL, 'ACTIVE', 36800, 18400
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM skus WHERE name = '家庭版·标准型');

INSERT INTO skus (name, price_cents, deposit_cents, benefits_md, image_url, status,
                  referral_fee_l1_cents, referral_fee_l2_cents)
SELECT '家庭版·增强型', 680000, 0,
  '### 家庭版·增强型\n日产水 400L · 双膜净化\n\n- RO 反渗透 + 矿化双膜\n- 远程水质实时回传\n- 7x24h 设备健康监测',
  NULL, 'ACTIVE', 54400, 27200
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM skus WHERE name = '家庭版·增强型');

INSERT INTO skus (name, price_cents, deposit_cents, benefits_md, image_url, status,
                  referral_fee_l1_cents, referral_fee_l2_cents)
SELECT '商用 A 型', 860000, 0,
  '### 商用 A 型\n日产水 1.2T · 小型商铺 / 工作室\n\n- 不锈钢工业级管路\n- 4G IoT 模组 + 边缘网关\n- 故障自检 & 远程派单',
  NULL, 'ACTIVE', 68800, 34400
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM skus WHERE name = '商用 A 型');

INSERT INTO skus (name, price_cents, deposit_cents, benefits_md, image_url, status,
                  referral_fee_l1_cents, referral_fee_l2_cents)
SELECT '商用 B 型', 1280000, 0,
  '### 商用 B 型\n日产水 3T · 餐饮 / 中型场景\n\n- 多级预过滤 + 紫外杀菌\n- 智能调度算法降耗 18%\n- 专属客户经理对接',
  NULL, 'ACTIVE', 102400, 51200
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM skus WHERE name = '商用 B 型');

-- 测试专用机（¥1，联调支付流程用；介绍费 0，不产生返利）。
INSERT INTO skus (name, price_cents, deposit_cents, benefits_md, image_url, status,
                  referral_fee_l1_cents, referral_fee_l2_cents)
SELECT '测试专用 · 请勿下单', 100, 0,
  '### 测试专用\n仅供联调测试 · 真实用户请勿选择\n\n- 非真实设备\n- 1 元象征性价格\n- 仅用于支付流程测试',
  NULL, 'ACTIVE', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM skus WHERE name = '测试专用 · 请勿下单');
