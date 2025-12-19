package com.example.keywordextractor.api;

import com.example.keywordextractor.domain.KeywordExtractionAllResponse;
import com.example.keywordextractor.domain.KeywordExtractionCheckResponse;
import com.example.keywordextractor.domain.KeywordExtractionRequest;
import com.example.keywordextractor.domain.KeywordExtractionResponse;
import com.example.keywordextractor.service.KeywordExtractionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/keyword-extraction")
@Validated
public class KeywordExtractionController {
  private final KeywordExtractionService service;

  public KeywordExtractionController(KeywordExtractionService service) {
    this.service = service;
  }

  @PostMapping
  public Mono<KeywordExtractionResponse> extract(@Valid @RequestBody KeywordExtractionRequest req) {
    return service.startExtraction(req);
  }

  @GetMapping("/check")
  public Mono<KeywordExtractionCheckResponse> check(@RequestParam @NotBlank String sessionId) {
    return service.check(sessionId);
  }

  @GetMapping("/all")
  public Mono<KeywordExtractionAllResponse> all(@RequestParam @NotBlank String sessionId) {
    return service.all(sessionId);
  }
}
