package com.example.keywordextractor.clients;

import com.example.keywordextractor.config.NaverSearchAdProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Dynamic credential pool with:
 * - runtime add/update/delete
 * - per-customerId daily quota (default 1000)
 * - per-customerId in-flight cap (semaphore)
 * - selection prefers available quota and capacity
 */
@Component
public class NaverCredentialManager {
  private static final int DEFAULT_DAILY_LIMIT = 1000;

  private final ConcurrentMap<String, State> states;
  private final AtomicReference<List<String>> enabledIds;
  private final AtomicInteger rr;
  private final Clock clock;

  public NaverCredentialManager(NaverSearchAdProperties props) {
    this.states = new ConcurrentHashMap<>();
    this.enabledIds = new AtomicReference<>(List.of());
    this.rr = new AtomicInteger(0);

    ZoneId zone = ZoneId.of(Optional.ofNullable(props.timezone()).orElse("Asia/Seoul"));
    this.clock = Clock.system(zone);

    // bootstrap from config
    for (var c : props.resolvedCredentials()) {
      upsert(
          new UpsertRequest(
              c.customerId(),
              c.apiKey(),
              c.apiSecret(),
              true,
              DEFAULT_DAILY_LIMIT,
              props.resolvedPerCredentialMaxInFlight()));
    }

    if (states.isEmpty()) {
      throw new IllegalStateException(
          "Missing Naver SearchAd credentials. Configure naverSearchAd.credentials[] or legacy apiKey/apiSecret/customerId.");
    }
    rebuildEnabledIds();
  }

  public Mono<Lease> acquire() {
    return Mono.fromCallable(this::acquireBlocking).subscribeOn(Schedulers.boundedElastic());
  }

  private Lease acquireBlocking() {
    List<String> ids = enabledIds.get();
    if (ids.isEmpty()) {
      throw new NoNaverCredentialAvailableException("No enabled customerId available");
    }

    LocalDate today = LocalDate.now(clock);
    int start = Math.floorMod(rr.getAndIncrement(), ids.size());

    // Fast path: round-robin scan
    for (int i = 0; i < ids.size(); i++) {
      String id = ids.get((start + i) % ids.size());
      State s = states.get(id);
      if (s == null) continue;
      if (s.tryAcquire(today)) {
        return new Lease(s);
      }
    }

    // If RR fails (capacity/quota), pick best remaining (least used, still has quota)
    List<State> candidates = new ArrayList<>();
    for (String id : ids) {
      State s = states.get(id);
      if (s != null) {
        s.resetIfNeeded(today);
        if (s.hasQuota()) {
          candidates.add(s);
        }
      }
    }

    candidates.sort(Comparator.comparingInt(State::usedToday));
    for (State s : candidates) {
      if (s.tryAcquire(today)) {
        return new Lease(s);
      }
    }

    throw new NoNaverCredentialAvailableException(
        "All Naver customerIds are exhausted (quota/capacity) for today");
  }

  public List<CredentialView> list() {
    LocalDate today = LocalDate.now(clock);
    return states.values().stream()
        .map(s -> s.view(today))
        .sorted(Comparator.comparing(CredentialView::customerId))
        .toList();
  }

  public void upsert(UpsertRequest req) {
    Objects.requireNonNull(req);
    if (isBlank(req.customerId()) || isBlank(req.apiKey()) || isBlank(req.apiSecret())) {
      throw new IllegalArgumentException("customerId/apiKey/apiSecret are required");
    }

    int dailyLimit = req.dailyLimit() == null || req.dailyLimit() < 1 ? DEFAULT_DAILY_LIMIT : req.dailyLimit();
    int maxInFlight = req.maxInFlight() == null || req.maxInFlight() < 1 ? 8 : req.maxInFlight();
    boolean enabled = req.enabled() == null || req.enabled();

    states.compute(
        req.customerId(),
        (id, existing) -> {
          if (existing == null) {
            return new State(id, req.apiKey(), req.apiSecret(), enabled, dailyLimit, maxInFlight, clock);
          }
          existing.update(req.apiKey(), req.apiSecret(), enabled, dailyLimit, maxInFlight);
          return existing;
        });

    rebuildEnabledIds();
  }

