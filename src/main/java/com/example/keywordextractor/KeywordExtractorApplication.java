package com.example.keywordextractor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KeywordExtractorApplication {
  public static void main(String[] args) {
    SpringApplication.run(KeywordExtractorApplication.class, args);
  }
}
