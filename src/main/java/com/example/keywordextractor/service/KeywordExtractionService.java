package com.example.keywordextractor.service;

import com.example.keywordextractor.clients.AnthropicClient;
import com.example.keywordextractor.config.AppProperties;
import com.example.keywordextractor.domain.KeywordExtractionAllResponse;
import com.example.keywordextractor.domain.KeywordExtractionCheckResponse;
import com.example.keywordextractor.domain.KeywordExtractionRequest;
import com.example.keywordextractor.domain.KeywordExtractionResponse;
import com.example.keywordextractor.domain.KeywordResult;
import com.example.keywordextractor.domain.KeywordType;
import com.example.keywordextractor.domain.SessionStatus;
import com.example.keywordextractor.queue.RedisEnrichmentJobQueue;
import com.example.keywordextractor.session.RedisSessionRepository;
import com.example.keywordextractor.session.RedisSessionRepository.SessionMeta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class KeywordExtractionService {
  private final UrlContentFetcher urlContentFetcher;
  private final PromptBuilder promptBuilder;
  private final AnthropicClient anthropicClient;
  private final KeywordJsonParser keywordJsonParser;
  private final NaverEnrichmentService naverEnrichmentService;
  private final RedisEnrichmentJobQueue jobQueue;
  private final RedisSessionRepository sessions;
  private final AppProperties appProperties;

  public KeywordExtractionService(
      UrlContentFetcher urlContentFetcher,
      PromptBuilder promptBuilder,
      AnthropicClient anthropicClient,
      KeywordJsonParser keywordJsonParser,
      NaverEnrichmentService naverEnrichmentService,
      RedisEnrichmentJobQueue jobQueue,
      RedisSessionRepository sessions,
      AppProperties appProperties) {
    this.urlContentFetcher = urlContentFetcher;
    this.promptBuilder = promptBuilder;
    this.anthropicClient = anthropicClient;
    this.keywordJsonParser = keywordJsonParser;
    this.naverEnrichmentService = naverEnrichmentService;
    this.jobQueue = jobQueue;
    this.sessions = sessions;
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

              List<String> first = keywords.subList(0, Math.min(firstPageSize, keywords.size()));
              List<String> remaining =
                  keywords.size() > first.size() ? keywords.subList(first.size(), keywords.size()) : List.of();

              return sessions
                  .createSession(sessionId, req, keywords, pending)
                  .then(naverEnrichmentService.enrichAndPersist(sessionId, first, concurrency))
                  .doOnSuccess(
                      ignored -> {
                        // enqueue remaining keywords for worker processing
                        for (List<String> chunk : chunk(remaining, 30)) {
                          jobQueue.enqueue(sessionId, chunk);
                        }
                      })
                  .then(sessions.getMetaRequired(sessionId))
                  .flatMap(
                      meta ->
                          sessions
                              .getResults(sessionId, first)
                              .map(
                                  firstResults ->
                                      new KeywordExtractionResponse(
                                          sessionId,
                                          meta.totalKeywords(),
                                          meta.doneCount(),
                                          meta.failedCount(),
                                          meta.status(),
                                          firstResults)));
            });
  }

  public Mono<KeywordExtractionCheckResponse> check(String sessionId) {
    return sessions
        .getMetaRequired(sessionId)
        .map(
            meta ->
                new KeywordExtractionCheckResponse(
                    meta.sessionId(),
                    meta.totalKeywords(),
                    meta.doneCount(),
                    meta.failedCount(),
                    meta.status(),
                    meta.status() == SessionStatus.COMPLETED));
  }

  public Mono<KeywordExtractionAllResponse> all(String sessionId) {
    return sessions
        .getMetaRequired(sessionId)
        .flatMap(
            meta ->
                sessions
                    .getOrderedKeywords(sessionId)
                    .flatMap(
                        orderedKeywords ->
                            sessions
                                .getResults(sessionId, orderedKeywords)
                                .map(
                                    ordered ->
                                        new KeywordExtractionAllResponse(
                                            meta.sessionId(),
                                            meta.totalKeywords(),
                                            meta.doneCount(),
                                            meta.failedCount(),
                                            meta.status(),
                                            ordered))));
  }

  private List<List<String>> chunk(List<String> all, int size) {
    if (all == null || all.isEmpty()) {
      return List.of();
    }
    List<List<String>> out = new ArrayList<>();
    for (int i = 0; i < all.size(); i += size) {
      out.add(all.subList(i, Math.min(all.size(), i + size)));
    }
    return out;
  }
}
