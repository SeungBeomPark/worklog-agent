package com.example.worklogagent.service;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.repository.WorkLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * 업무일지 CRUD 서비스. 화면 컨트롤러가 사용한다.
 * 저장소(엑셀/DB)는 WorkLogRepository 로 주입되므로 이 서비스는 방식과 무관하다.
 */
@Service
public class WorkLogService {

    private final WorkLogRepository repository;
    private final HolidayService holidayService;

    public WorkLogService(WorkLogRepository repository, HolidayService holidayService) {
        this.repository = repository;
        this.holidayService = holidayService;
    }

    /** 특정 연월의 목록 */
    public List<WorkLogEntry> listByMonth(int year, int month) {
        return repository.findByMonth(year, month);
    }

    /** 특정 날짜 한 건 */
    public Optional<WorkLogEntry> getByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    /** 추가 또는 수정 (날짜 기준 upsert) */
    public void save(WorkLogEntry entry) {
        repository.save(entry);
    }

    /** 삭제 */
    public void delete(LocalDate date) {
        repository.deleteByDate(date);
    }

    /**
     * 범위 저장 결과.
     * @param savedDates   실제로 저장된 날짜들
     * @param skippedDates 이미 작성돼 있어 건너뛴 날짜들 (화면에서 alert 로 안내)
     */
    public record RangeSaveResult(List<LocalDate> savedDates, List<LocalDate> skippedDates) {}

    /**
     * 시작일~종료일 범위의 각 날짜에 같은 내용으로 저장한다.
     *  - skipWeekendHoliday=true 면 주말/공휴일은 조용히 제외
     *  - 이미 작성된 날짜는 건너뛰고 skippedDates 에 담아 반환
     *
     * @param start   시작일 (포함)
     * @param end     종료일 (포함)
     * @param type    업무유형
     * @param project 프로젝트
     * @param content 업무 내용 (모든 날짜에 동일 적용)
     * @param skipWeekendHoliday 주말/공휴일 제외 여부
     */
    public RangeSaveResult saveRange(LocalDate start, LocalDate end,
                                     String type, String project, String content,
                                     boolean skipWeekendHoliday) {
        // 시작일이 종료일보다 뒤면 서로 교환 (사용자가 거꾸로 입력한 경우 방어)
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        List<LocalDate> saved = new ArrayList<>();
        List<LocalDate> skipped = new ArrayList<>();

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            // 주말/공휴일 제외 옵션
            if (skipWeekendHoliday && !holidayService.isBusinessDay(cursor)) {
                cursor = cursor.plusDays(1);
                continue;
            }
            // 이미 작성된 날짜면 건너뛰기
            if (repository.findByDate(cursor).isPresent()) {
                skipped.add(cursor);
            } else {
                repository.save(new WorkLogEntry(cursor, type, project, content));
                saved.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return new RangeSaveResult(saved, skipped);
    }
}
