package com.example.worklogagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 값을 담는 설정 객체.
 * record 로 선언하면 불변 + 생성자 자동 생성이라 편하다.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        WorkLog worklog,
        Telegram telegram,
        Slack slack,
        Discord discord,
        Llm llm,
        Holiday holiday
) {

    public record WorkLog(
            /** 업무일지 엑셀 파일들이 모여있는 디렉토리 */
            String directory,
            /** 파일명 접두사. 예: "MmList_" → MmList_202607.xls 를 찾는다 */
            String filePrefix
    ) {}

    public record Telegram(
            /** BotFather 에게서 받은 봇 토큰 */
            String botToken,
            /** 알림을 받을 대화방 chat id */
            String chatId
    ) {}

    public record Slack(
            /** Slack Incoming Webhook URL */
            String webhookUrl
    ) {}

    public record Discord(
            /** Discord Webhook URL */
            String webhookUrl
    ) {}

    public record Llm(
            /** 사용할 제공자: "anthropic" 또는 "openai" */
            String provider,
            /** Anthropic 설정 */
            Anthropic anthropic,
            /** OpenAI 설정 */
            OpenAi openai
    ) {
        public record Anthropic(
                /** Anthropic API 키 */
                String apiKey,
                /** 모델명 (예: claude-sonnet-4-6) */
                String model
        ) {}

        public record OpenAi(
                /** OpenAI API 키 */
                String apiKey,
                /** 모델명 (예: gpt-4o) */
                String model
        ) {}
    }

    public record Holiday(
            /** 공휴일을 API 로 조회할지, 하드코딩 목록을 쓸지 */
            boolean useApi,
            /** 공공데이터포털 특일정보 서비스키 (useApi=true 일 때) */
            String serviceKey
    ) {}
}
