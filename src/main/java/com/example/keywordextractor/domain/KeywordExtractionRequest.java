package com.example.keywordextractor.domain;

import jakarta.validation.constraints.NotBlank;

public record KeywordExtractionRequest(
    String url,
    @NotBlank String keyword,
    String category,
    String target) {}
