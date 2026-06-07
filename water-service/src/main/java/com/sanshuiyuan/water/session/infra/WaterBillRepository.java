package com.sanshuiyuan.water.session.infra;

import com.sanshuiyuan.water.session.domain.WaterBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaterBillRepository extends JpaRepository<WaterBill, Long> {

    Optional<WaterBill> findBySessionId(Long sessionId);

    List<WaterBill> findByUserIdOrderBySettledAtDesc(Long userId);
}
