package com.sanshuiyuan.evidence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 三水元 存证工人服务（evidence-worker，003）。
 * 业务主体：蚂蚁链异步存证（003 plan）。纯 REST worker，仅接受 S2S 调用；
 * 连 h5_db（独立 flyway 历史表）；端口 8090。
 */
@SpringBootApplication
public class EvidenceWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvidenceWorkerApplication.class, args);
    }
}
