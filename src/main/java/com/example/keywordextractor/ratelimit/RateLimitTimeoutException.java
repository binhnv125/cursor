package com.example.keywordextractor.ratelimit;

public class RateLimitTimeoutException extends RuntimeException {
  public RateLimitTimeoutException(String message) {
    super(message);
  }
}
