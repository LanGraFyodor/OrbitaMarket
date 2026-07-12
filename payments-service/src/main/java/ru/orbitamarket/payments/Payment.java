package ru.orbitamarket.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private String userId;

  @Column(nullable = false)
  private long amount;

  @Column(name = "balance_after")
  private Long balanceAfter;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status;

  private String failureReason;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Payment() {}

  public Payment(
      UUID orderId,
      String userId,
      long amount,
      Long balanceAfter,
      PaymentStatus status,
      String failureReason) {
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.balanceAfter = balanceAfter;
    this.status = status;
    this.failureReason = failureReason;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public String getUserId() {
    return userId;
  }

  public long getAmount() {
    return amount;
  }

  public Long getBalanceAfter() {
    return balanceAfter;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
