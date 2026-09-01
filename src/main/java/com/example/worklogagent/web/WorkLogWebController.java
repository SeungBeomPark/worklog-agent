package com.example.worklogagent.web;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.service.WorkLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 업무일지 화면 컨트롤러 (Thymeleaf).
 *
 *   GET  /worklogs?year=&month=   → 월별 목록
 *   GET  /worklogs/form?date=     → 작성/수정 폼 (date 있으면 수정)
 *   POST /worklogs/save           → 저장(추가/수정)
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

    /** 저장 (추가/수정 공용). 날짜가 같으면 덮어씀. */
    @PostMapping("/worklogs/save")
    public String save(@RequestParam String date,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String project,
                       @RequestParam(required = false) String content) {
        LocalDate d = LocalDate.parse(date, ISO);
        service.save(new WorkLogEntry(
                d,
                nullToEmpty(type),
                nullToEmpty(project),
                nullToEmpty(content)));
        // 저장 후 그 달 목록으로
        return "redirect:/worklogs?year=" + d.getYear() + "&month=" + d.getMonthValue();
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
