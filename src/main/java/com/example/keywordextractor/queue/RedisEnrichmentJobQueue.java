package com.example.keywordextractor.queue;

import com.example.keywordextractor.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisEnrichmentJobQueue {
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final AppProperties props;

  public RedisEnrichmentJobQueue(StringRedisTemplate redis, ObjectMapper objectMapper, AppProperties props) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.props = props;
  }

  public void enqueue(String sessionId, List<String> keywords) {
    try {
      Map<String, String> body = new HashMap<>();
      body.put("sessionId", sessionId);
      body.put("keywords", objectMapper.writeValueAsString(keywords));

      MapRecord<String, String, String> record =
          StreamRecords.mapBacked(body).withStreamKey(props.worker().redisStream());

      redis.opsForStream().add(record);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to enqueue enrichment job", e);
    }
  }
}
