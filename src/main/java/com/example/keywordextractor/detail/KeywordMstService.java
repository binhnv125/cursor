package com.example.keywordextractor.detail;

import com.example.keywordextractor.persistence.keyword.KeywordMstRepository;
import org.springframework.stereotype.Service;

@Service
public class KeywordMstService {
  private final KeywordMstRepository repo;

  public KeywordMstService(KeywordMstRepository repo) {
    this.repo = repo;
  }

  public void upsert(String keyword, String categoryId) {
    repo.upsert(keyword, categoryId);
  }
}
