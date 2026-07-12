package ru.orbitamarket.payments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {
  List<OutboxMessage> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
