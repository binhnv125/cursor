package com.example.keywordextractor.ratelimit;

import com.example.keywordextractor.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class NaverRateLimiter {
  private final StringRedisTemplate redis;
  private final AppProperties.RateLimit.Naver cfg;
  private final DefaultRedisScript<Long> script;

  public NaverRateLimiter(StringRedisTemplate redis, AppProperties props) {
    this.redis = redis;
    this.cfg = props.rateLimit().naver();
    this.script = new DefaultRedisScript<>();
    this.script.setResultType(Long.class);

    // KEYS[1]=globalKey, KEYS[2]=customerKey
    // ARGV[1]=globalLimit, ARGV[2]=customerLimit, ARGV[3]=ttlSeconds
    this.script.setScriptText(
        "local g=redis.call('INCR',KEYS[1]) "
            + "if g==1 then redis.call('EXPIRE',KEYS[1],ARGV[3]) end "
            + "local c=redis.call('INCR',KEYS[2]) "
            + "if c==1 then redis.call('EXPIRE',KEYS[2],ARGV[3]) end "
            + "local gl=tonumber(ARGV[1]) "
            + "local cl=tonumber(ARGV[2]) "
            + "if g<=gl and c<=cl then return 1 end "
            + "if g>gl then redis.call('DECR',KEYS[1]) end "
            + "if c>cl then redis.call('DECR',KEYS[2]) end "
            + "return 0");
  }

  public Mono<Void> acquire(String customerId) {
    long deadlineMs = System.currentTimeMillis() + Math.max(0, cfg.acquireTimeoutMs());
    return attempt(customerId, deadlineMs);
  }

  private Mono<Void> attempt(String customerId, long deadlineMs) {
    return Mono.fromCallable(
            () -> {
              long epochSecond = Instant.now().getEpochSecond();
              String gKey = "rl:naver:global:" + epochSecond;
              String cKey = "rl:naver:customer:" + customerId + ":" + epochSecond;

              Long allowed =
                  redis.execute(
                      script,
                      List.of(gKey, cKey),
                      String.valueOf(cfg.globalRps()),
                      String.valueOf(cfg.perCustomerRps()),
                      "2");
              return allowed != null && allowed == 1L;
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            ok -> {
              if (ok) {
                return Mono.empty();
              }
              if (System.currentTimeMillis() >= deadlineMs) {
                return Mono.error(
                    new RateLimitTimeoutException(
                        "Rate limit exceeded (naver global/per-customer); timeout waiting permit"));
              }
              return Mono.delay(Duration.ofMillis(50)).then(attempt(customerId, deadlineMs));
            });
  }
}
