package com.example.keywordextractor.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeywordBid(Integer pc, Integer mobile) {}
