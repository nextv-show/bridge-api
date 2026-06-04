package com.sanshuiyuan.cend.usersync;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface H5UserSyncOutboxRepository extends JpaRepository<H5UserSyncOutbox, Long> {

    Optional<H5UserSyncOutbox> findByCanonicalId(String canonicalId);

    /** 取一批待重试记录（按 id 升序，分页限量），供对账任务消费。 */
    List<H5UserSyncOutbox> findByStatusOrderByIdAsc(String status, Pageable pageable);
}
