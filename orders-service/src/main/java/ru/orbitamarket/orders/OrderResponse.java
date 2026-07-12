package ru.orbitamarket.orders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

record OrderResponse(
    @JsonProperty("order_id") UUID orderId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("product_type") OrderType productType,
    JsonNode payload,
    long price,
    OrderStatus status,
    @JsonProperty("failure_reason") String failureReason,
    @JsonProperty("created_at") Instant createdAt)
    implements java.io.Serializable {}
