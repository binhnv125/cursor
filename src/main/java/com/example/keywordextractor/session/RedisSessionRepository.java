package com.example.keywordextractor.session;

import com.example.keywordextractor.config.AppProperties;
import com.example.keywordextractor.domain.KeywordExtractionRequest;
import com.example.keywordextractor.domain.KeywordResult;
import com.example.keywordextractor.domain.SessionStatus;
import com.example.keywordextractor.service.SessionNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class RedisSessionRepository {
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final Duration ttl;

  private final DefaultRedisScript<Long> updateResultScript;

  public RedisSessionRepository(StringRedisTemplate redis, ObjectMapper objectMapper, AppProperties props) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.ttl = props.session().ttl();

    this.updateResultScript = new DefaultRedisScript<>();
    this.updateResultScript.setResultType(Long.class);
    this.updateResultScript.setScriptText(
        "local meta=KEYS[1] "
            + "local results=KEYS[2] "
            + "local statuses=KEYS[3] "
            + "local kw=ARGV[1] "
            + "local newStatus=ARGV[2] "
            + "local json=ARGV[3] "
            + "local prev=redis.call('HGET',statuses,kw) "
            + "if prev == newStatus then return 0 end "
            + "if prev == 'DONE' or prev == 'FAILED' then return 0 end "
            + "redis.call('HSET',results,kw,json) "
            + "redis.call('HSET',statuses,kw,newStatus) "
            + "if newStatus == 'DONE' then redis.call('HINCRBY',meta,'doneCount',1) else redis.call('HINCRBY',meta,'failedCount',1) end "
            + "local done=tonumber(redis.call('HGET',meta,'doneCount') or '0') "
            + "local failed=tonumber(redis.call('HGET',meta,'failedCount') or '0') "
            + "local total=tonumber(redis.call('HGET',meta,'totalKeywords') or '0') "
            + "if (done + failed) >= total then redis.call('HSET',meta,'status','COMPLETED') end "
            + "return 1");
  }

  public Mono<Void> createSession(
      String sessionId,
      KeywordExtractionRequest request,
      List<String> orderedKeywords,
      List<KeywordResult> pendingResults) {
    return Mono.fromRunnable(
            () -> createSessionBlocking(sessionId, request, orderedKeywords, pendingResults))
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }

  public void createSessionBlocking(
      String sessionId,
      KeywordExtractionRequest request,
      List<String> orderedKeywords,
      List<KeywordResult> pendingResults) {
    String metaKey = metaKey(sessionId);
    String orderKey = orderKey(sessionId);
    String resultsKey = resultsKey(sessionId);
    String statusesKey = statusesKey(sessionId);

    Map<String, String> meta = new HashMap<>();
    meta.put("sessionId", sessionId);
    meta.put("createdAt", Instant.now().toString());
    meta.put("status", SessionStatus.IN_PROGRESS.name());
    meta.put("totalKeywords", String.valueOf(orderedKeywords.size()));
    meta.put("doneCount", "0");
    meta.put("failedCount", "0");
    meta.put("request", writeJson(request));

    redis.opsForHash().putAll(metaKey, meta);

    if (!orderedKeywords.isEmpty()) {
      redis.delete(orderKey);
      redis.opsForList().rightPushAll(orderKey, orderedKeywords);
    }

    if (!pendingResults.isEmpty()) {
      Map<String, String> results = new HashMap<>(pendingResults.size());
      Map<String, String> statuses = new HashMap<>(pendingResults.size());
      for (KeywordResult r : pendingResults) {
        results.put(r.keyword(), writeJson(r));
        statuses.put(r.keyword(), "PENDING");
      }
      redis.opsForHash().putAll(resultsKey, results);
      redis.opsForHash().putAll(statusesKey, statuses);
    }

    expireAll(sessionId);
  }

  public Mono<SessionMeta> getMetaRequired(String sessionId) {
    return Mono.fromCallable(() -> getMetaRequiredBlocking(sessionId)).subscribeOn(Schedulers.boundedElastic());
  }

  public SessionMeta getMetaRequiredBlocking(String sessionId) {
    String metaKey = metaKey(sessionId);
    Map<Object, Object> m = redis.opsForHash().entries(metaKey);
    if (m == null || m.isEmpty()) {
      throw new SessionNotFoundException(sessionId);
    }

    int total = Integer.parseInt(String.valueOf(m.getOrDefault("totalKeywords", "0")));
    int done = Integer.parseInt(String.valueOf(m.getOrDefault("doneCount", "0")));
    int failed = Integer.parseInt(String.valueOf(m.getOrDefault("failedCount", "0")));
    SessionStatus status =
        SessionStatus.valueOf(String.valueOf(m.getOrDefault("status", "IN_PROGRESS")));

    return new SessionMeta(sessionId, total, done, failed, status);
  }

  public Mono<List<String>> getOrderedKeywords(String sessionId) {
    return Mono.fromCallable(() -> getOrderedKeywordsBlocking(sessionId)).subscribeOn(Schedulers.boundedElastic());
  }

  public List<String> getOrderedKeywordsBlocking(String sessionId) {
    List<String> v = redis.opsForList().range(orderKey(sessionId), 0, -1);
    if (v == null) {
      return List.of();
    }
    return v;
  }

  public Mono<List<KeywordResult>> getResults(String sessionId, List<String> keywords) {
    return Mono.fromCallable(() -> getResultsBlocking(sessionId, keywords))
        .subscribeOn(Schedulers.boundedElastic());
  }

  public List<KeywordResult> getResultsBlocking(String sessionId, List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      return List.of();
    }
    List<Object> raw =
        redis.opsForHash().multiGet(resultsKey(sessionId), new ArrayList<>(keywords));
    if (raw == null) {
      return List.of();
    }
    List<KeywordResult> out = new ArrayList<>(keywords.size());
    for (Object o : raw) {
      if (o == null) {
        out.add(null);
        continue;
      }
      out.add(readResult(String.valueOf(o)));
    }
    return out;
  }

  public Mono<Void> markDone(String sessionId, KeywordResult doneResult) {
    return Mono.fromRunnable(() -> markDoneBlocking(sessionId, doneResult))
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }

  public void markDoneBlocking(String sessionId, KeywordResult doneResult) {
    String metaKey = metaKey(sessionId);
    if (!Boolean.TRUE.equals(redis.hasKey(metaKey))) {
      throw new SessionNotFoundException(sessionId);
    }

    redis.execute(
        updateResultScript,
        List.of(metaKey, resultsKey(sessionId), statusesKey(sessionId)),
        doneResult.keyword(),
        "DONE",
        writeJson(doneResult));

    expireAll(sessionId);
  }

  public Mono<Void> markFailed(String sessionId, KeywordResult failedResult) {
    return Mono.fromRunnable(() -> markFailedBlocking(sessionId, failedResult))
        .subscribeOn(Schedulers.boundedElastic())
        .then();
  }

  public void markFailedBlocking(String sessionId, KeywordResult failedResult) {
    String metaKey = metaKey(sessionId);
    if (!Boolean.TRUE.equals(redis.hasKey(metaKey))) {
      throw new SessionNotFoundException(sessionId);
    }

    redis.execute(
        updateResultScript,
        List.of(metaKey, resultsKey(sessionId), statusesKey(sessionId)),
        failedResult.keyword(),
        "FAILED",
        writeJson(failedResult));

    expireAll(sessionId);
  }

  private void expireAll(String sessionId) {
    redis.expire(metaKey(sessionId), ttl);
    redis.expire(orderKey(sessionId), ttl);
    redis.expire(resultsKey(sessionId), ttl);
    redis.expire(statusesKey(sessionId), ttl);
  }

  private String writeJson(Object o) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize JSON", e);
    }
  }

  private KeywordResult readResult(String json) {
    try {
      return objectMapper.readValue(json, KeywordResult.class);
    } catch (Exception e) {
      return null;
    }
  }

  private String metaKey(String sessionId) {
    return "session:" + sessionId + ":meta";
  }

  private String orderKey(String sessionId) {
    return "session:" + sessionId + ":order";
  }

  private String resultsKey(String sessionId) {
    return "session:" + sessionId + ":results";
  }

  private String statusesKey(String sessionId) {
    return "session:" + sessionId + ":statuses";
  }

  public record SessionMeta(
      String sessionId, int totalKeywords, int doneCount, int failedCount, SessionStatus status) {}
}
