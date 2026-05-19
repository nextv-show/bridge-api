package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.Sku;
import com.sanshuiyuan.asset.domain.SkuStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkuRepository extends JpaRepository<Sku, Long> {
    List<Sku> findByStatus(SkuStatus status);
    Optional<Sku> findByIdAndStatus(Long id, SkuStatus status);
}
