package ru.orbitamarket.orders;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

record OrderApiError(
    @JsonProperty("error_code") String errorCode, String message, Instant timestamp) {}
