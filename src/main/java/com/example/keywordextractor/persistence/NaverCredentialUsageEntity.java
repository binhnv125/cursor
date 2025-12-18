package com.example.keywordextractor.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "naver_credential_usage")
@IdClass(NaverCredentialUsageId.class)
public class NaverCredentialUsageEntity {

  @Id
  @Column(name = "customer_id", nullable = false)
  private String customerId;

  @Id
  @Column(name = "usage_date", nullable = false)
  private LocalDate usageDate;

  @Column(name = "used_count", nullable = false)
  private int usedCount;

  protected NaverCredentialUsageEntity() {}

  public NaverCredentialUsageEntity(String customerId, LocalDate usageDate, int usedCount) {
    this.customerId = customerId;
    this.usageDate = usageDate;
    this.usedCount = usedCount;
  }

  public String getCustomerId() {
    return customerId;
  }

  public LocalDate getUsageDate() {
    return usageDate;
  }

  public int getUsedCount() {
    return usedCount;
  }
}
