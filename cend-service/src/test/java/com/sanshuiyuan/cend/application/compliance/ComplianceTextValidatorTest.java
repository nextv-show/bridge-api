package com.sanshuiyuan.cend.application.compliance;

import com.sanshuiyuan.cend.api.dto.FooterDto;
import com.sanshuiyuan.cend.api.dto.HeroDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C.3：合规校验正反向用例（CI 必跑）。
 * 正向：合规营销文案 + 法定免责声明放行；反向：每个违禁词及全角/大小写/空格变体被拦。
 */
class ComplianceTextValidatorTest {

    private final ComplianceTextValidator validator = new ComplianceTextValidator();

    @Test
    void compliantMarketingText_passes() {
        assertThat(validator.scanText("按月返还运营场景下的真实服务分润")).isEmpty();
        assertThat(validator.scanText("毫秒级脉冲流量与 TDS 实时回传，数据真实可验证。")).isEmpty();
        assertThat(validator.scanText("把日常饮水行为升级为可被验证的高价值数字化资产。")).isEmpty();
    }

    @Test
    void mandatedDisclaimers_areAllowed() {
        // 法定免责声明在「否定语境」下含 投资/理财/收益，必须放行。
        assertThat(validator.scanText(
                "本平台为水机产品销售平台，服务返利来自设备运营场景下的真实分润，不构成投资建议或理财产品。"))
                .isEmpty();
        assertThat(validator.scanText(
                "※ 合规提示：模拟结果基于物理用水量模型测算，不构成任何收益保证，亦不代表本金回报或利息约定。"))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROI", "回报率", "收益率", "收益", "分红", "理财", "投资", "保本", "固收", "保息"})
    void eachBannedWord_inMarketingCopy_isCaught(String banned) {
        // 把违禁词放进「承诺」语境（非否定声明），应被拦。
        List<String> hits = validator.scanText("预期" + banned + "高达 20%");
        assertThat(hits).isNotEmpty();
    }

    @Test
    void fullWidthAndSpacedVariants_areCaught() {
        assertThat(validator.scanText("ＲＯＩ 高达 30%")).contains("ROI");   // 全角字母
        assertThat(validator.scanText("收 益 率 翻倍")).contains("收益率");      // 插入空格
        assertThat(validator.scanText("roi 超高")).contains("ROI");          // 小写
    }

    @Test
    void assertCompliant_onCleanDtoGraph_doesNotThrow() {
        HeroDto hero = new HeroDto("/logo", List.of("让一台水机", "成为可被信任的", "资产单元"),
                "高价值数字化资产", List.of(), List.of("家庭", "商用"));
        validator.assertCompliant(hero); // 不抛异常
    }

    @Test
    void findViolations_recursesIntoNestedDto() {
        FooterDto bad = new FooterDto("年化收益率高达 12%，保本保息", "粤 ICP");
        assertThat(validator.findViolations(bad)).contains("收益率", "保本", "保息");
    }
}
