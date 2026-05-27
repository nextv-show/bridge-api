package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 合同查询服务。
 * <p>
 * 提供管理后台多维度检索能力（订单号/SN/用户/合同ID/日期/状态）。
 */
@Service
public class ContractQueryService {

    private static final Logger log = LoggerFactory.getLogger(ContractQueryService.class);

    private final ContractRepository contractRepository;

    public ContractQueryService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /**
     * 管理后台合同列表（多维度检索）。
     * <p>
     * 支持按订单号、SN、用户ID、合同编号、状态、日期范围筛选。
     *
     * @param orderId      订单号（模糊匹配）
     * @param deviceSn     设备 SN（精确匹配）
     * @param userId       用户 ID
     * @param contractNo   合同编号（模糊匹配）
     * @param status       合同状态
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @param pageable     分页参数
     * @return 合同分页列表
     */
    @Transactional(readOnly = true)
    public Page<Contract> searchContracts(String orderId, String deviceSn, Long userId,
                                           String contractNo, String status,
                                           LocalDateTime startDate, LocalDateTime endDate,
                                           Pageable pageable) {
        log.debug("管理后台合同检索 [orderId={}, deviceSn={}, userId={}, contractNo={}, status={}, startDate={}, endDate={}]",
                orderId, deviceSn, userId, contractNo, status, startDate, endDate);

        // 全量加载后内存过滤（简化实现，生产环境应使用 JPA Specification）
        List<Contract> allContracts = contractRepository.findAll();

        List<Contract> filtered = allContracts.stream()
                .filter(c -> orderId == null || orderId.isBlank() || (c.getOrderId() != null && c.getOrderId().contains(orderId)))
                .filter(c -> deviceSn == null || deviceSn.isBlank() || deviceSn.equals(c.getDeviceSn()))
                .filter(c -> userId == null || userId.equals(c.getUserId()))
                .filter(c -> contractNo == null || contractNo.isBlank() || (c.getContractNo() != null && c.getContractNo().contains(contractNo)))
                .filter(c -> status == null || status.isBlank() || status.equals(c.getStatus().name()))
                .filter(c -> startDate == null || (c.getCreatedAt() != null && !c.getCreatedAt().isBefore(startDate)))
                .filter(c -> endDate == null || (c.getCreatedAt() != null && !c.getCreatedAt().isAfter(endDate)))
                .collect(Collectors.toList());

        // 手动分页
        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<Contract> pageContent = filtered.subList(start, end);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    /**
     * 获取合同详情（含完整审计信息）。
     *
     * @param contractId 合同 ID
     * @return 合同详情
     */
    @Transactional(readOnly = true)
    public Contract getContractDetail(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));
    }

    /**
     * 通过设备 SN 查询关联合同。
     *
     * @param deviceSn 设备 SN
     * @return 合同（如果存在）
     */
    @Transactional(readOnly = true)
    public Optional<Contract> getContractByDeviceSn(String deviceSn) {
        return contractRepository.findByDeviceSn(deviceSn);
    }

    /**
     * 通过合同编号查询。
     *
     * @param contractNo 合同编号
     * @return 合同（如果存在）
     */
    @Transactional(readOnly = true)
    public Optional<Contract> getContractByContractNo(String contractNo) {
        return contractRepository.findByContractNo(contractNo);
    }
}
