package com.sanshuiyuan.asset.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.asset.application.CreateOrderUseCase;
import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * C.2.6 (controller branches): create order success / address missing (bean-validation
 * rejection) / get order detail of another user.
 *
 * Security note: the production JwtBearerFilter lives in a SecurityFilterChain bean not imported
 * by @WebMvcTest, so a permissive TestSecurityConfig is imported instead. Keeping a real (but
 * permit-all) chain preserves the SecurityContextHolderFilter, which lets the authentication()
 * post-processor seed a Long principal that @AuthenticationPrincipal Long userId resolves.
 */
@WebMvcTest(OrderController.class)
@Import(TestSecurityConfig.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    CreateOrderUseCase createOrderUseCase;

    @MockBean
    OrderRepository orderRepository;

    private RequestPostProcessor principal(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Order order(Long id, Long userId) {
        Order o = new Order();
        o.setId(id);
        o.setUserId(userId);
        o.setSkuId(1L);
        o.setQty(1);
        o.setAmountCents(19900L);
        o.setStatus(OrderStatus.PENDING_PAY);
        return o;
    }

    @Test
    void createOrder_success_returnsPendingPayOrder() throws Exception {
        when(createOrderUseCase.createOrder(eq(7L), eq(1L), eq(2), anyString()))
                .thenReturn(order(100L, 7L));

        String body = objectMapper.writeValueAsString(
                Map.of("skuId", 1, "qty", 2, "address", "{\"city\":\"SH\"}"));

        mockMvc.perform(post("/orders").with(principal(7L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PENDING_PAY"));
    }

    @Test
    void createOrder_missingAddress_failsBeanValidation() throws Exception {
        // CreateOrderRequest.address is @NotBlank; omitting it triggers
        // MethodArgumentNotValidException, which GlobalExceptionHandler maps to 422 with a
        // {error,message} body (C.2.6).
        String body = objectMapper.writeValueAsString(Map.of("skuId", 1, "qty", 1));

        mockMvc.perform(post("/orders").with(principal(7L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void getOrder_ownedByUser_returnsOrder() throws Exception {
        when(orderRepository.findById(eq(100L)))
                .thenReturn(Optional.of(order(100L, 7L)));

        mockMvc.perform(get("/orders/100").with(principal(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    void getOrder_missing_returns404() throws Exception {
        when(orderRepository.findById(eq(404L)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/404").with(principal(7L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_notOwnedByUser_returns403() throws Exception {
        // C.2.6: the controller now loads by id and rejects a non-owner with
        // NotOrderOwnerException, which GlobalExceptionHandler maps to 403 FORBIDDEN.
        when(orderRepository.findById(eq(100L)))
                .thenReturn(Optional.of(order(100L, 7L)));

        mockMvc.perform(get("/orders/100").with(principal(9L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void listMyOrders_byStatus_delegatesToRepository() throws Exception {
        when(orderRepository.findByUserIdAndStatus(eq(7L), eq(OrderStatus.PENDING_PAY)))
                .thenReturn(List.of(order(100L, 7L)));

        mockMvc.perform(get("/orders/mine").param("status", "PENDING_PAY").with(principal(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
