package com.example.keywordextractor.persistence;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class NaverCredentialUsageId implements Serializable {
  private String customerId;
  private LocalDate usageDate;

  public NaverCredentialUsageId() {}

  public NaverCredentialUsageId(String customerId, LocalDate usageDate) {
    this.customerId = customerId;
    this.usageDate = usageDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NaverCredentialUsageId that)) return false;
    return Objects.equals(customerId, that.customerId) && Objects.equals(usageDate, that.usageDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId, usageDate);
  }
}
