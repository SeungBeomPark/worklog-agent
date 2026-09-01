package com.example.worklogagent.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 리포지토리.
 * 메서드 이름만으로 쿼리가 자동 생성된다.
 */
public interface WorkLogJpaRepository extends JpaRepository<WorkLogJpaEntity, Long> {

    Optional<WorkLogJpaEntity> findByWorkDate(LocalDate workDate);

    List<WorkLogJpaEntity> findByWorkDateBetweenOrderByWorkDate(LocalDate from, LocalDate to);

    void deleteByWorkDate(LocalDate workDate);
}
