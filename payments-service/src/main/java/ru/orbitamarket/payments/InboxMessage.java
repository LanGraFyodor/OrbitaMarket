package ru.orbitamarket.payments;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_messages")
public class InboxMessage {

  @Id private UUID id;

  private Instant processedAt = Instant.now();

  protected InboxMessage() {}
}
