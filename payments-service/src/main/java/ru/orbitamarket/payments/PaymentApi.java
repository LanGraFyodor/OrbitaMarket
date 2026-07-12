package ru.orbitamarket.payments;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

record TopUpRequest(@Positive long amount) {}

record BalanceResponse(@JsonProperty("user_id") String userId, long balance, String currency)
    implements java.io.Serializable {}

record AccountCreation(BalanceResponse account, boolean created) {}

record ApiError(@JsonProperty("error_code") String errorCode, String message, Instant timestamp) {}

@RestController
@RequestMapping("/api/v1/payments")
class AccountsController {

  private final PaymentService paymentService;

  AccountsController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/accounts")
  ResponseEntity<BalanceResponse> createAccount(
      @RequestHeader(value = "X-User-Id", required = false) String userId) {
    AccountCreation creation = paymentService.createAccount(userId);
    return ResponseEntity.status(creation.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(creation.account());
  }

  @PostMapping("/accounts/top-up")
  BalanceResponse topUp(
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @Valid @RequestBody TopUpRequest request) {
    return paymentService.topUp(userId, request.amount());
  }

  @GetMapping("/accounts/balance")
  BalanceResponse balance(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return paymentService.balance(userId);
  }
}

@RestControllerAdvice
class PaymentApiExceptionHandler {

  @ExceptionHandler(PaymentApiException.class)
  ResponseEntity<ApiError> handle(PaymentApiException exception) {
    return ResponseEntity.status(exception.status())
        .body(new ApiError(exception.errorCode(), exception.getMessage(), Instant.now()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> invalidAmount() {
    return ResponseEntity.badRequest()
        .body(new ApiError("INVALID_AMOUNT", "Amount must be greater than zero", Instant.now()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> nonNumericAmount() {
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                "INVALID_AMOUNT", "Amount must be a number greater than zero", Instant.now()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> duplicateAccount() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError("ACCOUNT_ALREADY_EXISTS", "Account already exists", Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> internalError() {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError("INTERNAL_ERROR", "Unexpected server error", Instant.now()));
  }
}
