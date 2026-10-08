package com.example.worklogagent.mcp;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.service.ReportService;
import com.example.worklogagent.service.WorkLogService;
import com.example.worklogagent.service.WorkLogSummaryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 업무일지를 MCP 도구로 노출한다. Claude Desktop 등 MCP 클라이언트가 호출한다.
 *
 * 실제 저장/조회/요약은 기존 서비스(WorkLogService, WorkLogSummaryService)에 위임하므로,
 * 엑셀/DB 어느 저장소든 그대로 동작한다.
 * Spring AI 1.0.x 안정 패턴인 @Tool + MethodToolCallbackProvider(McpToolConfig) 로 등록한다.
 */
@Service
public class WorkLogMcpTools {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WorkLogService service;
    private final WorkLogSummaryService summaryService;
    private final ReportService reportService;

    public WorkLogMcpTools(WorkLogService service,
                           WorkLogSummaryService summaryService,
                           ReportService reportService) {
        this.service = service;
        this.summaryService = summaryService;
        this.reportService = reportService;
    }

    // ── 조회 ──
    @Tool(description = "특정 날짜(yyyy-MM-dd)의 업무일지 한 건을 조회한다.")
    public String getWorklog(
            @ToolParam(description = "조회할 날짜 (yyyy-MM-dd)") String date) {
        LocalDate d = LocalDate.parse(date, ISO);
        Optional<WorkLogEntry> found = service.getByDate(d);
        if (found.isEmpty()) {
            return date + " 에 작성된 업무일지가 없습니다.";
        }
        return format(found.get());
    }

    @Tool(description = "시작일~종료일(yyyy-MM-dd) 범위의 업무일지 원본 목록을 조회한다. "
            + "요약을 직접 만들고 싶을 때(원본이 필요할 때) 사용한다.")
    public String searchWorklog(
            @ToolParam(description = "시작일 (yyyy-MM-dd)") String startDate,
            @ToolParam(description = "종료일 (yyyy-MM-dd)") String endDate) {
        LocalDate from = LocalDate.parse(startDate, ISO);
        LocalDate to = LocalDate.parse(endDate, ISO);
        List<WorkLogEntry> all = collectRange(from, to);
        if (all.isEmpty()) {
            return startDate + " ~ " + endDate + " 기간에 작성된 업무일지가 없습니다.";
        }
        return all.stream().map(this::format).collect(Collectors.joining("\n\n"));
    }

    // ── 요약 (앱 내부 LLM) ──
    @Tool(description = "시작일~종료일(yyyy-MM-dd) 범위의 업무일지를 앱 내부 LLM 으로 요약한 "
            + "완성본을 반환한다. 웹/스케줄러의 주간 요약과 동일한 형식의 결과가 필요할 때 사용한다. "
            + "원본 항목만 필요하면 search_worklog 를 쓴다.")
    public String summarizeWorklog(
            @ToolParam(description = "시작일 (yyyy-MM-dd)") String startDate,
            @ToolParam(description = "종료일 (yyyy-MM-dd)") String endDate) {
        LocalDate from = LocalDate.parse(startDate, ISO);
        LocalDate to = LocalDate.parse(endDate, ISO);
        return summaryService.summarizeRange(from, to);
    }

    // ── 미작성일 점검 ──
    @Tool(description = "시작일~종료일(yyyy-MM-dd) 범위에서 '영업일인데 업무일지가 없는' 날짜 목록을 반환한다. "
            + "주말/공휴일은 제외하고 영업일만 본다.")
    public String findMissingDays(
            @ToolParam(description = "시작일 (yyyy-MM-dd)") String startDate,
            @ToolParam(description = "종료일 (yyyy-MM-dd)") String endDate) {
        LocalDate from = LocalDate.parse(startDate, ISO);
        LocalDate to = LocalDate.parse(endDate, ISO);
        List<LocalDate> missing = service.findMissingBusinessDays(from, to);
        if (missing.isEmpty()) {
            return startDate + " ~ " + endDate + " 기간의 영업일 업무일지가 모두 작성되어 있습니다.";
        }
        String list = missing.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        return "미작성 영업일 %d일: %s".formatted(missing.size(), list);
    }

    // ── 작성 ──
    @Tool(description = "특정 날짜에 업무일지를 작성하거나 수정한다. 같은 날짜가 있으면 덮어쓴다.")
    public String writeWorklog(
            @ToolParam(description = "작성할 날짜 (yyyy-MM-dd)") String date,
            @ToolParam(description = "업무유형 (예: 프로젝트, 휴가, 교육, 기타)", required = false) String type,
            @ToolParam(description = "프로젝트명", required = false) String project,
            @ToolParam(description = "업무 내용") String content) {
        LocalDate d = LocalDate.parse(date, ISO);
        service.save(new WorkLogEntry(
                d,
                type == null ? "" : type,
                project == null ? "" : project,
                content == null ? "" : content));
        return date + " 업무일지를 저장했습니다.";
    }

