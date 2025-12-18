package com.example.keywordextractor.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Session session, Enrichment enrichment) {
  public record Session(Duration ttl) {}

  public record Enrichment(int firstPageSize, int concurrency) {}
}
