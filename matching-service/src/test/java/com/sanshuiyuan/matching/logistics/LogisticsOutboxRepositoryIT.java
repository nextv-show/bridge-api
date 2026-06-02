package com.sanshuiyuan.matching.logistics;

import com.sanshuiyuan.matching.AbstractMysqlContainerTest;
import com.sanshuiyuan.matching.logistics.domain.LogisticsOutboxEntry;
import com.sanshuiyuan.matching.logistics.infra.LogisticsOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LogisticsOutboxRepositoryIT extends AbstractMysqlContainerTest {

    @Autowired
    LogisticsOutboxRepository repository;

    @Test
    void save_and_query_pending() {
        LogisticsOutboxEntry entry = new LogisticsOutboxEntry();
        entry.setRequestId(1001L);
        entry.setDeviceAssetId(2001L);
        entry.setPayloadJson("{\"ship_to_address\":\"test\"}");
        repository.saveAndFlush(entry);

        assertThat(repository.findByConsumedAtIsNullOrderByCreatedAtAsc()).hasSize(1);
        assertThat(repository.existsByRequestIdAndDeviceAssetId(1001L, 2001L)).isTrue();
    }
}
