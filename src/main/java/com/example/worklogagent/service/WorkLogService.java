package com.example.worklogagent.service;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.repository.WorkLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 업무일지 CRUD 서비스. 화면 컨트롤러가 사용한다.
 * 저장소(엑셀/DB)는 WorkLogRepository 로 주입되므로 이 서비스는 방식과 무관하다.
 */
@Service
public class WorkLogService {

    private final WorkLogRepository repository;

    public WorkLogService(WorkLogRepository repository) {
        this.repository = repository;
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
}
