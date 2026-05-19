package com.sanshuiyuan.user.infra.repository;

import com.sanshuiyuan.user.domain.HomeLayoutPref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HomeLayoutPrefRepository extends JpaRepository<HomeLayoutPref, Long> {
    Optional<HomeLayoutPref> findByUserId(Long userId);
}
