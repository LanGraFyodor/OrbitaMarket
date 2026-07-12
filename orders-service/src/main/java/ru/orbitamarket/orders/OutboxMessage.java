package ru.orbitamarket.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

  @Id private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(nullable = false)
  private String topic;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxMessage() {}

  OutboxMessage(UUID id, UUID orderId, String topic, String payload) {
    this.id = id;
    this.orderId = orderId;
    this.topic = topic;
    this.payload = payload;
  }

  UUID getId() {
    return id;
  }

  UUID getOrderId() {
    return orderId;
  }

  String getTopic() {
    return topic;
  }

  String getPayload() {
    return payload;
  }

  void markPublished() {
    publishedAt = Instant.now();
  }
}
