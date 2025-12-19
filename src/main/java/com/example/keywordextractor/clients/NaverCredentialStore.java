package com.example.keywordextractor.clients;

import com.example.keywordextractor.config.NaverSearchAdProperties;
import com.example.keywordextractor.persistence.NaverCredentialEntity;
import com.example.keywordextractor.persistence.NaverCredentialRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NaverCredentialStore {
  private final NaverCredentialRepository repo;

  public NaverCredentialStore(NaverCredentialRepository repo, NaverSearchAdProperties props) {
    this.repo = repo;

    // Seed from config if DB is empty.
    // Keeps backward compatibility while enabling runtime CRUD.
    if (repo.count() == 0) {
      List<NaverSearchAdProperties.Credential> seed = props.resolvedCredentials();
      if (!seed.isEmpty()) {
        Instant now = Instant.now();
        for (var c : seed) {
          repo.save(
              new NaverCredentialEntity(
                  c.customerId(),
                  c.apiKey(),
                  c.apiSecret(),
                  true,
                  1000,
                  props.resolvedPerCredentialMaxInFlight(),
                  now,
                  now));
        }
      }
    }
  }

  public List<NaverCredentialEntity> listAll() {
    return repo.findAllCredentials();
  }

  public List<NaverCredentialEntity> listEnabled() {
    return repo.findEnabled();
  }

  public Optional<NaverCredentialEntity> find(String customerId) {
    return repo.findById(customerId);
  }

  @Transactional
  public void upsert(
      String customerId,
      String apiKey,
      String apiSecret,
      boolean enabled,
      int dailyLimit,
      int maxInFlight) {
    Instant now = Instant.now();
    repo.findById(customerId)
        .ifPresentOrElse(
            existing -> {
              existing.setApiKey(apiKey);
              existing.setApiSecret(apiSecret);
              existing.setEnabled(enabled);
              existing.setDailyLimit(dailyLimit);
              existing.setMaxInFlight(maxInFlight);
              existing.setUpdatedAt(now);
              repo.save(existing);
            },
            () ->
                repo.save(
                    new NaverCredentialEntity(
                        customerId, apiKey, apiSecret, enabled, dailyLimit, maxInFlight, now, now)));
  }

  @Transactional
  public void delete(String customerId) {
    repo.deleteById(customerId);
  }

  @Transactional
  public void setEnabled(String customerId, boolean enabled) {
    NaverCredentialEntity c =
        repo.findById(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown customerId: " + customerId));
    c.setEnabled(enabled);
    c.setUpdatedAt(Instant.now());
    repo.save(c);
  }
}
