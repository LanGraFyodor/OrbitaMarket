package ru.orbitamarket.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.orbitamarket.contracts.OrderPaymentCompleted;
import ru.orbitamarket.contracts.OrderPaymentFailed;
import ru.orbitamarket.contracts.PaymentRequested;

@Service
class OrderService {

  private static final String PAYMENT_REQUESTS_TOPIC = "orders.payment-requests";

  private final OrderRepository orders;
  private final OutboxRepository outbox;
  private final InboxRepository inbox;
  private final ObjectMapper objectMapper;

  OrderService(
      OrderRepository orders,
      OutboxRepository outbox,
      InboxRepository inbox,
      ObjectMapper objectMapper) {
    this.orders = orders;
    this.outbox = outbox;
    this.inbox = inbox;
    this.objectMapper = objectMapper;
  }

  @Transactional(dontRollbackOn = OrderApiException.class)
  @CacheEvict(
      cacheNames = {"orders-by-user", "order-by-id"},
      allEntries = true)
  OrderResponse create(String userId, CreateOrderRequest request) {
    requireUserId(userId);
    OrderType productType = parseProductType(userId, request);
    long price = validatePrice(userId, productType, request);
    validatePayload(userId, productType, price, request.payload());

    Order order =
        orders.save(
            Order.accepted(userId, productType, serializePayload(request.payload()), price));
    PaymentRequested event =
        new PaymentRequested(UUID.randomUUID(), order.getId(), userId, price, Instant.now());
    outbox.save(
        new OutboxMessage(
            event.eventId(), order.getId(), PAYMENT_REQUESTS_TOPIC, serializeEvent(event)));
    return toResponse(order);
  }

  @Transactional
  @Cacheable(
      cacheNames = "orders-by-user",
      key = "#userId",
      condition = "#userId != null && !#userId.isBlank()",
      sync = true)
  List<OrderResponse> list(String userId) {
    requireUserId(userId);
    return orders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
  }

  @Transactional
  @Cacheable(
      cacheNames = "order-by-id",
      key = "#userId + ':' + #orderId",
      condition = "#userId != null && !#userId.isBlank()",
      sync = true)
  OrderResponse get(String userId, UUID orderId) {
    requireUserId(userId);
    return orders
        .findById(orderId)
        .filter(order -> order.getUserId().equals(userId))
        .map(this::toResponse)
        .orElseThrow(OrderApiException::orderNotFound);
  }

  @Transactional
  @CacheEvict(
      cacheNames = {"orders-by-user", "order-by-id"},
      allEntries = true)
  void applyCompleted(OrderPaymentCompleted result) {
    if (inbox.register(result.eventId()) == 0) {
      return;
    }
    Order order = orders.findById(result.orderId()).orElseThrow(OrderApiException::orderNotFound);
    if (!matchesPaymentResult(order, result.userId(), result.amount())) {
      return;
    }
    order.markPaid();
  }

  @Transactional
  @CacheEvict(
      cacheNames = {"orders-by-user", "order-by-id"},
      allEntries = true)
  void applyFailed(OrderPaymentFailed result) {
    if (inbox.register(result.eventId()) == 0) {
      return;
    }
    Order order = orders.findById(result.orderId()).orElseThrow(OrderApiException::orderNotFound);
    if (!matchesPaymentResult(order, result.userId())) {
      return;
    }
    order.markPaymentFailed(result.reason());
  }

  private boolean matchesPaymentResult(Order order, String userId) {
    return order.awaitsPaymentResult() && order.getUserId().equals(userId);
  }

  private boolean matchesPaymentResult(Order order, String userId, long amount) {
    return matchesPaymentResult(order, userId) && order.getPrice() == amount;
  }

  private OrderType parseProductType(String userId, CreateOrderRequest request) {
    try {
      return OrderType.valueOf(request.productType());
    } catch (RuntimeException exception) {
      saveRejected(userId, null, request, "UNKNOWN_PRODUCT_TYPE");
      throw OrderApiException.unknownProductType();
    }
  }

  private long validatePrice(String userId, OrderType productType, CreateOrderRequest request) {
    if (request.price() == null || request.price() <= 0) {
      saveRejected(userId, productType, request, "INVALID_PRICE");
      throw OrderApiException.invalidPrice();
    }
    return request.price();
  }

  private void validatePayload(String userId, OrderType productType, long price, JsonNode payload) {
    if (!validPayload(productType, payload)) {
      orders.save(
          Order.rejected(userId, productType, serializePayload(payload), price, "INVALID_PAYLOAD"));
      throw OrderApiException.invalidPayload();
    }
  }

  private boolean validPayload(OrderType productType, JsonNode payload) {
    if (payload == null || !payload.isObject() || !hasText(payload, "aoi")) {
      return false;
    }
    return switch (productType) {
      case ARCHIVE -> hasText(payload, "capture_date") && hasText(payload, "sensor_type");
      case TASKING ->
          hasText(payload, "sensor_type")
              && payload.path("time_window").isObject()
              && hasText(payload.path("time_window"), "from")
              && hasText(payload.path("time_window"), "to");
      case MONITORING ->
          ("DAILY".equals(payload.path("cadence").asText())
                  || "WEEKLY".equals(payload.path("cadence").asText()))
              && payload.path("duration_days").isIntegralNumber()
              && payload.path("duration_days").asInt() > 0;
    };
  }

  private void saveRejected(
      String userId, OrderType productType, CreateOrderRequest request, String reason) {
    long price = request.price() == null ? 0 : request.price();
    orders.save(
        Order.rejected(userId, productType, serializePayload(request.payload()), price, reason));
  }

  private String serializePayload(JsonNode payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw OrderApiException.invalidPayload();
    }
  }

  private String serializeEvent(PaymentRequested event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize payment request", exception);
    }
  }

  private OrderResponse toResponse(Order order) {
    try {
      return new OrderResponse(
          order.getId(),
          order.getUserId(),
          order.getType(),
          objectMapper.readTree(order.getPayload()),
          order.getPrice(),
          order.getStatus(),
          order.getFailureReason(),
          order.getCreatedAt());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored order payload is invalid", exception);
    }
  }

  @CacheEvict(
      cacheNames = {"orders-by-user", "order-by-id"},
      allEntries = true)
  public void evictReadCaches() {
    // Invoked after direct repository status updates performed by the outbox publisher.
  }

  private void requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw OrderApiException.missingUserId();
    }
  }

  private boolean hasText(JsonNode node, String field) {
    return node.path(field).isTextual() && !node.path(field).asText().isBlank();
  }
}
