package ru.orbitamarket.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

record NotificationResponse(
    UUID id,
    @JsonProperty("order_id") UUID orderId,
    String type,
    String title,
    String message,
    @JsonProperty("is_read") boolean read,
    @JsonProperty("created_at") Instant createdAt) {
  static NotificationResponse from(Notification value) {
    return new NotificationResponse(
        value.id(),
        value.orderId(),
        value.type(),
        value.title(),
        value.message(),
        value.read(),
        value.createdAt());
  }
}

record NotificationError(
    @JsonProperty("error_code") String errorCode, String message, Instant timestamp) {}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {
  private final NotificationService service;

  NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  List<NotificationResponse> list(
      @RequestHeader(value = "X-User-Id", required = false) String userId) {
    return service.list(userId).stream().map(NotificationResponse::from).toList();
  }

  @GetMapping("/unread-count")
  Map<String, Long> unread(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return Map.of("unread", service.unread(userId));
  }

  @PatchMapping("/{id}/read")
  NotificationResponse read(
      @RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable UUID id) {
    return NotificationResponse.from(service.markRead(userId, id));
  }

  @PatchMapping("/read-all")
  Map<String, String> readAll(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    service.markAllRead(userId);
    return Map.of("status", "ok");
  }

  @GetMapping(value = "/stream", produces = "text/event-stream")
  SseEmitter stream(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return service.subscribe(userId);
  }
}

@RestControllerAdvice
class NotificationErrors {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<NotificationError> invalid(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(new NotificationError("MISSING_USER_ID", exception.getMessage(), Instant.now()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<NotificationError> invalidId() {
    return ResponseEntity.badRequest()
        .body(
            new NotificationError(
                "INVALID_NOTIFICATION_ID", "Notification id must be a UUID", Instant.now()));
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<NotificationError> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            new NotificationError(
                "NOTIFICATION_NOT_FOUND", "Notification was not found", Instant.now()));
  }
}
