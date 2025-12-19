package com.example.keywordextractor.detail;

import jakarta.validation.constraints.NotBlank;

public record KeywordDetailRequest(@NotBlank String keyword, @NotBlank String categoryId) {}
