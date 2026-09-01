package com.example.worklogagent.service;

import com.example.worklogagent.config.AppProperties;
import com.example.worklogagent.service.llm.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 호출 진입점.
 * 설정(app.llm.provider)에 맞는 LlmProvider 를 골라 위임한다.
 * 제공자별 상세(엔드포인트/응답 파싱)는 각 LlmProvider 구현이 담당한다.
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final AppProperties props;
    private final List<LlmProvider> providers;

    public LlmClient(AppProperties props, List<LlmProvider> providers) {
        this.props = props;
        this.providers = providers;
    }

    /** 프롬프트를 던지고 요약 텍스트를 돌려받는다. 실패 시 예외 대신 안내 문자열 반환. */
    public String summarize(String userPrompt) {
        String selected = props.llm().provider();
        if (selected == null || selected.isBlank()) {
            log.warn("app.llm.provider 가 설정되지 않았습니다.");
            return "(요약 실패: LLM 제공자가 설정되지 않았습니다.)";
        }

        // 설정된 이름과 일치하는 제공자 찾기
        LlmProvider provider = providers.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(selected.trim()))
                .findFirst()
                .orElse(null);

        if (provider == null) {
            String available = providers.stream()
                    .map(LlmProvider::providerName)
                    .collect(Collectors.joining(", "));
            log.warn("알 수 없는 제공자 '{}'. 사용 가능: {}", selected, available);
            return "(요약 실패: 알 수 없는 LLM 제공자 '" + selected + "')";
        }

        if (!provider.isConfigured()) {
            log.warn("제공자 '{}' 의 API 키/모델이 설정되지 않았습니다.", selected);
            return "(요약 실패: '" + selected + "' 의 API 키가 설정되지 않았습니다.)";
        }

        try {
            return provider.complete(userPrompt);
        } catch (Exception e) {
            log.error("LLM 요약 호출 실패 (provider={})", selected, e);
            return "(요약 생성에 실패했습니다. 원본 업무일지를 확인해 주세요.)";
        }
    }
}