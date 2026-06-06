package com.sanshuiyuan.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 三水元 结算服务（004-settlement-engine）。
 * 消费 003 water_bills → 分账到所有权人/平台 → 提现代付。
 * 数据库：h5_db（settlement_flyway_schema_history），端口 8091。
 */
@SpringBootApplication
public class SettlementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
