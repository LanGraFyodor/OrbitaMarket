package ru.orbitamarket.orders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

record CreateOrderRequest(
    @JsonProperty("product_type") String productType, Long price, JsonNode payload) {}
