package ru.orbitamarket.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "user_id", nullable = false, updatable = false)
  private String userId;

  @Enumerated(EnumType.STRING)
  private OrderType type;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(nullable = false)
  private long price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.CREATED;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Order() {}

  private Order(String userId, OrderType type, String payload, long price) {
    this.userId = userId;
    this.type = type;
    this.payload = payload;
    this.price = price;
  }

  static Order accepted(String userId, OrderType type, String payload, long price) {
    return new Order(userId, type, payload, price);
  }

  static Order rejected(
      String userId, OrderType type, String payload, long price, String failureReason) {
    Order order = new Order(userId, type, payload, price);
    order.status = OrderStatus.REJECTED;
    order.failureReason = failureReason;
    return order;
  }

  void markPaid() {
    status = OrderStatus.PAID;
    failureReason = null;
  }

  void markPaymentFailed(String reason) {
    status = OrderStatus.PAYMENT_FAILED;
    failureReason = reason;
  }

  UUID getId() {
    return id;
  }

  String getUserId() {
    return userId;
  }

  OrderType getType() {
    return type;
  }

  String getPayload() {
    return payload;
  }

  long getPrice() {
    return price;
  }

  OrderStatus getStatus() {
    return status;
  }

  String getFailureReason() {
    return failureReason;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
