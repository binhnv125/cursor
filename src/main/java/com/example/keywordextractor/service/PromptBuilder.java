package com.example.keywordextractor.service;

import com.example.keywordextractor.domain.KeywordExtractionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
  private final ObjectMapper objectMapper;

  public PromptBuilder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String systemPrompt() {
    return String.join(
        "\n",
        "당신은 네이버 검색광고 전문가입니다.",
        "입력 정보를 분석해 광고 성과를 높일 수 있는 한국어 키워드만 제안하세요.",
        "URL 정보가 있다면 해당 URL을 읽고 핵심 키워드를 찾습니다.",
        "카테고리별 키워드는 중복 없이 제공하고, 검색 의도와 연관성을 최우선으로 고려합니다.",
        "시즌·계절성 키워드는 특정 시기, 계절, 행사와 연관된 표현을 포함하세요.",
        "형용사 키워드는 매력도를 높이는 형용사(예: 프리미엄, 촉촉한 등)를 반드시 포함하세요.",
        "지역 키워드는 명확한 지명(도시, 지역명 등)을 포함하세요.",
        "브랜드 키워드는 주요 브랜드명 또는 경쟁 브랜드를 포함하세요.",
        "롱테일 키워드는 검색량은 적어도 구매 의도가 뚜렷한 3~6단어 조합으로 제안하세요.",
        "모든 키워드는 15자 이내로 유지하고, 불필요한 특수문자나 따옴표는 제거하세요.",
        "오직 JSON만 출력하고 기타 설명은 포함하지 마세요.",
        "``` 등 코드 블록 구문을 절대로 사용하지 마세요.",
        "반드시 다음 스키마로만 응답하세요: {\"keywords\":[\"키워드1\",\"키워드2\",...]}");
  }

  public String userPrompt(KeywordExtractionRequest req, String urlContent) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("url", req.url());
    input.put("keyword", req.keyword());
    input.put("category", req.category());
    input.put("target", req.target());
    input.put("urlContent", urlContent == null || urlContent.isBlank() ? null : urlContent);

    try {
      return "입력 정보:\n" + objectMapper.writeValueAsString(input);
    } catch (JsonProcessingException e) {
      return "입력 정보: url=" + req.url() + ", keyword=" + req.keyword();
    }
  }
}
