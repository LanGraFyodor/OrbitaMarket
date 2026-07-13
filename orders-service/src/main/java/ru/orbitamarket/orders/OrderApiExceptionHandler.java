package ru.orbitamarket.orders;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class OrderApiExceptionHandler {

  @ExceptionHandler(OrderApiException.class)
  ResponseEntity<OrderApiError> handle(OrderApiException exception) {
    return ResponseEntity.status(exception.status())
        .body(new OrderApiError(exception.errorCode(), exception.getMessage(), Instant.now()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<OrderApiError> malformedJson() {
    return ResponseEntity.badRequest()
        .body(new OrderApiError("INVALID_PAYLOAD", "Malformed JSON request", Instant.now()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<OrderApiError> invalidPathParameter() {
    return ResponseEntity.badRequest()
        .body(new OrderApiError("INVALID_ORDER_ID", "order_id must be a UUID", Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<OrderApiError> internalError() {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new OrderApiError("INTERNAL_ERROR", "Unexpected server error", Instant.now()));
  }
}
