package com.example.keywordextractor.service;

import com.example.keywordextractor.clients.AnthropicClient;
import com.example.keywordextractor.config.AppProperties;
import com.example.keywordextractor.domain.KeywordExtractionAllResponse;
import com.example.keywordextractor.domain.KeywordExtractionCheckResponse;
import com.example.keywordextractor.domain.KeywordExtractionRequest;
import com.example.keywordextractor.domain.KeywordExtractionResponse;
import com.example.keywordextractor.domain.KeywordExtractionSession;
import com.example.keywordextractor.domain.KeywordResult;
import com.example.keywordextractor.domain.KeywordType;
import com.example.keywordextractor.domain.SessionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class KeywordExtractionService {
  private final SessionStore sessionStore;
  private final UrlContentFetcher urlContentFetcher;
  private final PromptBuilder promptBuilder;
  private final AnthropicClient anthropicClient;
  private final KeywordJsonParser keywordJsonParser;
  private final NaverEnrichmentService naverEnrichmentService;
  private final KeywordBackgroundProcessor backgroundProcessor;
  private final AppProperties appProperties;

  public KeywordExtractionService(
      SessionStore sessionStore,
      UrlContentFetcher urlContentFetcher,
      PromptBuilder promptBuilder,
      AnthropicClient anthropicClient,
      KeywordJsonParser keywordJsonParser,
      NaverEnrichmentService naverEnrichmentService,
      KeywordBackgroundProcessor backgroundProcessor,
      AppProperties appProperties) {
    this.sessionStore = sessionStore;
    this.urlContentFetcher = urlContentFetcher;
    this.promptBuilder = promptBuilder;
    this.anthropicClient = anthropicClient;
    this.keywordJsonParser = keywordJsonParser;
    this.naverEnrichmentService = naverEnrichmentService;
    this.backgroundProcessor = backgroundProcessor;
    this.appProperties = appProperties;
  }

  public Mono<KeywordExtractionResponse> startExtraction(KeywordExtractionRequest req) {
    String sessionId = UUID.randomUUID().toString();
    int firstPageSize = appProperties.enrichment().firstPageSize();
    int concurrency = Math.max(1, appProperties.enrichment().concurrency());

    return urlContentFetcher
        .fetchText(req.url())
        .flatMap(
            urlText ->
                anthropicClient.generateJsonOnly(
                    promptBuilder.systemPrompt(), promptBuilder.userPrompt(req, urlText)))
        .map(keywordJsonParser::parseKeywords)
        .flatMap(
            keywords -> {
              if (keywords.isEmpty()) {
                return Mono.error(
                    new IllegalArgumentException(
                        "LLM returned empty/invalid keywords JSON. Expect {\"keywords\":[...]}"));
              }

              List<KeywordResult> pending = new ArrayList<>(keywords.size());
              for (String k : keywords) {
                KeywordType type = KeywordClassifier.classify(k);
                pending.add(KeywordResult.pending(k, type));
              }

              KeywordExtractionSession session =
                  new KeywordExtractionSession(sessionId, Instant.now(), req, keywords);
              session.initPending(pending);
              sessionStore.put(session);

              List<String> first = keywords.subList(0, Math.min(firstPageSize, keywords.size()));
              List<String> remaining =
                  keywords.size() > first.size() ? keywords.subList(first.size(), keywords.size()) : List.of();

              return naverEnrichmentService
                  .enrichAndUpdate(session, first, concurrency)
                  .doOnSuccess(
                      ignored ->
                          backgroundProcessor
                              .processRemaining(session, remaining, concurrency)
                              .subscribe())
                  .map(
                      ignored ->
                          new KeywordExtractionResponse(
                              session.sessionId(),
                              session.total(),
                              session.doneCount(),
                              session.failedCount(),
                              session.status(),
                              first.stream().map(k -> session.resultsByKeyword().get(k)).toList()));
            });
  }

  public Mono<KeywordExtractionCheckResponse> check(String sessionId) {
    KeywordExtractionSession session = sessionStore.getRequired(sessionId);
    boolean all = session.status() == SessionStatus.COMPLETED;
    return Mono.just(
        new KeywordExtractionCheckResponse(
            session.sessionId(),
            session.total(),
            session.doneCount(),
            session.failedCount(),
            session.status(),
            all));
  }

  public Mono<KeywordExtractionAllResponse> all(String sessionId) {
    KeywordExtractionSession session = sessionStore.getRequired(sessionId);
    List<KeywordResult> ordered =
        session.orderedKeywords().stream().map(k -> session.resultsByKeyword().get(k)).toList();
    return Mono.just(
        new KeywordExtractionAllResponse(
            session.sessionId(),
            session.total(),
            session.doneCount(),
            session.failedCount(),
            session.status(),
            ordered));
  }
}
