package com.example.keywordextractor.service;

import com.example.keywordextractor.config.AppProperties;
import com.example.keywordextractor.domain.KeywordExtractionSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {
  private final Cache<String, KeywordExtractionSession> cache;

  public SessionStore(AppProperties props) {
    Duration ttl = props.session().ttl();
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(10_000)
            .build();
  }

  public void put(KeywordExtractionSession session) {
    cache.put(session.sessionId(), session);
  }

  public Optional<KeywordExtractionSession> get(String sessionId) {
    return Optional.ofNullable(cache.getIfPresent(sessionId));
  }

  public KeywordExtractionSession getRequired(String sessionId) {
    KeywordExtractionSession s = cache.getIfPresent(sessionId);
    if (s == null) {
      throw new SessionNotFoundException(sessionId);
    }
    return s;
  }
}
