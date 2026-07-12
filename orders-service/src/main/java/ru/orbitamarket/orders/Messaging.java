package ru.orbitamarket.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.orbitamarket.contracts.OrderPaymentCompleted;
import ru.orbitamarket.contracts.OrderPaymentFailed;

@Component
class PaymentResultListener {

  private final ObjectMapper objectMapper;
  private final OrderService orderService;

  PaymentResultListener(ObjectMapper objectMapper, OrderService orderService) {
    this.objectMapper = objectMapper;
    this.orderService = orderService;
  }

  @KafkaListener(topics = "payments.results", groupId = "orders-service")
  public void receive(String payload) throws Exception {
    JsonNode event = objectMapper.readTree(payload);
    if (event.hasNonNull("reason")) {
      orderService.applyFailed(objectMapper.treeToValue(event, OrderPaymentFailed.class));
    } else {
      orderService.applyCompleted(objectMapper.treeToValue(event, OrderPaymentCompleted.class));
    }
  }
}

@Component
class OrderOutboxPublisher {

  private final OutboxRepository outbox;
  private final OrderRepository orders;
  private final OrderService orderService;
  private final KafkaTemplate<String, String> kafkaTemplate;

  OrderOutboxPublisher(
      OutboxRepository outbox,
      OrderRepository orders,
      OrderService orderService,
      KafkaTemplate<String, String> kafkaTemplate) {
    this.outbox = outbox;
    this.orders = orders;
    this.orderService = orderService;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Scheduled(fixedDelayString = "${app.outbox-delay-ms:500}")
  @Transactional
  public void publish() {
    boolean changed = false;
    for (OutboxMessage message : outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
      try {
        kafkaTemplate
            .send(message.getTopic(), message.getId().toString(), message.getPayload())
            .get(10, TimeUnit.SECONDS);
        orders.markPaymentPending(message.getOrderId());
        message.markPublished();
        changed = true;
      } catch (Exception exception) {
        throw new IllegalStateException("Kafka publish failed; outbox will be retried", exception);
      }
    }
    if (changed) {
      orderService.evictReadCaches();
    }
  }
}
