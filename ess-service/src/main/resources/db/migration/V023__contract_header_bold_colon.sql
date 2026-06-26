-- V023: 合同/承诺书头部标签「冒号纳入加粗」
--
-- 承接 V022：V022 为绕开 CommonMark flanking（`**标签：**值` 闭合 ** 前是标点、后接文字 →
-- 留字面 **），把冒号移到加粗外（`**标签**：值`），结果「标签」加粗但「：」是常规体。
-- 业务要求：整个「标签：」（含全角冒号）加粗显示、值常规。
--
-- 正解：冒号留在加粗内、闭合 ** 后补一个空格（`**标签：** 值`）——闭合 ** 后接空格即构成
-- 右侧界定符，强调正常闭合、不留字面 **，且「标签：」整体进入 <strong>。
--
-- 故本迁移把 V022 产出的 `**标签**：` 改成 `**标签：** `（注意 to 串结尾的空格）。
-- 依赖 Flyway 顺序保证 V022 先于 V023 执行；仅影响此后新生成合同。

-- ── 设备认购主合同 MAIN_CONTRACT ────────────────────────────────────────────
UPDATE contract_templates
SET content_body = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    content_body
  , '**协议编号**：',                 '**协议编号：** ')
  , '**签订日期**：',                 '**签订日期：** ')
  , '**甲方（设备所有权人/用户）**：', '**甲方（设备所有权人/用户）：** ')
  , '**身份证号/统一社会信用代码**：', '**身份证号/统一社会信用代码：** ')
  , '**联系电话**：',                 '**联系电话：** ')
  , '**乙方（平台运营方）**：',       '**乙方（平台运营方）：** ')
  , '**法定代表人**：',               '**法定代表人：** ')
  , '**联系地址**：',                 '**联系地址：** ')
WHERE template_code = 'MAIN_CONTRACT'
  AND (is_deprecated = false OR is_deprecated IS NULL);

-- ── 实名认证与用水需求发布承诺书 KYC_AUTH_CONTRACT ──────────────────────────
UPDATE contract_templates
SET content_body = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    content_body
  , '**承诺书编号**：',               '**承诺书编号：** ')
  , '**签署日期**：',                 '**签署日期：** ')
  , '**承诺人（实名用户）**：',       '**承诺人（实名用户）：** ')
  , '**身份证号**：',                 '**身份证号：** ')
  , '**联系电话**：',                 '**联系电话：** ')
  , '**接收主体（平台运营方）**：',   '**接收主体（平台运营方）：** ')
WHERE template_code = 'KYC_AUTH_CONTRACT'
  AND (is_deprecated = false OR is_deprecated IS NULL);
