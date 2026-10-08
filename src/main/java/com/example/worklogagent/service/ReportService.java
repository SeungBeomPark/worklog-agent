package com.example.worklogagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 업무 리포트 생성 서비스.
 * 기존 요약(WorkLogSummaryService)과 통계(WorkLogService)를 조합해
 * Markdown 리포트를 만들고, 필요하면 파일로도 저장한다.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final WorkLogSummaryService summaryService;
    private final WorkLogService workLogService;

    /** 리포트 파일을 저장할 폴더 (기본: ./reports) */
    @Value("${app.report.directory}")
    private String reportDir;

    public ReportService(WorkLogSummaryService summaryService, WorkLogService workLogService) {
        this.summaryService = summaryService;
        this.workLogService = workLogService;
    }

    /** 리포트 결과. markdown 은 본문, savedPath 는 파일 저장 시 경로(안 하면 null). */
    public record Report(String markdown, String savedPath) {}

    /**
     * 기간 리포트를 생성한다.
     * @param from     시작일
     * @param to       종료일
     * @param saveFile true 면 .md 파일로도 저장
     */
    public Report generate(LocalDate from, LocalDate to, boolean saveFile) {
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }

        String summary = summaryService.summarizeRange(from, to);
        WorkLogService.WorkLogStats stats = workLogService.statsOfRange(from, to);
        String markdown = buildMarkdown(from, to, summary, stats);

        String savedPath = null;
        if (saveFile) {
            savedPath = save(from, to, markdown);
        }
        return new Report(markdown, savedPath);
    }

    private String buildMarkdown(LocalDate from, LocalDate to,
                                 String summary, WorkLogService.WorkLogStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 업무 리포트 (%s ~ %s)\n\n".formatted(from, to));

        sb.append("## 요약\n\n");
        sb.append(summary).append("\n\n");

        sb.append("## 통계\n\n");
        sb.append("- 총 작성 일수: **%d일**\n\n".formatted(stats.totalDays()));

        sb.append("### 업무유형별\n\n");
        if (stats.byType().isEmpty()) {
            sb.append("(데이터 없음)\n\n");
        } else {
            sb.append("| 업무유형 | 일수 |\n|---|---|\n");
            for (Map.Entry<String, Integer> e : stats.byType().entrySet()) {
                sb.append("| %s | %d |\n".formatted(e.getKey(), e.getValue()));
            }
            sb.append("\n");
        }

        sb.append("### 프로젝트별\n\n");
        if (stats.byProject().isEmpty()) {
            sb.append("(데이터 없음)\n\n");
        } else {
            sb.append("| 프로젝트 | 일수 |\n|---|---|\n");
            for (Map.Entry<String, Integer> e : stats.byProject().entrySet()) {
                sb.append("| %s | %d |\n".formatted(e.getKey(), e.getValue()));
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("_생성 시각: %s_\n".formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        return sb.toString();
    }

    private String save(LocalDate from, LocalDate to, String markdown) {
        try {
            Path dir = Path.of(reportDir);
            Files.createDirectories(dir);
            String fileName = "worklog-report_%s_%s_%s.md".formatted(
                    from, to, LocalDateTime.now().format(STAMP));
            Path file = dir.resolve(fileName);
            Files.writeString(file, markdown, StandardCharsets.UTF_8);
            log.info("리포트 저장: {}", file.toAbsolutePath());
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new UncheckedIOException("리포트 파일 저장 실패", e);
        }
    }
}
