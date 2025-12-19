package com.example.keywordextractor.detail;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/keyword-extraction")
public class KeywordDetailController {
  private final KeywordDetailService service;

  public KeywordDetailController(KeywordDetailService service) {
    this.service = service;
  }

  @PostMapping("/detail")
  public Mono<KeywordDetailResponse> detail(@Valid @RequestBody KeywordDetailRequest req) {
    return service.detail(req);
  }
}
