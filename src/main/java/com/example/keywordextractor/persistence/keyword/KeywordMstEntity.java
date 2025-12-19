package com.example.keywordextractor.persistence.keyword;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "keyword_mst")
public class KeywordMstEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "keyword", nullable = false)
  private String keyword;

  @Column(name = "category_id", nullable = false)
  private String categoryId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected KeywordMstEntity() {}

  public KeywordMstEntity(String keyword, String categoryId, Instant createdAt, Instant updatedAt) {
    this.keyword = keyword;
    this.categoryId = categoryId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getKeyword() {
    return keyword;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
