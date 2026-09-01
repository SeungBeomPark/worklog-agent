package com.example.worklogagent.web;

import com.example.worklogagent.notify.CompositeNotifier;
import com.example.worklogagent.service.HolidayService;
import com.example.worklogagent.service.WorkLogCheckService;
import com.example.worklogagent.service.WorkLogSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 개발/운영 중 즉시 동작을 확인하기 위한 엔드포인트.
 * 스케줄(17시)까지 기다리지 않고 브라우저로 바로 실행해 볼 수 있다.
 *
 *   GET /test/notify          → 알림 채널 연결 테스트
 *   GET /test/check           → 오늘 점검 즉시 실행
 *   GET /test/check?date=2026-07-24  → 특정 날짜 점검
 *   GET /test/summary         → 이번 주 요약 즉시 실행
 *
 * 운영 배포 시에는 이 컨트롤러를 제거하거나 접근을 제한하는 것이 좋다.
 */
@RestController
public class TestController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WorkLogCheckService checkService;
    private final WorkLogSummaryService summaryService;
    private final HolidayService holidayService;
    private final CompositeNotifier notifier;

    public TestController(WorkLogCheckService checkService,
                          WorkLogSummaryService summaryService,
                          HolidayService holidayService,
                          CompositeNotifier notifier) {
        this.checkService = checkService;
        this.summaryService = summaryService;
        this.holidayService = holidayService;
        this.notifier = notifier;
    }

    @GetMapping("/test/notify")
    public String testNotify() {
        notifier.send("✅ 알림 채널 연결 테스트 메시지입니다.");
        return "sent";
    }

    @GetMapping("/test/check")
    public String testCheck(@RequestParam(required = false) String date) {
        LocalDate target = (date != null) ? LocalDate.parse(date) : LocalDate.now(SEOUL);
        checkService.checkAndNotify(target);
        return "checked: " + target;
    }

    @GetMapping("/test/summary")
    public String testSummary(@RequestParam(required = false) String date) throws Exception {
        LocalDate target = (date != null) ? LocalDate.parse(date) : LocalDate.now(SEOUL);
        String summary = summaryService.summarizeWeekOf(target);
        notifier.send("📋 (테스트) 주간 업무 요약\n\n" + summary);
        return summary;
    }

    /** 공휴일 판별 확인: /test/holiday?date=2026-08-15 */
    @GetMapping("/test/holiday")
    public String testHoliday(@RequestParam String date) {
        LocalDate d = LocalDate.parse(date);
        boolean business = holidayService.isBusinessDay(d);
        return date + " → " + (business ? "영업일" : "휴일(주말/공휴일)");
    }
}
