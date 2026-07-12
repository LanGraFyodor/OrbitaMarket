package ru.orbitamarket.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record OrderPaymentFailed(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("order_id") UUID orderId,
    @JsonProperty("user_id") String userId,
    String reason) {}
