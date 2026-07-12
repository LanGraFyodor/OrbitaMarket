package ru.orbitamarket.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserAccount {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "job_title", length = 100)
  private String jobTitle;

  @Column(length = 120)
  private String company;

  @Column(length = 30)
  private String phone;

  @Column(length = 500)
  private String bio;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserAccount() {}

  UserAccount(UUID id, String email, String passwordHash, String displayName, Instant now) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.createdAt = now;
    this.updatedAt = now;
  }

  UUID id() {
    return id;
  }

  String email() {
    return email;
  }

  String passwordHash() {
    return passwordHash;
  }

  String displayName() {
    return displayName;
  }

  String jobTitle() {
    return jobTitle;
  }

  String company() {
    return company;
  }

  String phone() {
    return phone;
  }

  String bio() {
    return bio;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  void updateProfile(
      String displayName, String jobTitle, String company, String phone, String bio) {
    this.displayName = displayName;
    this.jobTitle = jobTitle;
    this.company = company;
    this.phone = phone;
    this.bio = bio;
    this.updatedAt = Instant.now();
  }
}
