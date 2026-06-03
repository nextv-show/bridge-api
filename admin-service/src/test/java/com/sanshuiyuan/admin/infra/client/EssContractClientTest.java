package com.sanshuiyuan.admin.infra.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * #44：字节代理应透传 ess 的非 2xx 状态，而非被 RestTemplate 默认错误处理抛成 500。
 */
@ExtendWith(MockitoExtension.class)
class EssContractClientTest {

    @Mock
    private RestTemplate restTemplate;

    private EssContractClient client() {
        return new EssContractClient(restTemplate, "http://ess:8085", "tok");
    }

    @Test
    void contractPdf_passesThroughDownstream409() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null));

        ResponseEntity<byte[]> resp = client().contractPdf(7L);
        assertEquals(409, resp.getStatusCode().value());
    }

    @Test
    void certificateDownload_passesThroughDownstream404() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        ResponseEntity<byte[]> resp = client().certificateDownload(9L);
        assertEquals(404, resp.getStatusCode().value());
    }

    @Test
    void contractPdf_success_returnsBytes() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("%PDF".getBytes()));

        ResponseEntity<byte[]> resp = client().contractPdf(7L);
        assertEquals(200, resp.getStatusCode().value());
    }
}
