package io.github.hjle.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.order.dto.OrderEntity;
import io.github.hjle.order.dto.request.OrderRequest;
import io.github.hjle.order.outbox.OutboxEntity;
import io.github.hjle.order.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock MemberServiceClient memberServiceClient;

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, outboxRepository, memberServiceClient, objectMapper);
    }

    @Test
    void getOrderById_returns_order() {
        OrderEntity order = OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderEntity result = orderService.getOrderById(1L);

        assertThat(result).isEqualTo(order);
    }

    @Test
    void getOrderById_throws_when_not_found() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void cancelOrder_sets_status_cancelled_and_saves_outbox() {
        OrderEntity order = OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxRepository).save(any(OutboxEntity.class));
    }

    @Test
    void cancelOrder_throws_when_already_cancelled() {
        OrderEntity order = OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build();
        order.cancel();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelOrder_throws_when_refunded() {
        OrderEntity order = spy(OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build());
        doReturn(OrderStatus.REFUNDED).when(order).getStatus();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class);
    }
}