  public void delete(String customerId) {
    if (isBlank(customerId)) {
      throw new IllegalArgumentException("customerId is required");
    }
    states.remove(customerId);
    rebuildEnabledIds();
  }

  public void setEnabled(String customerId, boolean enabled) {
    State s = states.get(customerId);
    if (s == null) {
      throw new IllegalArgumentException("Unknown customerId: " + customerId);
    }
    s.setEnabled(enabled);
    rebuildEnabledIds();
  }

  private void rebuildEnabledIds() {
    List<String> ids =
        states.values().stream().filter(State::enabled).map(State::customerId).sorted().toList();
    enabledIds.set(ids);
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  public record UpsertRequest(
      String customerId,
      String apiKey,
      String apiSecret,
      Boolean enabled,
      Integer dailyLimit,
      Integer maxInFlight) {}

  public record CredentialView(
      String customerId,
      boolean enabled,
      int dailyLimit,
      int usedToday,
      int remainingToday,
      int maxInFlight,
      int inFlight,
      LocalDate date) {}

  public static final class Lease implements AutoCloseable {
    private final State state;
    private boolean closed;

    private Lease(State state) {
      this.state = state;
    }

    public String customerId() {
      return state.customerId();
    }

    public String apiKey() {
      return state.apiKey();
    }

    public String apiSecret() {
      return state.apiSecret();
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      state.release();
    }
  }

  private static final class State {
    private final String customerId;
    private volatile String apiKey;
    private volatile String apiSecret;
    private volatile boolean enabled;
    private volatile int dailyLimit;

    private final AtomicInteger usedToday;
    private final AtomicInteger inFlight;
    private final AtomicReference<LocalDate> day;
    private volatile Semaphore semaphore;
    private volatile int maxInFlight;

    private final Clock clock;

    State(
        String customerId,
        String apiKey,
        String apiSecret,
        boolean enabled,
        int dailyLimit,
        int maxInFlight,
        Clock clock) {
      this.customerId = customerId;
      this.apiKey = apiKey;
      this.apiSecret = apiSecret;
      this.enabled = enabled;
      this.dailyLimit = dailyLimit;
      this.maxInFlight = maxInFlight;
      this.semaphore = new Semaphore(maxInFlight);
      this.usedToday = new AtomicInteger(0);
      this.inFlight = new AtomicInteger(0);
      this.day = new AtomicReference<>(LocalDate.now(clock));
      this.clock = clock;
    }

    String customerId() {
      return customerId;
    }

    String apiKey() {
      return apiKey;
    }

    String apiSecret() {
      return apiSecret;
    }

    boolean enabled() {
      return enabled;
    }

    int usedToday() {
      return usedToday.get();
    }

    void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    void update(String apiKey, String apiSecret, boolean enabled, int dailyLimit, int maxInFlight) {
      this.apiKey = apiKey;
      this.apiSecret = apiSecret;
      this.enabled = enabled;
      this.dailyLimit = dailyLimit;
      if (maxInFlight != this.maxInFlight) {
        this.maxInFlight = maxInFlight;
        this.semaphore = new Semaphore(maxInFlight);
      }
    }

    void resetIfNeeded(LocalDate today) {
      LocalDate prev = day.get();
      if (!prev.equals(today) && day.compareAndSet(prev, today)) {
        usedToday.set(0);
      }
    }

    boolean hasQuota() {
      LocalDate today = LocalDate.now(clock);
      resetIfNeeded(today);
      return usedToday.get() < dailyLimit;
    }

    boolean tryAcquire(LocalDate today) {
      if (!enabled) {
        return false;
      }
      resetIfNeeded(today);

      // capacity first
      if (!semaphore.tryAcquire()) {
        return false;
      }

      // then quota (CAS increment)
      while (true) {
        int u = usedToday.get();
        if (u >= dailyLimit) {
          semaphore.release();
          return false;
        }
        if (usedToday.compareAndSet(u, u + 1)) {
          inFlight.incrementAndGet();
          return true;
        }
      }
    }

    void release() {
      inFlight.decrementAndGet();
      semaphore.release();
    }

    CredentialView view(LocalDate today) {
      resetIfNeeded(today);
      int used = usedToday.get();
      int remain = Math.max(0, dailyLimit - used);
      return new CredentialView(customerId, enabled, dailyLimit, used, remain, maxInFlight, inFlight.get(), today);
    }
  }
}
