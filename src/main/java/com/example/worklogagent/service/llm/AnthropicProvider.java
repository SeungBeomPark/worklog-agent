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
 * Anthropic Claude 제공자.
 * 엔드포인트: https://api.anthropic.com/v1/messages
 */
@Component
public class AnthropicProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();

    public AnthropicProvider(AppProperties props) {
        this.props = props;
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public boolean isConfigured() {
        AppProperties.Llm.Anthropic a = props.llm().anthropic();
        return a != null && a.apiKey() != null && !a.apiKey().isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String userPrompt) {
        AppProperties.Llm.Anthropic a = props.llm().anthropic();

        Map<String, Object> body = Map.of(
                "model", a.model(),
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        Map<String, Object> resp = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", a.apiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        // 응답: { "content": [ { "type": "text", "text": "..." } ] }
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.get("content");
        if (content == null || content.isEmpty()) {
            return "(요약 실패: 빈 응답)";
        }
        Object text = content.get(0).get("text");
        return text != null ? text.toString() : "(요약 실패: 텍스트 없음)";
    }
}