    @Tool(description = "시작일~종료일(yyyy-MM-dd) 범위의 각 날짜에 같은 내용으로 업무일지를 작성한다. "
            + "휴가/교육처럼 여러 날 연속 업무를 한 번에 넣을 때 사용한다. "
            + "이미 작성된 날짜는 건너뛴다.")
    public String writeWorklogRange(
            @ToolParam(description = "시작일 (yyyy-MM-dd)") String startDate,
            @ToolParam(description = "종료일 (yyyy-MM-dd)") String endDate,
            @ToolParam(description = "업무유형 (예: 프로젝트, 휴가, 교육, 기타)", required = false) String type,
            @ToolParam(description = "프로젝트명", required = false) String project,
            @ToolParam(description = "업무 내용") String content,
            @ToolParam(description = "주말/공휴일을 제외할지 (기본 true)", required = false) Boolean skipWeekendHoliday) {
        LocalDate from = LocalDate.parse(startDate, ISO);
        LocalDate to = LocalDate.parse(endDate, ISO);
        boolean skip = (skipWeekendHoliday == null) || skipWeekendHoliday;

        WorkLogService.RangeSaveResult result = service.saveRange(
                from, to,
                type == null ? "" : type,
                project == null ? "" : project,
                content == null ? "" : content,
                skip);

        StringBuilder sb = new StringBuilder();
        sb.append("저장 %d일".formatted(result.savedDates().size()));
        if (!result.savedDates().isEmpty()) {
            sb.append(": ").append(result.savedDates().stream()
                    .map(LocalDate::toString).collect(Collectors.joining(", ")));
        }
        if (!result.skippedDates().isEmpty()) {
            sb.append("\n이미 작성돼 건너뛴 %d일: ".formatted(result.skippedDates().size()))
              .append(result.skippedDates().stream()
                    .map(LocalDate::toString).collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    // ── 삭제 ──
    @Tool(description = "특정 날짜의 업무일지를 삭제한다.")
    public String deleteWorklog(
            @ToolParam(description = "삭제할 날짜 (yyyy-MM-dd)") String date) {
        LocalDate d = LocalDate.parse(date, ISO);
        service.delete(d);
        return date + " 업무일지를 삭제했습니다.";
    }

    // ── 통계 ──
    @Tool(description = "시작일~종료일(yyyy-MM-dd) 범위의 업무유형별 / 프로젝트별 작성 일수를 집계한다. "
            + "'이번 달 프로젝트별로 며칠씩 썼나' 같은 질문에 사용한다.")
    public String worklogStats(
            @ToolParam(description = "시작일 (yyyy-MM-dd)") String startDate,
            @ToolParam(description = "종료일 (yyyy-MM-dd)") String endDate) {
        LocalDate from = LocalDate.parse(startDate, ISO);
        LocalDate to = LocalDate.parse(endDate, ISO);
        WorkLogService.WorkLogStats stats = service.statsOfRange(from, to);

        if (stats.totalDays() == 0) {
            return startDate + " ~ " + endDate + " 기간에 작성된 업무일지가 없습니다.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("기간 %s ~ %s, 총 작성 %d일\n".formatted(startDate, endDate, stats.totalDays()));
        sb.append("[업무유형별]\n");
        for (Map.Entry<String, Integer> e : stats.byType().entrySet()) {
            sb.append("- %s: %d일\n".formatted(e.getKey(), e.getValue()));
        }
        sb.append("[프로젝트별]\n");
        for (Map.Entry<String, Integer> e : stats.byProject().entrySet()) {
            sb.append("- %s: %d일\n".formatted(e.getKey(), e.getValue()));
        }
        return sb.toString().trim();
    }

    // ── 리포트 내보내기 ──
    @Tool(description = "시작일~종료일(yyyy-MM-dd) 범위의 업무 리포트를 생성한다. "
            + "요약과 유형별/프로젝트별 통계를 묶은 Markdown 을 반환하며, "
            + "saveFile=true 면 서버의 리포트 폴더에 .md 파일로도 저장하고 그 경로를 함께 알려준다. "
            + "'이번 달 업무 보고서 만들어줘' 같은 요청에 사용한다.")
    public String exportReport(
            @ToolParam(description = "시작일 (yyyy-MM-dd)") String startDate,
            @ToolParam(description = "종료일 (yyyy-MM-dd)") String endDate,
            @ToolParam(description = ".md 파일로도 저장할지 (기본 false)", required = false) Boolean saveFile) {
        LocalDate from = LocalDate.parse(startDate, ISO);
        LocalDate to = LocalDate.parse(endDate, ISO);
        boolean save = saveFile != null && saveFile;

        ReportService.Report report = reportService.generate(from, to, save);
        if (report.savedPath() != null) {
            return report.markdown() + "\n\n---\n저장된 파일: " + report.savedPath();
        }
        return report.markdown();
    }

    // ── 내부 유틸 ──
    private List<WorkLogEntry> collectRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }
        List<WorkLogEntry> result = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate lastMonth = to.withDayOfMonth(1);
        while (!cursor.isAfter(lastMonth)) {
            for (WorkLogEntry e : service.listByMonth(cursor.getYear(), cursor.getMonthValue())) {
                if (!e.date().isBefore(from) && !e.date().isAfter(to)) {
                    result.add(e);
                }
            }
            cursor = cursor.plusMonths(1);
        }
        result.sort(Comparator.comparing(WorkLogEntry::date));
        return result;
    }

    private String format(WorkLogEntry e) {
        return "[%s] 유형=%s, 프로젝트=%s\n%s".formatted(
                e.date(),
                e.type() == null || e.type().isBlank() ? "-" : e.type(),
                e.project() == null || e.project().isBlank() ? "-" : e.project(),
                e.content() == null || e.content().isBlank() ? "(내용 없음)" : e.content());
    }
}
