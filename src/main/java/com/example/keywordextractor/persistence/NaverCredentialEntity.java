package com.example.keywordextractor.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "naver_credentials")
public class NaverCredentialEntity {

  @Id
  @Column(name = "customer_id", nullable = false)
  private String customerId;

  @Column(name = "api_key", nullable = false)
  private String apiKey;

  @Column(name = "api_secret", nullable = false)
  private String apiSecret;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "daily_limit", nullable = false)
  private int dailyLimit;

  @Column(name = "max_in_flight", nullable = false)
  private int maxInFlight;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NaverCredentialEntity() {}

  public NaverCredentialEntity(
      String customerId,
      String apiKey,
      String apiSecret,
      boolean enabled,
      int dailyLimit,
      int maxInFlight,
      Instant createdAt,
      Instant updatedAt) {
    this.customerId = customerId;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.enabled = enabled;
    this.dailyLimit = dailyLimit;
    this.maxInFlight = maxInFlight;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public String getCustomerId() {
    return customerId;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getApiSecret() {
    return apiSecret;
  }

  public void setApiSecret(String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getDailyLimit() {
    return dailyLimit;
  }

  public void setDailyLimit(int dailyLimit) {
    this.dailyLimit = dailyLimit;
  }

  public int getMaxInFlight() {
    return maxInFlight;
  }

  public void setMaxInFlight(int maxInFlight) {
    this.maxInFlight = maxInFlight;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
