package ru.orbitamarket.orders;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InboxRepository extends JpaRepository<InboxMessage, UUID> {

  @Modifying
  @Query(
      value =
          """
          insert into inbox_messages (id, processed_at)
          values (:eventId, current_timestamp)
          on conflict (id) do nothing
          """,
      nativeQuery = true)
  int register(@Param("eventId") UUID eventId);
}
