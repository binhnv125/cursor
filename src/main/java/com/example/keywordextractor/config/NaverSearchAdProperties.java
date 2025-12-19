package com.example.keywordextractor.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naverSearchAd")
public record NaverSearchAdProperties(
    String baseUrl,
    // Legacy single-credential config (kept for backward compatibility)
    String apiKey,
    String apiSecret,
    String customerId,
    // Preferred: multiple credentials (e.g. 10 customerIds)
    List<Credential> credentials,
    // Limit per credential to avoid 429s; tuned with Naver quota
    Integer perCredentialMaxInFlight,
    // Timezone used for \"daily\" quota reset
    String timezone,
    Duration timeout) {

  public record Credential(String apiKey, String apiSecret, String customerId) {}

  public List<Credential> resolvedCredentials() {
    if (credentials != null && !credentials.isEmpty()) {
      return credentials;
    }
    List<Credential> one = new ArrayList<>();
    if (apiKey != null && apiSecret != null && customerId != null) {
      one.add(new Credential(apiKey, apiSecret, customerId));
    }
    return one;
  }

  public int resolvedPerCredentialMaxInFlight() {
    return perCredentialMaxInFlight == null || perCredentialMaxInFlight < 1
        ? 8
        : perCredentialMaxInFlight;
  }
}
