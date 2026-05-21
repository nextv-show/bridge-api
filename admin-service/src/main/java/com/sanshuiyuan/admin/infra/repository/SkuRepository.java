package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.Sku;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkuRepository extends JpaRepository<Sku, Long> {
}
