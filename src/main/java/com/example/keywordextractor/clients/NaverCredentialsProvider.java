package com.example.keywordextractor.clients;

import com.example.keywordextractor.config.NaverSearchAdProperties;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class NaverCredentialsProvider {
  private final List<NaverSearchAdProperties.Credential> credentials;
  private final Semaphore[] semaphores;
  private final AtomicInteger rr;
  private final int maxInFlight;

  public NaverCredentialsProvider(NaverSearchAdProperties props) {
    this.credentials = List.copyOf(props.resolvedCredentials());
    if (credentials.isEmpty()) {
      throw new IllegalStateException(
          "Missing Naver SearchAd credentials. Configure naverSearchAd.credentials[] or legacy apiKey/apiSecret/customerId.");
    }

    this.maxInFlight = props.resolvedPerCredentialMaxInFlight();
    this.semaphores = new Semaphore[credentials.size()];
    for (int i = 0; i < credentials.size(); i++) {
      semaphores[i] = new Semaphore(maxInFlight);
    }

    this.rr = new AtomicInteger(0);
  }

  public Mono<Lease> acquire() {
    // Semaphore acquire is blocking; move to boundedElastic.
    return Mono.fromCallable(
            () -> {
              int start = Math.floorMod(rr.getAndIncrement(), credentials.size());
              // Try all credentials, pick first available.
              for (int i = 0; i < credentials.size(); i++) {
                int idx = (start + i) % credentials.size();
                Semaphore s = semaphores[idx];
                if (s.tryAcquire()) {
                  return new Lease(credentials.get(idx), s);
                }
              }

              // If none available, block briefly on round-robin credential.
              int idx = start;
              semaphores[idx].acquire();
              return new Lease(credentials.get(idx), semaphores[idx]);
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  public record Lease(NaverSearchAdProperties.Credential credential, Semaphore semaphore)
      implements AutoCloseable {
    public Lease {
      Objects.requireNonNull(credential);
      Objects.requireNonNull(semaphore);
    }

    @Override
    public void close() {
      semaphore.release();
    }
  }
}
