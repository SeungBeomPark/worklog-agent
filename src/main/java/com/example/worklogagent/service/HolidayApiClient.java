package com.example.worklogagent.service;

import com.example.worklogagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 공공데이터포털 "특일정보" API(getRestDeInfo) 연동.
 *
 * 데이터셋: https://www.data.go.kr/data/15012690/openapi.do
 * 엔드포인트:
 *   http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo
 * 파라미터:
 *   - solYear  : 연도 (필수). 연도만 주면 그 해 전체 공휴일을 받는다.
 *   - ServiceKey : 인증키
 *   - numOfRows : 한 페이지 개수 (연 공휴일 넉넉히 100)
 *   - _type=json : JSON 응답
 * 응답 item 필드:
 *   - locdate   : yyyyMMdd (정수)
 *   - dateName  : 공휴일 명칭
 *   - isHoliday : "Y"/"N" (공공기관 휴일 여부)
 *
 * 연도 단위로 한 번만 호출하고 결과를 캐싱한다.
 */
@Component
public class HolidayApiClient {

    private static final Logger log = LoggerFactory.getLogger(HolidayApiClient.class);

    private static final String ENDPOINT =
            "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
    private static final DateTimeFormatter LOCDATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();

    // 연도별 공휴일 캐시: year -> {공휴일 날짜 집합}
    private final Map<Integer, Set<LocalDate>> cacheByYear = new ConcurrentHashMap<>();

    public HolidayApiClient(AppProperties props) {
        this.props = props;
    }

    /** 해당 날짜가 공휴일이면 true. API 실패 시 예외를 던진다(호출측에서 폴백). */
    public boolean isHoliday(LocalDate date) {
        Set<LocalDate> holidays = cacheByYear.computeIfAbsent(
                date.getYear(), this::fetchYear);
        return holidays.contains(date);
    }

    /** 특정 연도 전체 공휴일을 API로 조회한다. */
    @SuppressWarnings("unchecked")
    private Set<LocalDate> fetchYear(int year) {
        // 서비스키는 이미 URL 인코딩된 값일 수 있으므로 직접 쿼리에 붙이고
        // UriComponentsBuilder 의 build(true) 로 재인코딩을 막는다.
        URI uri = UriComponentsBuilder.fromHttpUrl(ENDPOINT)
                .queryParam("solYear", year)
                .queryParam("numOfRows", 100)
                .queryParam("_type", "json")
                .queryParam("ServiceKey", props.holiday().serviceKey())
                .build(true)   // 이미 인코딩된 것으로 간주 (이중 인코딩 방지)
                .toUri();

        log.info("{}년 공휴일 API 조회 시작", year);

        Map<String, Object> root = restClient.get()
                .uri(uri)
                .retrieve()
                .body(Map.class);

        Set<LocalDate> result = ConcurrentHashMap.newKeySet();
        try {
            // response -> body -> items -> item(단건이면 객체, 복수면 배열)
            Map<String, Object> response = (Map<String, Object>) root.get("response");
            Map<String, Object> body = (Map<String, Object>) response.get("body");
            Object itemsObj = body.get("items");

            // items 가 빈 문자열("")로 오는 경우(결과 0건) 방어
            if (!(itemsObj instanceof Map)) {
                log.warn("{}년 공휴일 결과가 비어있습니다.", year);
                return result;
            }
            Map<String, Object> items = (Map<String, Object>) itemsObj;
            Object itemObj = items.get("item");

            List<Map<String, Object>> itemList;
            if (itemObj instanceof List) {
                itemList = (List<Map<String, Object>>) itemObj;
            } else if (itemObj instanceof Map) {
                itemList = List.of((Map<String, Object>) itemObj); // 1건일 때
            } else {
                return result;
            }

            for (Map<String, Object> item : itemList) {
                // isHoliday 가 "Y" 인 것만 실제 휴일로 인정
                Object isHoliday = item.get("isHoliday");
                if (isHoliday != null && !"Y".equals(isHoliday.toString().trim())) {
                    continue;
                }
                String locdate = String.valueOf(item.get("locdate")).trim();
                if (locdate.endsWith(".0")) {          // 숫자로 파싱되어 100.0 형태 방지
                    locdate = locdate.substring(0, locdate.length() - 2);
                }
                result.add(LocalDate.parse(locdate, LOCDATE_FMT));
            }
            log.info("{}년 공휴일 {}건 조회 완료", year, result.size());

        } catch (Exception e) {
            // 파싱 실패는 상위로 던져서 하드코딩 폴백을 타게 한다.
            throw new IllegalStateException("공휴일 API 응답 파싱 실패", e);
        }
        return result;
    }
}
