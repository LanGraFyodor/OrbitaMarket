package ru.orbitamarket.notifications;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository extends JpaRepository<Notification, UUID> {
  boolean existsByEventId(UUID eventId);

  List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

  long countByUserIdAndReadFalse(String userId);
}
