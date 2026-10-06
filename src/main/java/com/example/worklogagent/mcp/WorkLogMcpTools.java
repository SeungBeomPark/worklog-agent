package com.example.worklogagent.mcp;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.service.WorkLogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 업무일지를 MCP 도구로 노출한다. Claude Desktop 등 MCP 클라이언트가 호출한다.
 *
 * 실제 저장/조회는 기존 WorkLogService 에 위임하므로, 엑셀/DB 어느 저장소든 그대로 동작한다.
 * Spring AI 1.0.x 안정 패턴인 @Tool + MethodToolCallbackProvider(McpToolConfig) 로 등록한다.
 */
@Service
public class WorkLogMcpTools {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WorkLogService service;

    public WorkLogMcpTools(WorkLogService service) {
        this.service = service;
    }

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

    @Tool(description = "시작일~종료일(yyyy-MM-dd) 범위의 업무일지 목록을 조회한다. "
            + "주간/월간 요약이나 특정 기간 업무 확인에 사용한다.")
    public String searchWorklog(
            @ToolParam(description = "시작일 (yyyy-MM-dd)") String startDate,
            @ToolParam(description = "종료일 (yyyy-MM-dd)") String endDate) {
        LocalDate from = LocalDate.parse(startDate, ISO);
        LocalDate to = LocalDate.parse(endDate, ISO);
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }
        List<WorkLogEntry> all = collectRange(from, to);
        if (all.isEmpty()) {
            return startDate + " ~ " + endDate + " 기간에 작성된 업무일지가 없습니다.";
        }
        return all.stream().map(this::format).collect(Collectors.joining("\n\n"));
    }

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

    @Tool(description = "특정 날짜의 업무일지를 삭제한다.")
    public String deleteWorklog(
            @ToolParam(description = "삭제할 날짜 (yyyy-MM-dd)") String date) {
        LocalDate d = LocalDate.parse(date, ISO);
        service.delete(d);
        return date + " 업무일지를 삭제했습니다.";
    }

    // ── 내부 유틸 ──

    private List<WorkLogEntry> collectRange(LocalDate from, LocalDate to) {
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
