package com.example.keywordextractor.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NaverCredentialRepository extends JpaRepository<NaverCredentialEntity, String> {

  @Query("select c from NaverCredentialEntity c")
  List<NaverCredentialEntity> findAllCredentials();

  @Query("select c from NaverCredentialEntity c where c.enabled = true")
  List<NaverCredentialEntity> findEnabled();
}
