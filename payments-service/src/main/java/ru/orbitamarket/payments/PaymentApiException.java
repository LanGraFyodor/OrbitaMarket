package ru.orbitamarket.payments;

import org.springframework.http.HttpStatus;

final class PaymentApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  private PaymentApiException(String errorCode, HttpStatus status, String message) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  static PaymentApiException missingUserId() {
    return new PaymentApiException(
        "MISSING_USER_ID", HttpStatus.BAD_REQUEST, "X-User-Id is required");
  }

  static PaymentApiException accountNotFound() {
    return new PaymentApiException(
        "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "Account was not found");
  }

  static PaymentApiException invalidAmount() {
    return new PaymentApiException(
        "INVALID_AMOUNT", HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
  }

  String errorCode() {
    return errorCode;
  }

  HttpStatus status() {
    return status;
  }
}
