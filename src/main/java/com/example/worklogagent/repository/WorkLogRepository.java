package com.example.worklogagent.repository;

import com.example.worklogagent.model.WorkLogEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 업무일지 저장소 추상화.
 * 엑셀 구현(ExcelWorkLogRepository)과 DB 구현(JpaWorkLogRepository)이 각각 구현한다.
 * 화면/컨트롤러/스케줄러는 이 인터페이스에만 의존하므로,
 * 저장 방식을 바꿔도 나머지 코드는 그대로다.
 *
 * 날짜(LocalDate)를 하루치의 키로 사용한다 (하루 1건 기준, 업로드 양식과 동일).
 */
public interface WorkLogRepository {

    /** 특정 날짜 한 건 조회. 없으면 Optional.empty() */
    Optional<WorkLogEntry> findByDate(LocalDate date);

    /** 기간(from~to, 양끝 포함) 내 모든 항목을 날짜순으로 */
    List<WorkLogEntry> findByDateRange(LocalDate from, LocalDate to);

    /** 특정 연월(yyyy, MM)의 모든 항목 (화면 목록용) */
    List<WorkLogEntry> findByMonth(int year, int month);

    /** 추가 또는 수정 (같은 날짜가 있으면 덮어씀 = upsert) */
    void save(WorkLogEntry entry);

    /** 특정 날짜 삭제 */
    void deleteByDate(LocalDate date);

    /** 해당 날짜에 작성된 내용이 있는지 (스케줄러 점검용) */
    default boolean isWritten(LocalDate date) {
        return findByDate(date).map(WorkLogEntry::isWritten).orElse(false);
    }
}
