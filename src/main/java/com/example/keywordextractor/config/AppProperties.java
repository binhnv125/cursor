package com.example.keywordextractor.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Session session, Enrichment enrichment, Worker worker, RateLimit rateLimit) {
  public record Session(Duration ttl) {}

  public record Enrichment(int firstPageSize, int concurrency) {}

  public record Worker(
      boolean enabled, String redisStream, String consumerGroup, String consumerName) {}

  public record RateLimit(Naver naver) {
    public record Naver(int globalRps, int perCustomerRps, long acquireTimeoutMs) {}
  }
}
