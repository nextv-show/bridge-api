package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.Order;
import com.sanshuiyuan.asset.domain.OrderStatus;
import com.sanshuiyuan.asset.domain.Sku;
import com.sanshuiyuan.asset.domain.SkuStatus;
import com.sanshuiyuan.asset.infra.repository.OrderRepository;
import com.sanshuiyuan.asset.infra.repository.SkuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C.2.6: pure Mockito unit tests for CreateOrderUseCase covering order creation
 * (status PENDING_PAY, amount = priceCents * qty), inactive/missing SKU rejection,
 * and the documented address-validation gap.
 */
@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    SkuRepository skuRepository;

    @InjectMocks
    CreateOrderUseCase useCase;

    private Sku activeSku(long priceCents) {
        Sku s = new Sku();
        s.setId(1L);
        s.setName("基础饮水机");
        s.setPriceCents(priceCents);
        s.setStatus(SkuStatus.ACTIVE);
        return s;
    }

    @Test
    void createOrder_success_setsPendingPayAndComputesAmount() {
        when(skuRepository.findByIdAndStatus(1L, SkuStatus.ACTIVE))
                .thenReturn(Optional.of(activeSku(19900L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.createOrder(7L, 1L, 3, "{\"city\":\"SH\"}");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getSkuId()).isEqualTo(1L);
        assertThat(saved.getQty()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAY);
        assertThat(saved.getAmountCents()).isEqualTo(19900L * 3);
        assertThat(saved.getAddressSnapshot()).isEqualTo("{\"city\":\"SH\"}");
        assertThat(saved.getWxPrepayId()).startsWith("mock_prepay_id_");
        assertThat(result).isSameAs(saved);
    }

    @Test
    void createOrder_inactiveOrMissingSku_throwsSkuUnavailable() {
        when(skuRepository.findByIdAndStatus(eq(99L), eq(SkuStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        // C.2.6: invalid/inactive SKU now raises SkuUnavailableException (mapped to 409).
        assertThatThrownBy(() -> useCase.createOrder(7L, 99L, 1, "{\"city\":\"SH\"}"))
                .isInstanceOf(SkuUnavailableException.class)
                .hasMessageContaining("Invalid or inactive SKU");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_nullAddress_isNotValidatedByUseCase() {
        // GAP vs tasks C.2.6 ("地址缺失"): CreateOrderUseCase does NOT validate addressJson.
        // Bean validation on CreateOrderRequest only guards the controller path; calling the
        // use case directly with a null address succeeds and persists null addressSnapshot.
        when(skuRepository.findByIdAndStatus(1L, SkuStatus.ACTIVE))
                .thenReturn(Optional.of(activeSku(19900L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.createOrder(7L, 1L, 1, null);

        assertThat(result.getAddressSnapshot()).isNull();
        verify(orderRepository).save(any(Order.class));
    }
}
