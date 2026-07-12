package ru.orbitamarket.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private String userId;

  @Column(nullable = false)
  private long balance;

  @Version private long version;

  protected Account() {}

  public Account(String userId) {
    this.userId = userId;
  }

  public String getUserId() {
    return userId;
  }

  public long getBalance() {
    return balance;
  }
}
