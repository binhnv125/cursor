package com.example.keywordextractor.clients;

import com.example.keywordextractor.persistence.NaverCredentialUsageRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NaverQuotaService {
  private final NaverCredentialUsageRepository usageRepo;

  public NaverQuotaService(NaverCredentialUsageRepository usageRepo) {
    this.usageRepo = usageRepo;
  }

  @Transactional
  public boolean tryConsume(String customerId, LocalDate date, int dailyLimit) {
    usageRepo.ensureRow(customerId, date);
    return usageRepo.tryConsume(customerId, date, dailyLimit) == 1;
  }
}
