package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EssFlowRecordRepository extends JpaRepository<EssFlowRecord, Long> {

    Optional<EssFlowRecord> findByContractId(String contractId);

    Optional<EssFlowRecord> findByEssFlowId(String essFlowId);

    List<EssFlowRecord> findByFlowStatus(FlowStatus flowStatus);

    List<EssFlowRecord> findByFlowStatusIn(List<FlowStatus> statuses);
}
