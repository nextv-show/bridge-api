package com.sanshuiyuan.asset.infra.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String s2sToken;

    public UserServiceClient(RestTemplate restTemplate,
                             @Value("${user-service.base-url}") String baseUrl,
                             @Value("${user-service.s2s-token}") String s2sToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.s2sToken = s2sToken;
    }

    public void addOwnerRole(Long userId) {
        String url = baseUrl + "/internal/users/" + userId + "/roles";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", s2sToken);

        Map<String, String> body = Map.of("role", "OWNER");
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(url, request, Void.class);
    }
}
