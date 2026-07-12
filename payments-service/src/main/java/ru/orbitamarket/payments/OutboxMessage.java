package ru.orbitamarket.payments;

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

  @Column(nullable = false)
  private String topic;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  private Instant publishedAt;

  protected OutboxMessage() {}

  public OutboxMessage(UUID id, String topic, String payload) {
    this.id = id;
    this.topic = topic;
    this.payload = payload;
  }

  public UUID getId() {
    return id;
  }

  public String getTopic() {
    return topic;
  }

  public String getPayload() {
    return payload;
  }

  public void markPublished() {
    publishedAt = Instant.now();
  }
}
