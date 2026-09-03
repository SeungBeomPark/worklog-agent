package com.example.worklogagent.web;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.service.WorkLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 업무일지 화면 컨트롤러 (Thymeleaf).
 *
 *   GET  /worklogs?year=&month=   → 월별 목록
 *   GET  /worklogs/form?date=     → 작성/수정 폼 (date 있으면 수정)
 *   POST /worklogs/save           → 저장 (수정=단일, 신규=범위)
 *   POST /worklogs/delete         → 삭제
 */
@Controller
public class WorkLogWebController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WorkLogService service;

    public WorkLogWebController(WorkLogService service) {
        this.service = service;
    }

    /** 월별 목록 화면. 파라미터 없으면 이번 달. */
    @GetMapping("/worklogs")
    public String list(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       Model model) {
        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month)
                : YearMonth.now();

        List<WorkLogEntry> entries = service.listByMonth(ym.getYear(), ym.getMonthValue());

        model.addAttribute("entries", entries);
        model.addAttribute("year", ym.getYear());
        model.addAttribute("month", ym.getMonthValue());
        model.addAttribute("prev", ym.minusMonths(1));
        model.addAttribute("next", ym.plusMonths(1));
        return "worklogs/list";
    }

    /** 작성/수정 폼. date 파라미터가 있으면 기존 값을 채워 수정 모드. */
    @GetMapping("/worklogs/form")
    public String form(@RequestParam(required = false) String date, Model model) {
        WorkLogEntry entry;
        boolean editMode = false;

        if (date != null && !date.isBlank()) {
            LocalDate d = LocalDate.parse(date, ISO);
            Optional<WorkLogEntry> found = service.getByDate(d);
            if (found.isPresent()) {
                entry = found.get();
                editMode = true;
            } else {
                entry = new WorkLogEntry(d, "", "", "");
            }
        } else {
            entry = new WorkLogEntry(LocalDate.now(), "", "", "");
        }

        model.addAttribute("entry", entry);
        model.addAttribute("editMode", editMode);
        return "worklogs/form";
    }

    /**
     * 저장.
     *  - 수정 모드(editMode=true): 해당 단일 날짜만 덮어쓰기
     *  - 신규 모드: startDate~endDate 범위의 각 날짜에 같은 내용 저장
     *              (이미 작성된 날짜는 건너뛰고 alert 로 안내)
     */
    @PostMapping("/worklogs/save")
    public String save(@RequestParam(required = false, defaultValue = "false") boolean editMode,
                       @RequestParam(required = false) String date,        // 수정 모드용 단일 날짜
                       @RequestParam(required = false) String startDate,   // 신규 모드용 시작일
                       @RequestParam(required = false) String endDate,     // 신규 모드용 종료일
                       @RequestParam(required = false, defaultValue = "false") boolean skipWeekendHoliday,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String project,
                       @RequestParam(required = false) String content,
                       RedirectAttributes ra) {

        if (editMode) {
            // 단일 날짜 수정
            LocalDate d = LocalDate.parse(date, ISO);
            service.save(new WorkLogEntry(d, nullToEmpty(type), nullToEmpty(project), nullToEmpty(content)));
            return "redirect:/worklogs?year=" + d.getYear() + "&month=" + d.getMonthValue();
        }

        // 신규 범위 저장
        LocalDate start = LocalDate.parse(startDate, ISO);
        LocalDate end = LocalDate.parse(endDate, ISO);

        WorkLogService.RangeSaveResult result = service.saveRange(
                start, end, nullToEmpty(type), nullToEmpty(project), nullToEmpty(content),
                skipWeekendHoliday);

        // 건너뛴(이미 작성된) 날짜가 있으면 목록 화면에서 alert 로 보여주도록 flash 로 전달
        if (!result.skippedDates().isEmpty()) {
            String skipped = result.skippedDates().stream()
                    .map(LocalDate::toString)
                    .collect(Collectors.joining(", "));
            ra.addFlashAttribute("skippedMessage",
                    "이미 작성된 일지가 있어 아래 날짜는 저장하지 않았습니다:\n" + skipped);
        }

        // 시작일이 속한 달의 목록으로 이동
        LocalDate first = start.isAfter(end) ? end : start;
        return "redirect:/worklogs?year=" + first.getYear() + "&month=" + first.getMonthValue();
    }

    /** 삭제 */
    @PostMapping("/worklogs/delete")
    public String delete(@RequestParam String date) {
        LocalDate d = LocalDate.parse(date, ISO);
        service.delete(d);
        return "redirect:/worklogs?year=" + d.getYear() + "&month=" + d.getMonthValue();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}