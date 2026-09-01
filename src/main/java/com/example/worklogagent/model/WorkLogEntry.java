package com.example.worklogagent.model;

import java.time.LocalDate;

/**
 * 업무일지 엑셀의 한 행(하루치)을 표현한다.
 *
 * @param date    날짜
 * @param type    업무유형 (예: "프로젝트", "휴가")
 * @param project 프로젝트명
 * @param content 업무 내용
 */
public record WorkLogEntry(
        LocalDate date,
        String type,
        String project,
        String content
) {
    /** 실제 업무 내용이 채워졌는지 (작성 여부 판정용) */
    public boolean isWritten() {
        return content != null && !content.isBlank();
    }

    /** 휴가/휴무일인지 */
    public boolean isDayOff() {
        return "휴가".equals(type);
    }
}
