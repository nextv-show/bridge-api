package com.sanshuiyuan.settlement.infra.water;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** water_db.water_bills 只读仓库（跨库分页拉取待结算账单）。 */
public interface WaterBillRepository extends JpaRepository<WaterBillEntity, Long> {
    List<WaterBillEntity> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
