package com.example.keywordextractor.persistence.keyword;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KeywordMstRepository extends JpaRepository<KeywordMstEntity, Long> {

  @Modifying
  @Transactional
  @Query(
      value =
          "insert into keyword_mst(keyword, category_id, created_at, updated_at) "
              + "values (:keyword, :categoryId, now(), now()) "
              + "on conflict (keyword, category_id) do update set updated_at = now()",
      nativeQuery = true)
  int upsert(@Param("keyword") String keyword, @Param("categoryId") String categoryId);
}
