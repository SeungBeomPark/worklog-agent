package com.example.worklogagent.service;

import com.example.worklogagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 영업일 판별.
 *   - 주말: DayOfWeek 로 즉시 판별
 *   - 공휴일:
 *       useApi=true  → 공공데이터포털 특일정보 API 조회 (실패 시 하드코딩 폴백)
 *       useApi=false → 하드코딩 목록만 사용
 */
@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    private final AppProperties props;
    private final HolidayApiClient apiClient;

    /**
     * API 를 못 쓰는 상황(키 미설정/장애)을 위한 폴백 목록.
     * 2026년 대한민국 공휴일 예시. 회사 자체 휴무일도 여기 추가 가능.
     */
    private static final Set<LocalDate> FALLBACK_HOLIDAYS = Set.of(
            LocalDate.of(2026, 1, 1),    // 신정
            LocalDate.of(2026, 2, 16),   // 설날 연휴
            LocalDate.of(2026, 2, 17),   // 설날
            LocalDate.of(2026, 2, 18),   // 설날 연휴
            LocalDate.of(2026, 3, 1),    // 삼일절
            LocalDate.of(2026, 3, 2),    // 삼일절 대체공휴일
            LocalDate.of(2026, 5, 5),    // 어린이날
            LocalDate.of(2026, 5, 24),   // 부처님오신날
            LocalDate.of(2026, 5, 25),   // 부처님오신날 대체공휴일
            LocalDate.of(2026, 6, 6),    // 현충일
            LocalDate.of(2026, 8, 15),   // 광복절
            LocalDate.of(2026, 9, 24),   // 추석 연휴
            LocalDate.of(2026, 9, 25),   // 추석
            LocalDate.of(2026, 9, 26),   // 추석 연휴
            LocalDate.of(2026, 10, 3),   // 개천절
            LocalDate.of(2026, 10, 9),   // 한글날
            LocalDate.of(2026, 12, 25)   // 성탄절
    );

    public HolidayService(AppProperties props, HolidayApiClient apiClient) {
        this.props = props;
        this.apiClient = apiClient;
    }

    /** 영업일이면 true (주말도 공휴일도 아님) */
    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !isPublicHoliday(date);
    }

    private boolean isPublicHoliday(LocalDate date) {
        boolean useApi = props.holiday() != null && props.holiday().useApi();
        if (useApi) {
            try {
                return apiClient.isHoliday(date);
            } catch (Exception e) {
                // API 장애 시에도 점검이 멈추지 않도록 하드코딩 목록으로 폴백
                log.warn("공휴일 API 조회 실패 - 하드코딩 목록으로 폴백합니다.", e);
            }
        }
        return FALLBACK_HOLIDAYS.contains(date);
    }
}
