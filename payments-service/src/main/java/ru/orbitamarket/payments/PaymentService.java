package ru.orbitamarket.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.orbitamarket.contracts.OrderPaymentCompleted;
import ru.orbitamarket.contracts.OrderPaymentFailed;
import ru.orbitamarket.contracts.PaymentRequested;

@Service
class PaymentService {

  private static final String PAYMENT_RESULTS_TOPIC = "payments.results";

  private final AccountRepository accounts;
  private final PaymentRepository payments;
  private final OutboxRepository outbox;
  private final InboxRepository inbox;
  private final ObjectMapper objectMapper;

  PaymentService(
      AccountRepository accounts,
      PaymentRepository payments,
      OutboxRepository outbox,
      InboxRepository inbox,
      ObjectMapper objectMapper) {
    this.accounts = accounts;
    this.payments = payments;
    this.outbox = outbox;
    this.inbox = inbox;
    this.objectMapper = objectMapper;
  }

  @Transactional
  @CacheEvict(cacheNames = "account-balance", key = "#userId")
  AccountCreation createAccount(String userId) {
    requireUserId(userId);
    return accounts
        .findByUserId(userId)
        .map(account -> new AccountCreation(toResponse(account), false))
        .orElseGet(() -> new AccountCreation(toResponse(accounts.save(new Account(userId))), true));
  }

  @Transactional
  @CacheEvict(cacheNames = "account-balance", key = "#userId")
  BalanceResponse topUp(String userId, long amount) {
    requireUserId(userId);
    if (amount <= 0) {
      throw PaymentApiException.invalidAmount();
    }
    if (accounts.credit(userId, amount) == 0) {
      throw PaymentApiException.accountNotFound();
    }
    return balance(userId);
  }

  @Transactional
  @Cacheable(
      cacheNames = "account-balance",
      key = "#userId",
      condition = "#userId != null && !#userId.isBlank()",
      sync = true)
  BalanceResponse balance(String userId) {
    requireUserId(userId);
    return accounts
        .findByUserId(userId)
        .map(this::toResponse)
        .orElseThrow(PaymentApiException::accountNotFound);
  }

  @Transactional
  @CacheEvict(cacheNames = "account-balance", key = "#request.userId()")
  void charge(PaymentRequested request) {
    if (inbox.register(request.eventId()) == 0) {
      return;
    }

    Payment payment =
        payments.findByOrderId(request.orderId()).orElseGet(() -> createPayment(request));
    enqueuePaymentResult(payment);
  }

  private Payment createPayment(PaymentRequested request) {
    boolean paid = accounts.debitIfEnough(request.userId(), request.amount()) == 1;
    Long balanceAfter =
        paid ? accounts.findByUserId(request.userId()).map(Account::getBalance).orElse(null) : null;
    PaymentStatus status = paid ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
    String failureReason = paid ? null : "INSUFFICIENT_BALANCE";
    return payments.save(
        new Payment(
            request.orderId(),
            request.userId(),
            request.amount(),
            balanceAfter,
            status,
            failureReason));
  }

  private void enqueuePaymentResult(Payment payment) {
    Object result;
    UUID eventId = UUID.randomUUID();
    if (payment.getStatus() == PaymentStatus.COMPLETED) {
      result =
          new OrderPaymentCompleted(
              eventId,
              payment.getOrderId(),
              payment.getUserId(),
              payment.getAmount(),
              payment.getBalanceAfter());
    } else {
      result =
          new OrderPaymentFailed(
              eventId, payment.getOrderId(), payment.getUserId(), payment.getFailureReason());
    }
    try {
      outbox.save(
          new OutboxMessage(
              eventId, PAYMENT_RESULTS_TOPIC, objectMapper.writeValueAsString(result)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize payment result", exception);
    }
  }

  private BalanceResponse toResponse(Account account) {
    return new BalanceResponse(account.getUserId(), account.getBalance(), "geocredits");
  }

  private void requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw PaymentApiException.missingUserId();
    }
  }
}
