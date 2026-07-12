package ru.orbitamarket.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.orbitamarket.contracts.PaymentRequested;

@Component
class PaymentRequestedListener {

  private final ObjectMapper objectMapper;
  private final PaymentService paymentService;

  PaymentRequestedListener(ObjectMapper objectMapper, PaymentService paymentService) {
    this.objectMapper = objectMapper;
    this.paymentService = paymentService;
  }

  @KafkaListener(topics = "orders.payment-requests", groupId = "payments-service")
  public void receive(String payload) throws Exception {
    paymentService.charge(objectMapper.readValue(payload, PaymentRequested.class));
  }
}

@Component
class PaymentOutboxPublisher {

  private final OutboxRepository outbox;
  private final KafkaTemplate<String, String> kafkaTemplate;

  PaymentOutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafkaTemplate) {
    this.outbox = outbox;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Scheduled(fixedDelayString = "${app.outbox-delay-ms:500}")
  @Transactional
  public void publish() {
    for (OutboxMessage message : outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
      try {
        kafkaTemplate
            .send(message.getTopic(), message.getId().toString(), message.getPayload())
            .get(10, TimeUnit.SECONDS);
        message.markPublished();
      } catch (Exception exception) {
        throw new IllegalStateException("Kafka publish failed; outbox will be retried", exception);
      }
    }
  }
}
