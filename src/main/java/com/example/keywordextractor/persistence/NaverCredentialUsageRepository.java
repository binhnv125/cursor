package com.example.keywordextractor.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NaverCredentialUsageRepository
    extends JpaRepository<NaverCredentialUsageEntity, NaverCredentialUsageId> {

  @Modifying
  @Transactional
  @Query(
      value =
          "insert into naver_credential_usage(customer_id, usage_date, used_count) "
              + "values (:customerId, :usageDate, 0) "
              + "on conflict (customer_id, usage_date) do nothing",
      nativeQuery = true)
  int ensureRow(@Param("customerId") String customerId, @Param("usageDate") LocalDate usageDate);

  /**
   * Atomically consumes 1 quota if used_count < dailyLimit.
   *
   * @return 1 if consumed, 0 otherwise
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "update naver_credential_usage "
              + "set used_count = used_count + 1 "
              + "where customer_id = :customerId "
              + "  and usage_date = :usageDate "
              + "  and used_count < :dailyLimit",
      nativeQuery = true)
  int tryConsume(
      @Param("customerId") String customerId,
      @Param("usageDate") LocalDate usageDate,
      @Param("dailyLimit") int dailyLimit);

  @Query(
      "select u from NaverCredentialUsageEntity u where u.usageDate = :usageDate and u.customerId in :customerIds")
  List<NaverCredentialUsageEntity> findForDate(
      @Param("usageDate") LocalDate usageDate, @Param("customerIds") List<String> customerIds);
}
