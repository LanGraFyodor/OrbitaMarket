package ru.orbitamarket.orders;

import org.springframework.http.HttpStatus;

final class OrderApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  private OrderApiException(String errorCode, HttpStatus status, String message) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  static OrderApiException invalidPayload() {
    return new OrderApiException(
        "INVALID_PAYLOAD",
        HttpStatus.BAD_REQUEST,
        "Required payload fields are missing or invalid");
  }

  static OrderApiException invalidPrice() {
    return new OrderApiException(
        "INVALID_PRICE", HttpStatus.BAD_REQUEST, "Price must be greater than zero");
  }

  static OrderApiException unknownProductType() {
    return new OrderApiException(
        "UNKNOWN_PRODUCT_TYPE", HttpStatus.BAD_REQUEST, "Unsupported product_type");
  }

  static OrderApiException orderNotFound() {
    return new OrderApiException("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "Order was not found");
  }

  static OrderApiException missingUserId() {
    return new OrderApiException(
        "MISSING_USER_ID", HttpStatus.BAD_REQUEST, "X-User-Id is required");
  }

  String errorCode() {
    return errorCode;
  }

  HttpStatus status() {
    return status;
  }
}
