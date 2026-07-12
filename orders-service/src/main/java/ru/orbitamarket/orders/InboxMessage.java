package ru.orbitamarket.orders;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_messages")
public class InboxMessage {
  @Id public UUID id;
  public Instant processedAt = Instant.now();

  protected InboxMessage() {}

  InboxMessage(UUID id) {
    this.id = id;
  }
}
