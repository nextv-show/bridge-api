package com.sanshuiyuan.cend.checkout.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.cend.checkout.api.dto.SpecDto;
import com.sanshuiyuan.cend.checkout.api.dto.SpecsResponse;
import com.sanshuiyuan.cend.checkout.domain.DeviceSpec;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpecQueryService {

    private static final Logger log = LoggerFactory.getLogger(SpecQueryService.class);

    private final DeviceSpecRepository repo;
    private final ObjectMapper objectMapper;

    public SpecQueryService(DeviceSpecRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "device-specs", unless = "#result.specs().isEmpty()")
    public SpecsResponse listActiveSpecs() {
        List<DeviceSpec> specs = repo.findByStatus(DeviceSpec.SpecStatus.ACTIVE);
        List<SpecDto> dtos = specs.stream().map(this::toDto).toList();
        return new SpecsResponse(dtos);
    }

    private SpecDto toDto(DeviceSpec s) {
        List<String> features = parseFeatures(s.getFeaturesJson());
        return new SpecDto(
            s.getSpecId(), s.getModelCode(), s.getName(),
            s.getPriceCents(), s.isRecommended(), s.getMonitorLine(), features
        );
    }

    private List<String> parseFeatures(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse features_json: {}", json);
            return List.of();
        }
    }
}
