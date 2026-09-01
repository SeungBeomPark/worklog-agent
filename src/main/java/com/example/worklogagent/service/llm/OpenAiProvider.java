package com.example.worklogagent.service.llm;

import com.example.worklogagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 제공자 (Chat Completions).
 * 엔드포인트: https://api.openai.com/v1/chat/completions
 * 응답 구조가 Anthropic 과 달라서 파싱이 다르다: choices[0].message.content
 */
@Component
public class OpenAiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();

    public OpenAiProvider(AppProperties props) {
        this.props = props;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public boolean isConfigured() {
        AppProperties.Llm.OpenAi o = props.llm().openai();
        return o != null && o.apiKey() != null && !o.apiKey().isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String userPrompt) {
        AppProperties.Llm.OpenAi o = props.llm().openai();

        Map<String, Object> body = Map.of(
                "model", o.model(),
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        Map<String, Object> resp = restClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + o.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        // 응답: { "choices": [ { "message": { "content": "..." } } ] }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            return "(요약 실패: 빈 응답)";
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object text = message != null ? message.get("content") : null;
        return text != null ? text.toString() : "(요약 실패: 텍스트 없음)";
    }
}