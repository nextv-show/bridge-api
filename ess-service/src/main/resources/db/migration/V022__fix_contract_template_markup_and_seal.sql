-- V022: 修复合同/承诺书模板渲染缺陷 + 乙方签章文案
--
-- 背景（文件模式 CreateFlowByFiles：markdown → flexmark → PDF）：
--   1) 头部 `**标签：**值` 这种「加粗以全角冒号收尾、紧贴变量值」的写法触发 CommonMark
--      flanking 规则——闭合 ** 前是标点「：」、后是文字，闭合 ** 不构成右侧界定符，
--      强调无法闭合，** 原样留作字面字符印进 PDF。修法：把「：」移出加粗（`**标签**：值`）。
--   2) 乙方签署栏「公章」实为合同专用章，统一改为「盖章」，并与 ess.file.seal-keyword 关键字一致。
--   3) 删除乙方「授权代表」占位（乙方走企业自动盖章，无经办人手签栏）。
--
-- 用 REPLACE() 定向替换，仅改受影响 token，保留正文其余内容与行尾硬换行不变。
-- 历史已生成/已签合同的正文已固化在 contracts 表，不受影响；本迁移只影响此后新生成的合同。

-- ── 设备认购主合同 MAIN_CONTRACT ────────────────────────────────────────────
UPDATE contract_templates
SET content_body = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    content_body
  , '**协议编号：**',                 '**协议编号**：')
  , '**签订日期：**',                 '**签订日期**：')
  , '**甲方（设备所有权人/用户）：**', '**甲方（设备所有权人/用户）**：')
  , '**身份证号/统一社会信用代码：**', '**身份证号/统一社会信用代码**：')
  , '**联系电话：**',                 '**联系电话**：')
  , '**乙方（平台运营方）：**',       '**乙方（平台运营方）**：')
  , '**法定代表人：**',               '**法定代表人**：')
  , '**联系地址：**',                 '**联系地址**：')
  , '授权代表：______',              '')
  , '公章：______',                  '盖章：______')
WHERE template_code = 'MAIN_CONTRACT'
  AND (is_deprecated = false OR is_deprecated IS NULL);

-- ── 实名认证与用水需求发布承诺书 KYC_AUTH_CONTRACT ──────────────────────────
UPDATE contract_templates
SET content_body = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    content_body
  , '**承诺书编号：**',               '**承诺书编号**：')
  , '**签署日期：**',                 '**签署日期**：')
  , '**承诺人（实名用户）：**',       '**承诺人（实名用户）**：')
  , '**身份证号：**',                 '**身份证号**：')
  , '**联系电话：**',                 '**联系电话**：')
  , '**接收主体（平台运营方）：**',   '**接收主体（平台运营方）**：')
  , '公章：______',                  '盖章：______')
WHERE template_code = 'KYC_AUTH_CONTRACT'
  AND (is_deprecated = false OR is_deprecated IS NULL);
