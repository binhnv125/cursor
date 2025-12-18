package com.example.keywordextractor.api;

import com.example.keywordextractor.service.SessionNotFoundException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  public record ErrorResponse(String error, String message, Instant timestamp) {}

  @ExceptionHandler(SessionNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("BAD_REQUEST", e.getMessage(), Instant.now()));
  }
}
