package com.example.keywordextractor.clients;

import com.example.keywordextractor.config.AnthropicProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AnthropicClient {
  private final WebClient webClient;
  private final AnthropicProperties props;

  public AnthropicClient(WebClient webClient, AnthropicProperties props) {
    this.webClient = webClient;
    this.props = props;
  }

  public Mono<String> generateJsonOnly(String systemPrompt, String userPrompt) {
    if (props.apiKey() == null || props.apiKey().isBlank()) {
      return Mono.error(
          new IllegalStateException(
              "Missing ANTHROPIC_API_KEY (set anthropic.apiKey or env var)."));
    }

    MessagesRequest req =
        new MessagesRequest(
            props.model(),
            props.maxTokens(),
            systemPrompt,
            List.of(new Message("user", List.of(new ContentBlock("text", userPrompt)))));

    return webClient
        .post()
        .uri(props.baseUrl() + "/v1/messages")
        .header("x-api-key", props.apiKey())
        .header("anthropic-version", "2023-06-01")
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(req)
        .retrieve()
        .bodyToMono(MessagesResponse.class)
        .map(MessagesResponse::firstText)
        .switchIfEmpty(Mono.error(new IllegalStateException("Empty response from Anthropic")));
  }

  public record MessagesRequest(
      String model,
      @JsonProperty("max_tokens") int maxTokens,
      String system,
      List<Message> messages) {}

  public record Message(String role, List<ContentBlock> content) {}

  public record ContentBlock(String type, String text) {}

  public record MessagesResponse(List<ContentBlock> content) {
    public String firstText() {
      if (content == null || content.isEmpty() || content.get(0) == null) {
        return "";
      }
      return content.get(0).text();
    }
  }
}
