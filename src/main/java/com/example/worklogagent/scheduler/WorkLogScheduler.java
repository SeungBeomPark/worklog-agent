package com.example.worklogagent.scheduler;

import com.example.worklogagent.notify.CompositeNotifier;
import com.example.worklogagent.service.WorkLogCheckService;
import com.example.worklogagent.service.WorkLogSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 스케줄 진입점.
 *   - 일일 점검: 평일 17:00 (주말은 cron 이 거르고, 공휴일은 서비스가 거른다)
 *   - 주간 요약: 금요일 17:30
 * 모든 시각은 Asia/Seoul 기준.
 */
@Component
public class WorkLogScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkLogScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WorkLogCheckService checkService;
    private final WorkLogSummaryService summaryService;
    private final CompositeNotifier notifier;

    public WorkLogScheduler(WorkLogCheckService checkService,
                            WorkLogSummaryService summaryService,
                            CompositeNotifier notifier) {
        this.checkService = checkService;
        this.summaryService = summaryService;
        this.notifier = notifier;
    }

    // 초 분 시 일 월 요일 → 매주 월~금 17:00:00
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Seoul")
    public void dailyCheck() {
        LocalDate today = LocalDate.now(SEOUL);
        log.info("[일일 점검] 시작: {}", today);
        checkService.checkAndNotify(today);
    }

    // 매주 금요일 17:30 주간 요약
    @Scheduled(cron = "0 30 17 * * FRI", zone = "Asia/Seoul")
    public void weeklySummary() {
        LocalDate today = LocalDate.now(SEOUL);
        log.info("[주간 요약] 시작: {}", today);
        try {
            String summary = summaryService.summarizeWeekOf(today);
            notifier.send("📋 이번 주 업무 요약\n\n" + summary);
        } catch (Exception e) {
            log.error("주간 요약 실패", e);
            notifier.send("❗주간 요약 생성에 실패했습니다. 로그를 확인해 주세요.");
        }
    }
}
