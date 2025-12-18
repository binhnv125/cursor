package com.example.keywordextractor.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class KeywordExtractionSession {
  private final String sessionId;
  private final Instant createdAt;
  private final KeywordExtractionRequest request;

  private final List<String> orderedKeywords;
  private final Map<String, KeywordResult> resultsByKeyword;
  private final AtomicInteger doneCount;
  private final AtomicInteger failedCount;

  private volatile SessionStatus status;

  public KeywordExtractionSession(
      String sessionId, Instant createdAt, KeywordExtractionRequest request, List<String> orderedKeywords) {
    this.sessionId = sessionId;
    this.createdAt = createdAt;
    this.request = request;
    this.orderedKeywords = List.copyOf(orderedKeywords);
    this.resultsByKeyword = new ConcurrentHashMap<>();
    this.doneCount = new AtomicInteger(0);
    this.failedCount = new AtomicInteger(0);
    this.status = SessionStatus.IN_PROGRESS;
  }

  public String sessionId() {
    return sessionId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public KeywordExtractionRequest request() {
    return request;
  }

  public List<String> orderedKeywords() {
    return orderedKeywords;
  }

  public Map<String, KeywordResult> resultsByKeyword() {
    return resultsByKeyword;
  }

  public int total() {
    return orderedKeywords.size();
  }

  public int doneCount() {
    return doneCount.get();
  }

  public int failedCount() {
    return failedCount.get();
  }

  public SessionStatus status() {
    return status;
  }

  public void markDone(KeywordResult result) {
    KeywordResult prev = resultsByKeyword.put(result.keyword(), result);
    if (prev == null || prev.status() != KeywordResultStatus.DONE) {
      doneCount.incrementAndGet();
    }
    maybeComplete();
  }

  public void markFailed(String keyword, KeywordType type, String error) {
    KeywordResult prev = resultsByKeyword.put(keyword, KeywordResult.failed(keyword, type, error));
    if (prev == null || prev.status() != KeywordResultStatus.FAILED) {
      failedCount.incrementAndGet();
    }
    maybeComplete();
  }

  public void initPending(List<KeywordResult> pending) {
    for (KeywordResult r : pending) {
      resultsByKeyword.put(r.keyword(), r);
    }
  }

  private void maybeComplete() {
    if (doneCount() + failedCount() >= total()) {
      status = SessionStatus.COMPLETED;
    }
  }
}
