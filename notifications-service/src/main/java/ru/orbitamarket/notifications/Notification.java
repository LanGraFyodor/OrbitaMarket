package ru.orbitamarket.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
class Notification {
  @Id private UUID id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "order_id")
  private UUID orderId;

  @Column(nullable = false, length = 40)
  private String type;

  @Column(nullable = false, length = 140)
  private String title;

  @Column(nullable = false, length = 500)
  private String message;

  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Notification() {}

  Notification(
      UUID eventId, String userId, UUID orderId, String type, String title, String message) {
    this.id = UUID.randomUUID();
    this.eventId = eventId;
    this.userId = userId;
    this.orderId = orderId;
    this.type = type;
    this.title = title;
    this.message = message;
    this.createdAt = Instant.now();
  }

  UUID id() {
    return id;
  }

  String userId() {
    return userId;
  }

  UUID orderId() {
    return orderId;
  }

  String type() {
    return type;
  }

  String title() {
    return title;
  }

  String message() {
    return message;
  }

  boolean read() {
    return read;
  }

  Instant createdAt() {
    return createdAt;
  }

  void markRead() {
    this.read = true;
  }
}
