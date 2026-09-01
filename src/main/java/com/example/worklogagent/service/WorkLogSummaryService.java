package com.example.worklogagent.service;

import com.example.worklogagent.model.WorkLogEntry;
import com.example.worklogagent.repository.WorkLogRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 주별 업무일지 요약. 이번 주(월~금) 항목을 모아 LLM 에게 요약을 맡긴다.
 * 저장소(엑셀/DB)는 WorkLogRepository 로 추상화되어 있다.
 */
@Service
public class WorkLogSummaryService {

    private final WorkLogRepository repository;
    private final LlmClient llmClient;

    public WorkLogSummaryService(WorkLogRepository repository, LlmClient llmClient) {
        this.repository = repository;
        this.llmClient = llmClient;
    }

    /** 주어진 날짜가 속한 주(월~금)를 요약한다. */
    public String summarizeWeekOf(LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate friday = monday.plusDays(4);

        List<WorkLogEntry> entries = repository.findByDateRange(monday, friday);
        if (entries.isEmpty()) {
            return "이번 주(%s ~ %s)에 작성된 업무일지가 없습니다."
                    .formatted(monday, friday);
        }

        String prompt = buildPrompt(monday, friday, entries);
        return llmClient.summarize(prompt);
    }

    private String buildPrompt(LocalDate from, LocalDate to, List<WorkLogEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래는 %s ~ %s 주간 업무일지입니다.\n".formatted(from, to));
        sb.append("다음 지침에 따라 요약해 주세요:\n");
        sb.append("- 프로젝트별로 묶어서 핵심 업무를 정리\n");
        sb.append("- 전체를 3~5줄 이내로 간결하게\n");
        sb.append("- 휴가/휴무일은 마지막에 따로 표기\n\n");
        sb.append("[업무일지 원본]\n");
        for (WorkLogEntry e : entries) {
            String oneLineContent = e.content().replaceAll("[\\r\\n]+", " ");
            sb.append("- %s [%s] %s / %s\n".formatted(
                    e.date(), e.type(), e.project(), oneLineContent));
        }
        return sb.toString();
    }
}
