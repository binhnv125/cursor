package com.example.keywordextractor.admin;

import com.example.keywordextractor.clients.NaverCredentialManager;
import com.example.keywordextractor.clients.NaverCredentialManager.CredentialView;
import com.example.keywordextractor.clients.NaverCredentialManager.UpsertRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/naver-credentials")
@Validated
public class NaverCredentialsAdminController {
  private final NaverCredentialManager manager;

  public NaverCredentialsAdminController(NaverCredentialManager manager) {
    this.manager = manager;
  }

  @GetMapping
  public List<CredentialView> list() {
    return manager.list();
  }

  @PostMapping
  public void create(@Valid @RequestBody UpsertBody body) {
    manager.upsert(body.toReq());
  }

  @PutMapping("/{customerId}")
  public void update(@PathVariable @NotBlank String customerId, @Valid @RequestBody UpsertBody body) {
    manager.upsert(body.toReq(customerId));
  }

  @DeleteMapping("/{customerId}")
  public void delete(@PathVariable @NotBlank String customerId) {
    manager.delete(customerId);
  }

  @PostMapping("/{customerId}/enabled")
  public void setEnabled(
      @PathVariable @NotBlank String customerId, @RequestParam boolean enabled) {
    manager.setEnabled(customerId, enabled);
  }

  public record UpsertBody(
      @NotBlank String apiKey,
      @NotBlank String apiSecret,
      String customerId,
      Boolean enabled,
      Integer dailyLimit,
      Integer maxInFlight) {

    UpsertRequest toReq() {
      if (customerId == null || customerId.isBlank()) {
        throw new IllegalArgumentException("customerId is required in body for create");
      }
      return new UpsertRequest(customerId, apiKey, apiSecret, enabled, dailyLimit, maxInFlight);
    }

    UpsertRequest toReq(String forcedCustomerId) {
      return new UpsertRequest(forcedCustomerId, apiKey, apiSecret, enabled, dailyLimit, maxInFlight);
    }
  }
}
