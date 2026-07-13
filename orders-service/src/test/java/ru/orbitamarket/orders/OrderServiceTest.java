package ru.orbitamarket.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.orbitamarket.contracts.OrderPaymentCompleted;
import ru.orbitamarket.contracts.OrderPaymentFailed;

class OrderServiceTest {

  private final OrderRepository orders = mock(OrderRepository.class);
  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final InboxRepository inbox = mock(InboxRepository.class);
  private final OrderService service =
      new OrderService(orders, outbox, inbox, new ObjectMapper().findAndRegisterModules());

  @Test
  void createsOrderInCreatedStatusAndTransactionalOutboxMessage() {
    when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    var payload =
        JsonNodeFactory.instance
            .objectNode()
            .put("aoi", "POINT(30 60)")
            .put("capture_date", "2026-07-01")
            .put("sensor_type", "MSI");

    OrderResponse result = service.create("u1", new CreateOrderRequest("ARCHIVE", 100L, payload));

    assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
    verify(outbox).save(argThat(message -> message.getTopic().equals("orders.payment-requests")));
  }

  @Test
  void invalidArchiveIsStoredAsRejectedWithFailureReason() {
    when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    var payload = JsonNodeFactory.instance.objectNode().put("aoi", "POINT(30 60)");

    assertThatThrownBy(() -> service.create("u1", new CreateOrderRequest("ARCHIVE", 100L, payload)))
        .isInstanceOf(OrderApiException.class)
        .hasMessageContaining("payload");
    verify(orders)
        .save(
            argThat(
                order ->
                    order.getStatus() == OrderStatus.REJECTED
                        && "INVALID_PAYLOAD".equals(order.getFailureReason())));
  }

  @Test
  void rejectsMissingUser() {
    assertThatThrownBy(() -> service.list(null))
        .isInstanceOf(OrderApiException.class)
        .hasMessage("X-User-Id is required");
  }

  @Test
  void ignoresUncorrelatedAndTerminalPaymentResults() {
    Order order = Order.accepted("u1", OrderType.ARCHIVE, "{}", 100);
    when(inbox.register(any())).thenReturn(1);
    when(orders.findById(order.getId())).thenReturn(Optional.of(order));

    service.applyCompleted(
        new OrderPaymentCompleted(UUID.randomUUID(), order.getId(), "other-user", 100, 900));
    service.applyCompleted(
        new OrderPaymentCompleted(UUID.randomUUID(), order.getId(), "u1", 99, 901));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

    service.applyCompleted(
        new OrderPaymentCompleted(UUID.randomUUID(), order.getId(), "u1", 100, 900));
    service.applyFailed(
        new OrderPaymentFailed(UUID.randomUUID(), order.getId(), "u1", "INSUFFICIENT_BALANCE"));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
  }
}
