package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.exception.EssApiException;
import com.sanshuiyuan.ess.exception.EssCallbackVerificationException;
import com.sanshuiyuan.ess.exception.EssFlowException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EssExceptionHandlerTest {

    private final EssExceptionHandler handler = new EssExceptionHandler();

    @Test
    void handleApiException_shouldReturnBadGateway() {
        var ex = new EssApiException("CreateFlow", "timeout");
        ResponseEntity<Map<String, Object>> response = handler.handleApiException(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(-1, response.getBody().get("code"));
        assertEquals("EssApiError", response.getBody().get("error"));
    }

    @Test
    void handleFlowException_shouldReturnUnprocessableEntity() {
        var ex = new EssFlowException("c-001", "flow-001", "flow not found");
        ResponseEntity<Map<String, Object>> response = handler.handleFlowException(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(-2, response.getBody().get("code"));
        assertEquals("EssFlowError", response.getBody().get("error"));
    }

    @Test
    void handleCallbackVerificationException_shouldReturnUnauthorized() {
        var ex = new EssCallbackVerificationException("签名不匹配");
        ResponseEntity<Map<String, Object>> response = handler.handleCallbackVerificationException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(-3, response.getBody().get("code"));
        assertEquals("CallbackVerificationError", response.getBody().get("error"));
    }

    @Test
    void handleGenericException_shouldReturnInternalServerError() {
        var ex = new RuntimeException("unexpected");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(-99, response.getBody().get("code"));
        assertEquals("InternalServerError", response.getBody().get("error"));
    }
}
