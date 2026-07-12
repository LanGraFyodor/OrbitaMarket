package ru.orbitamarket.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequested(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("order_id") UUID orderId,
    @JsonProperty("user_id") String userId,
    long amount,
    @JsonProperty("occurred_at") Instant occurredAt) {}
