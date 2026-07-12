package ru.orbitamarket.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
class NotificationService {
  private final NotificationRepository repository;
  private final ObjectMapper objectMapper;
  private final Map<String, List<SseEmitter>> clients = new ConcurrentHashMap<>();

  NotificationService(NotificationRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "payments.results")
  @Transactional
  void paymentResult(String payload) throws IOException {
    JsonNode event = objectMapper.readTree(payload);
    UUID eventId = UUID.fromString(event.path("event_id").asText());
    if (repository.existsByEventId(eventId)) return;

    String userId = event.path("user_id").asText();
    UUID orderId = UUID.fromString(event.path("order_id").asText());
    boolean failed = event.hasNonNull("reason");
    Notification notification =
        repository.save(
            new Notification(
                eventId,
                userId,
                orderId,
                failed ? "PAYMENT_FAILED" : "PAYMENT_COMPLETED",
                failed ? "Оплата не прошла" : "Миссия оплачена",
                failed
                    ? "Проверьте баланс заказа " + shortId(orderId)
                    : "Заказ " + shortId(orderId) + " передан в обработку"));
    broadcast(notification);
  }

  List<Notification> list(String userId) {
    return repository.findTop50ByUserIdOrderByCreatedAtDesc(requireUser(userId));
  }

  long unread(String userId) {
    return repository.countByUserIdAndReadFalse(requireUser(userId));
  }

  @Transactional
  Notification markRead(String userId, UUID id) {
    Notification notification = repository.findById(id).orElseThrow();
    if (!notification.userId().equals(requireUser(userId)))
      throw new IllegalArgumentException("Notification belongs to another user");
    notification.markRead();
    return notification;
  }

  @Transactional
  void markAllRead(String userId) {
    list(userId).forEach(Notification::markRead);
  }

  SseEmitter subscribe(String userId) {
    String currentUser = requireUser(userId);
    SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
    clients.computeIfAbsent(currentUser, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
    Runnable cleanup =
        () -> {
          List<SseEmitter> emitters = clients.get(currentUser);
          if (emitters != null) emitters.remove(emitter);
        };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ignored -> cleanup.run());
    try {
      emitter.send(SseEmitter.event().name("connected").data("ready"));
    } catch (IOException exception) {
      cleanup.run();
    }
    return emitter;
  }

  private void broadcast(Notification notification) {
    List<SseEmitter> emitters = clients.get(notification.userId());
    if (emitters == null) return;
    emitters.removeIf(
        emitter -> {
          try {
            emitter.send(
                SseEmitter.event()
                    .name("notification")
                    .data(NotificationResponse.from(notification)));
            return false;
          } catch (IOException exception) {
            emitter.complete();
            return true;
          }
        });
  }

  private static String requireUser(String userId) {
    if (userId == null || userId.isBlank())
      throw new IllegalArgumentException("X-User-Id is required");
    return userId;
  }

  private static String shortId(UUID id) {
    return id.toString().substring(0, 8).toUpperCase();
  }
}
