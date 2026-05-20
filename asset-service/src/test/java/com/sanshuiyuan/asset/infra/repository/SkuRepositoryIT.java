package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.AbstractMysqlContainerTest;
import com.sanshuiyuan.asset.domain.Sku;
import com.sanshuiyuan.asset.domain.SkuStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C.1.4 (persistence): SkuRepository derived queries against real MySQL with Flyway-applied
 * schema and the V004 seed data, covering ACTIVE listing and active/inactive detail lookups.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SkuRepositoryIT extends AbstractMysqlContainerTest {

    @Autowired
    SkuRepository skuRepository;

    @Test
    void findByStatus_returnsSeededActiveSkus() {
        List<Sku> active = skuRepository.findByStatus(SkuStatus.ACTIVE);
        // V004 seeds 3 ACTIVE SKUs.
        assertThat(active).hasSizeGreaterThanOrEqualTo(3);
        assertThat(active).allMatch(s -> s.getStatus() == SkuStatus.ACTIVE);
    }

    @Test
    void findByIdAndStatus_activeSku_present() {
        Long id = skuRepository.findByStatus(SkuStatus.ACTIVE).get(0).getId();
        Optional<Sku> found = skuRepository.findByIdAndStatus(id, SkuStatus.ACTIVE);
        assertThat(found).isPresent();
    }

    @Test
    void findByIdAndStatus_inactiveSku_absentWhenQueriedAsActive() {
        Sku inactive = new Sku();
        inactive.setName("下架机型");
        inactive.setPriceCents(9900L);
        inactive.setStatus(SkuStatus.INACTIVE);
        Sku saved = skuRepository.saveAndFlush(inactive);

        assertThat(skuRepository.findByIdAndStatus(saved.getId(), SkuStatus.ACTIVE)).isEmpty();
        assertThat(skuRepository.findByIdAndStatus(saved.getId(), SkuStatus.INACTIVE)).isPresent();
        assertThat(skuRepository.findByStatus(SkuStatus.ACTIVE))
                .noneMatch(s -> s.getId().equals(saved.getId()));
    }
}
