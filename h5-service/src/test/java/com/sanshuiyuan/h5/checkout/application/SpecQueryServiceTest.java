package com.sanshuiyuan.h5.checkout.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.h5.checkout.api.dto.SpecDto;
import com.sanshuiyuan.h5.checkout.api.dto.SpecsResponse;
import com.sanshuiyuan.h5.checkout.domain.DeviceSpec;
import com.sanshuiyuan.h5.checkout.infra.repository.DeviceSpecRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecQueryServiceTest {

    @Mock DeviceSpecRepository repo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SpecQueryService createService() {
        return new SpecQueryService(repo, objectMapper);
    }

    private void setField(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DeviceSpec makeSpec(String specId, String modelCode, String name, long priceCents,
                                boolean recommended, String monitorLine, String featuresJson,
                                DeviceSpec.SpecStatus status) {
        try {
            var ctor = DeviceSpec.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            DeviceSpec s = ctor.newInstance();
            setField(s, "specId", specId);
            setField(s, "modelCode", modelCode);
            setField(s, "name", name);
            setField(s, "priceCents", priceCents);
            setField(s, "recommended", recommended);
            setField(s, "monitorLine", monitorLine);
            setField(s, "featuresJson", featuresJson);
            setField(s, "status", status);
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void listActiveSpecs_returnsActiveSpecs() {
        DeviceSpec spec1 = makeSpec("home-pro", "BR-H2", "家庭版·增强型", 680000L,
                true, "日产水 400L", "[\"RO反渗透\",\"远程水质\"]", DeviceSpec.SpecStatus.ACTIVE);
        DeviceSpec spec2 = makeSpec("biz-a", "BR-C1", "商用 A 型", 860000L,
                false, "日产水 1.2T", "[\"不锈钢工业级\"]", DeviceSpec.SpecStatus.ACTIVE);
        when(repo.findByStatus(DeviceSpec.SpecStatus.ACTIVE)).thenReturn(List.of(spec1, spec2));

        SpecQueryService svc = createService();
        SpecsResponse resp = svc.listActiveSpecs();

        assertThat(resp.specs()).hasSize(2);
        SpecDto dto1 = resp.specs().get(0);
        assertThat(dto1.specId()).isEqualTo("home-pro");
        assertThat(dto1.priceCents()).isEqualTo(680000L);
        assertThat(dto1.recommended()).isTrue();
        assertThat(dto1.features()).containsExactly("RO反渗透", "远程水质");
    }

    @Test
    void listActiveSpecs_emptyResult_returnsEmptyList() {
        when(repo.findByStatus(DeviceSpec.SpecStatus.ACTIVE)).thenReturn(List.of());

        SpecQueryService svc = createService();
        SpecsResponse resp = svc.listActiveSpecs();

        assertThat(resp.specs()).isEmpty();
    }

    @Test
    void listActiveSpecs_cacheableAnnotationPresent() throws NoSuchMethodException {
        // Verify the method is annotated with @Cacheable
        var method = SpecQueryService.class.getMethod("listActiveSpecs");
        var cacheable = method.getAnnotation(org.springframework.cache.annotation.Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly("device-specs");
    }
}
