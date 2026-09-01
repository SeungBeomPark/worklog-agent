package com.example.worklogagent.repository;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.repository.jpa.WorkLogJpaEntity;
import com.example.worklogagent.repository.jpa.WorkLogJpaRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DB(JPA) 기반 저장소 구현. WorkLogRepository 를 구현해
 * 화면/스케줄러가 엑셀과 동일하게 사용하도록 한다.
 *
 * worklog.storage=db 일 때만 빈으로 등록된다(StorageCondition).
 */
@Repository
@Conditional(StorageCondition.Db.class)
public class JpaWorkLogRepository implements WorkLogRepository {

    private final WorkLogJpaRepository jpa;

    public JpaWorkLogRepository(WorkLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<WorkLogEntry> findByDate(LocalDate date) {
        return jpa.findByWorkDate(date).map(WorkLogJpaEntity::toDomain);
    }

    @Override
    public List<WorkLogEntry> findByDateRange(LocalDate from, LocalDate to) {
        return jpa.findByWorkDateBetweenOrderByWorkDate(from, to).stream()
                .map(WorkLogJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkLogEntry> findByMonth(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        return findByDateRange(first, last);
    }

    @Override
    @Transactional
    public void save(WorkLogEntry entry) {
        // 같은 날짜가 있으면 수정, 없으면 추가 (upsert)
        Optional<WorkLogJpaEntity> existing = jpa.findByWorkDate(entry.date());
        if (existing.isPresent()) {
            WorkLogJpaEntity e = existing.get();
            e.update(entry.type(), entry.project(), entry.content());
            jpa.save(e);
        } else {
            jpa.save(new WorkLogJpaEntity(
                    entry.date(), entry.type(), entry.project(), entry.content()));
        }
    }

    @Override
    @Transactional
    public void deleteByDate(LocalDate date) {
        jpa.deleteByWorkDate(date);
    }
}
