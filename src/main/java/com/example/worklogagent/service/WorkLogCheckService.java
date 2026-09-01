package com.example.worklogagent.service;

import com.example.worklogagent.notify.CompositeNotifier;
import com.example.worklogagent.repository.WorkLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 일일 점검 조율자.
 * 영업일 판별 → 작성 확인 → 미작성 시 알림, 순서로 호출한다.
 * 저장소(엑셀/DB)는 WorkLogRepository 로 추상화되어 있어 방식과 무관하게 동작한다.
 */
@Service
public class WorkLogCheckService {

    private static final Logger log = LoggerFactory.getLogger(WorkLogCheckService.class);

    private final HolidayService holidayService;
    private final WorkLogRepository repository;
    private final CompositeNotifier notifier;

    public WorkLogCheckService(HolidayService holidayService,
                               WorkLogRepository repository,
                               CompositeNotifier notifier) {
        this.holidayService = holidayService;
        this.repository = repository;
        this.notifier = notifier;
    }

    public void checkAndNotify(LocalDate date) {
        if (!holidayService.isBusinessDay(date)) {
            log.info("{} 는 휴일이므로 점검을 건너뜁니다.", date);
            return;
        }

        try {
            if (repository.isWritten(date)) {
                log.info("{} 업무일지 작성 완료 확인.", date);
            } else {
                String msg = "⚠️ 업무일지 미작성 알림\n\n오늘(%s) 업무일지가 아직 작성되지 않았습니다.\n퇴근 전 작성 부탁드립니다."
                        .formatted(date);
                notifier.send(msg);
                log.info("{} 업무일지 미작성 → 알림 발송.", date);
            }
        } catch (Exception e) {
            log.error("{} 업무일지 확인 실패", date, e);
            notifier.send("❗업무일지를 확인하지 못했습니다(%s). 저장소 설정을 확인해 주세요."
                    .formatted(date));
        }
    }
}
