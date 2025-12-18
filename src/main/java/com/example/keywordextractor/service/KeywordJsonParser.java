package com.example.keywordextractor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KeywordJsonParser {
  private final ObjectMapper objectMapper;

  public KeywordJsonParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<String> parseKeywords(String llmText) {
    if (llmText == null || llmText.isBlank()) {
      return List.of();
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(llmText);
    } catch (Exception e) {
      // Best-effort: no JSON => no keywords.
      return List.of();
    }

    JsonNode arr = null;
    if (root.isArray()) {
      arr = root;
    } else if (root.isObject()) {
      arr = firstArrayField(root, List.of("keywords", "keywordList", "data", "items"));
    }

    if (arr == null || !arr.isArray()) {
      return List.of();
    }

    Set<String> dedup = new LinkedHashSet<>();
    for (JsonNode n : arr) {
      String kw = null;
      if (n == null || n.isNull()) {
        continue;
      }
      if (n.isTextual()) {
        kw = n.asText();
      } else if (n.isObject()) {
        JsonNode k = firstTextField(n, List.of("keyword", "relKeyword", "text", "value"));
        if (k != null) {
          kw = k.asText();
        }
      }

      String cleaned = KeywordSanitizer.sanitize(kw);
      if (cleaned != null && !cleaned.isBlank()) {
        dedup.add(cleaned);
      }
    }

    return new ArrayList<>(dedup);
  }

  private JsonNode firstArrayField(JsonNode obj, List<String> candidates) {
    for (String f : candidates) {
      JsonNode n = obj.get(f);
      if (n != null && n.isArray()) {
        return n;
      }
    }
    // fallback: find any array
    obj.fieldNames();
    var it = obj.fields();
    while (it.hasNext()) {
      var e = it.next();
      if (e.getValue() != null && e.getValue().isArray()) {
        return e.getValue();
      }
    }
    return null;
  }

  private JsonNode firstTextField(JsonNode obj, List<String> candidates) {
    for (String f : candidates) {
      JsonNode n = obj.get(f);
      if (n != null && n.isTextual()) {
        return n;
      }
    }
    return null;
  }
}
