package ru.orbitamarket.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record OrderPaymentCompleted(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("order_id") UUID orderId,
    @JsonProperty("user_id") String userId,
    long amount,
    @JsonProperty("new_balance") long newBalance) {}
