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
import org.junit.jupiter.api.Test;

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
}
