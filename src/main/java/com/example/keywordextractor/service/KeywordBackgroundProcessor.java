package com.example.keywordextractor.service;

import com.example.keywordextractor.domain.KeywordExtractionSession;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KeywordBackgroundProcessor {
  private final NaverEnrichmentService enrichmentService;

  public KeywordBackgroundProcessor(NaverEnrichmentService enrichmentService) {
    this.enrichmentService = enrichmentService;
  }

  public Mono<Void> processRemaining(KeywordExtractionSession session, List<String> remaining, int concurrency) {
    if (remaining == null || remaining.isEmpty()) {
      return Mono.empty();
    }

    // Run in background; non-blocking I/O still, but we don't want to tie request lifecycle.
    return enrichmentService
        .enrichAndUpdate(session, remaining, concurrency)
        .then()
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> Mono.empty());
  }
}
