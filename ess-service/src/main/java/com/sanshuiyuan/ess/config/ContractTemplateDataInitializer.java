package com.sanshuiyuan.ess.config;

import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.infra.repository.ContractTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 合同模板初始数据配置。
 * <p>
 * 在首次启动时录入主合同模板和产权归属确认书附件模板。
 * 仅在 dev 和 default profile 下自动执行。
 */
@Configuration
public class ContractTemplateDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(ContractTemplateDataInitializer.class);

    /**
     * 主合同模板编码。
     */
    public static final String MAIN_CONTRACT_CODE = "MAIN_CONTRACT";

    /**
     * 产权归属确认书附件模板编码。
     */
    public static final String PROPERTY_CERT_CODE = "PROPERTY_CERT";

    @Bean
    @Profile({"dev", "default"})
    CommandLineRunner initContractTemplates(ContractTemplateRepository repository) {
        return args -> {
            seedIfAbsent(repository, MAIN_CONTRACT_CODE,
                    "三水元智能水机设备管理与代运营服务协议",
                    ContractTemplate.TemplateType.MAIN,
                    loadMainContractTemplate());

            seedIfAbsent(repository, PROPERTY_CERT_CODE,
                    "三水元智能水机产权归属确认书",
                    ContractTemplate.TemplateType.ATTACHMENT,
                    loadPropertyCertTemplate());

            log.info("合同模板初始数据检查完成");
        };
    }

    private void seedIfAbsent(ContractTemplateRepository repository,
                               String code, String name,
                               ContractTemplate.TemplateType type,
                               String contentBody) {
        if (repository.findTopByTemplateCodeOrderByVersionDesc(code).isEmpty()) {
            ContractTemplate template = ContractTemplate.create(code, name, type, contentBody, 1);
            repository.save(template);
            log.info("初始模板已录入 [code={}, name={}]", code, name);
        }
    }

    private String loadMainContractTemplate() {
        return """
        # **三水元智能水机设备管理与代运营服务协议**

        **协议编号：**{{contractNo}}
        **签订日期：**{{signDate}}

        **甲方（设备所有权人/用户）：**{{userName}}
        **身份证号/统一社会信用代码：**{{idCardNo}}
        **联系电话：**{{phone}}

        **乙方（平台运营方）：**天津源创智能科技有限公司
        **法定代表人：**{{legalRepresentative}}
        **联系地址：**{{companyAddress}}

        鉴于甲方认可乙方（"三水元"平台）"基于物联网与大数据技术的分布式智慧饮水基础设施"模式，并已通过平台全款购买智能水机设备；乙方具备物联网（IoT）、软硬件运维及智能供需撮合技术能力。双方本着平等、自愿、公平和诚实信用的原则，就甲方将所购设备委托乙方代为管理及运营事宜，达成如下协议：

        ## **第一条 协议标的与物权归属**

        **1.1 物权锚定：**本协议标的为甲方购买的三水元智能水机（以下简称"设备"）。本生态实行"一机一码一合约"制，甲方对购买设备对应SN码的物理实体拥有绝对所有权。
        **1.2 破产隔离：**乙方仅作为平台技术支撑与代运营服务方。若乙方发生破产清算，该设备物理实体及其产生的独立物联网收益不属于乙方的破产财产，依法实现破产隔离。
        **1.3 设备信息：**

        * 设备型号：{{deviceModel}}
        * 设备SN码：{{deviceSn}}
        * 设备购机款：人民币{{devicePrice}}元

        ## **第二条 代运营服务内容与平台职责**

        **2.1 供需撮合：**乙方通过系统核心撮合引擎，将甲方的闲置设备资源精准推送给有真实饮水需求的B端场所或C端家庭（即"终端用水用户"），并在达成撮合后调度完成物流与上门安装。
        **2.2 技术监测与存证：**乙方利用智能水机主板集成的RTC独立时钟芯片与高精脉冲流量计，对设备运行状态、滤芯消耗及每滴水的流出进行毫秒级采集，确保数据"物理真实"并防止人为篡改。
        **2.3 运维保障：**乙方负责设备生命周期内的滤芯更换提醒及派单、日常维修工单跟进及后台软件系统（SaaS）的升级与维护。

        ## **第三条 服务返利与结算标准（核心合规条款）**

        **3.1 消费驱动结算：**甲方的服务返利完全挂钩于终端用户的真实用水消费（单价设定按终端实际撮合为准，如0.4元至1.5元/升）及客观的IoT物联网数据。平台按照《平台自动分账规则》，在扣除对应的耗材、维护及运营成本后，将运营分成以系统自动清分形式结算至甲方账户。
        **3.2 断网缓存补偿：**若设备处于弱网或断网环境，主板支持最高30天数据离线缓存补录，设备恢复联网后系统将自动校验并执行补单结算，确保数据不丢失。
        **3.3 提现合规规则：**为保障生态健康运转并符合相关税务及运营规范，收益提现强制执行"现金+积分（健康水滴，比例为70%现金+30%积分）"拆分机制，并按系统公示比例收取必要的手续费用。

        ## **第四条 风险提示与合规熔断机制（特别提示）**

        **4.1 去金融化与不保本声明：**本协议本质为实体设备委托租赁与代运营服务，**绝非理财、非法集资或金融投资产品。**乙方绝不承诺固定保本付息及固定年化收益（不含任何ROI承诺）。因市场环境变化、设备匹配失败、场地客流量不足等客观因素造成的收益不及预期风险，由甲方自行评估并承担。
        **4.2 "两倍出局"熔断机制：**为筑牢合规底线，阻断无限期收益的类金融特征，当该单一SN码设备累计产生的服务返利达到甲方初始购机款的 **2倍** 时，本代运营协议自动终止，该设备资产包状态自动进入"已熔断出局"状态，不再产生超额分成。

        ## **第五条 数据授权与隐私保护**

        **5.1 数据采集授权：**甲方同意授权乙方在提供基本供水及代运营服务前提下，采集设备运行物联网数据。乙方承诺严格遵照《数据安全法》及《个人信息保护法》相关规定予以脱敏与加密保护。

        ## **第六条 协议生效、锁定与争议解决**

        **6.1 冷静期与生效：**本协议通过电子签名/签章签署后，即进入法定的"24小时冷静锁定确认期"。在此期间内甲方有权单方撤销；24小时届满且未撤销的，本协议正式生效。
        **6.2 争议管辖：**凡因本协议引起的任何争议，双方应友好协商；协商不成的，任何一方均有权向 **乙方所在地（天津市）有管辖权的人民法院** 提起诉讼。

        | 甲方（设备所有权人）： {{userName}} 电子签字/盖章：______ 日期：{{signDate}} | 乙方（运营方）：天津源创智能科技有限公司 授权代表签字/公章：______ 日期：{{signDate}} |
        | :---- | :---- |
        """;
    }

    private String loadPropertyCertTemplate() {
        return """
        # **三水元智能水机产权归属确认书**

        **确认书编号：**{{contractNo}}-PC
        **出具日期：**{{signDate}}

        **设备所有权人（以下简称"产权人"）：**{{userName}}
        **身份证号/统一社会信用代码：**{{idCardNo}}
        **联系电话：**{{phone}}

        **平台运营方（以下简称"运营方"）：**天津源创智能科技有限公司

        ## **第一条 标的物确权**

        本确认书旨在明确"三水元"智能水机的物权归属。产权人通过三水元平台全款购机，自支付完成且经过24小时冷静期后，即取得对应SN码设备的绝对物理所有权。

        * 设备型号：{{deviceModel}}
        * 设备唯一出厂编码（SN码）：{{deviceSn}}
        * 购置金额：人民币{{devicePrice}}元

        ## **第二条 独立物权与破产隔离**

        **2.1 绝对所有权：**产权人对上述设备享有占有、使用、收益和处分的完整物权。本平台实行的"一机一码一合约"机制，确保认购资金与物理实体一一对应。
        **2.2 破产隔离机制：**运营方仅提供设备的代运营、供需撮合及物联网数据监测服务。任何情况下，上述设备均不属于运营方的公司资产。若运营方遭遇债务危机、被执行或破产清算，该设备物理实体及其衍生的物联网数据资产依法不列入破产清算财产范围，实现物理级风险隔离。

        ## **第三条 权利限制与禁止事项**

        **3.1 平台禁止事项：**运营方不得未经产权人明确授权，将该设备用于抵押、质押、担保或任何形式的二次融资工具包装。
        **3.2 产权人声明：**产权人知悉并确认，购买该设备是基于真实的商品买卖及代运营商业行为，非购买金融理财、虚拟货币或任何形式的非法集资产品。

        | 产权人签字/盖章：______ 日期：{{signDate}} | 运营方公章： 日期：{{signDate}} |
        | :---- | :---- |
        """;
    }
}
