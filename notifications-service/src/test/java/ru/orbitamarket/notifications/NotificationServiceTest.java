package ru.orbitamarket.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {
  private NotificationRepository repository;
  private NotificationService service;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationRepository.class);
    when(repository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    service = new NotificationService(repository, new ObjectMapper());
  }

  @Test
  void createsUserNotificationFromPaymentCompletedEvent() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    String payload =
        """
        {"event_id":"%s","order_id":"%s","user_id":"user-42","amount":120,"new_balance":880}
        """
            .formatted(eventId, orderId);

    service.paymentResult(payload);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository).save(captor.capture());
    assertEquals("user-42", captor.getValue().userId());
    assertEquals("PAYMENT_COMPLETED", captor.getValue().type());
    assertEquals(orderId, captor.getValue().orderId());
  }

  @Test
  void ignoresRepeatedKafkaEvent() throws Exception {
    UUID eventId = UUID.randomUUID();
    when(repository.existsByEventId(eventId)).thenReturn(true);
    String payload =
        """
        {"event_id":"%s","order_id":"%s","user_id":"user-42","reason":"INSUFFICIENT_BALANCE"}
        """
            .formatted(eventId, UUID.randomUUID());

    service.paymentResult(payload);

    verify(repository, never()).save(any());
  }

  @Test
  void refusesToReadAnotherUsersNotification() {
    Notification notification =
        new Notification(
            UUID.randomUUID(), "owner", UUID.randomUUID(), "PAYMENT_COMPLETED", "Paid", "Ready");
    when(repository.findById(notification.id())).thenReturn(Optional.of(notification));

    assertThrows(
        IllegalArgumentException.class, () -> service.markRead("attacker", notification.id()));
  }
}
