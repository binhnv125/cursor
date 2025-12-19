package com.example.keywordextractor.queue;

import com.example.keywordextractor.config.AppProperties;
import com.example.keywordextractor.service.NaverEnrichmentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisEnrichmentJobWorker implements SmartLifecycle {
  private final RedisConnectionFactory connectionFactory;
  private final String stream;
  private final String group;
  private final String consumer;
  private final ObjectMapper objectMapper;
  private final NaverEnrichmentService enrichmentService;
  private final AppProperties appProperties;
  private final StringRedisTemplate redis;

  private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
  private Subscription subscription;
  private volatile boolean running;

  public RedisEnrichmentJobWorker(
      RedisConnectionFactory connectionFactory,
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      NaverEnrichmentService enrichmentService,
      AppProperties props) {
    this.connectionFactory = connectionFactory;
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.enrichmentService = enrichmentService;
    this.appProperties = props;

    this.stream = props.worker().redisStream();
    this.group = props.worker().consumerGroup();
    String baseConsumer = props.worker().consumerName() == null ? "worker" : props.worker().consumerName();
    this.consumer = baseConsumer + "-" + UUID.randomUUID();
  }

  @PostConstruct
  public void ensureGroup() {
    try (RedisConnection conn = connectionFactory.getConnection()) {
      // Create group if not exists; MKSTREAM by creating stream if missing.
      conn.xGroupCreate(
          stream.getBytes(),
          group,
          ReadOffset.from("0-0"),
          true);
    } catch (Exception ignored) {
      // group already exists or redis doesn't support; ignore
    }
  }

  @Override
  public void start() {
    if (running) return;

    StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>>
        options =
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .batchSize(10)
                .build();

    container = StreamMessageListenerContainer.create(connectionFactory, options);

    subscription =
        container.receive(
            Consumer.from(group, consumer),
            StreamOffset.create(stream, ReadOffset.lastConsumed()),
            msg -> handle(msg));

    container.start();
    running = true;
  }

  private void handle(MapRecord<String, String, String> msg) {
    try {
      Map<String, String> v = msg.getValue();
      String sessionId = v.get("sessionId");
      List<String> keywords =
          objectMapper.readValue(v.get("keywords"), new TypeReference<List<String>>() {});

      int concurrency = Math.max(1, appProperties.enrichment().concurrency());
      enrichmentService.enrichAndPersist(sessionId, keywords, concurrency).block(Duration.ofMinutes(5));

      // Ack only after successful processing (at-least-once)
      RecordId id = msg.getId();
      redis.opsForStream().acknowledge(stream, group, id);
    } catch (Exception e) {
      // Do not ack: message stays pending for manual/auto claim.
    }
  }

  @PreDestroy
  public void shutdown() {
    stop();
  }

  @Override
  public void stop() {
    if (!running) return;
    if (subscription != null) {
      subscription.cancel();
    }
    if (container != null) {
      container.stop();
    }
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return 0;
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }
}
