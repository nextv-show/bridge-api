-- V010 给 SKU 增加「按机型固定的一次性销售介绍费」两档费率（分）。
-- 合规铁律：这是按机型固定的销售介绍费金额（运营后台填实数），
--           严禁做成购机款的百分比/比例分成 —— 推荐返利不得与购机款金额挂钩。
-- 默认 0：未配置费率的机型不产生返利（等同自然销售，无介绍费）。
ALTER TABLE skus
  ADD COLUMN referral_fee_l1_cents BIGINT NOT NULL DEFAULT 0
    COMMENT 'L1（直接邀请人）一次性销售介绍费（分），按机型固定，严禁百分比',
  ADD COLUMN referral_fee_l2_cents BIGINT NOT NULL DEFAULT 0
    COMMENT 'L2（间接邀请人）一次性销售介绍费（分），按机型固定，严禁百分比';
