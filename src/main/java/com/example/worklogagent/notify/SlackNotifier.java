package com.example.worklogagent.notify;

import com.example.worklogagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Slack Incoming Webhook 으로 알림 전송. 무료.
 *
 * 준비:
 *   1) Slack → Apps → "Incoming Webhooks" 활성화
 *   2) 채널 선택 후 Webhook URL 발급
 *      (예: https://hooks.slack.com/services/T000/B000/XXXX)
 *   3) application.yml 의 app.slack.webhook-url 에 입력
 *
 * webhook-url 이 비어 있으면 이 채널은 자동으로 비활성화된다.
 */
@Component
public class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();

    public SlackNotifier(AppProperties props) {
        this.props = props;
    }

    @Override
    public boolean isEnabled() {
        return props.slack() != null
                && props.slack().webhookUrl() != null
                && !props.slack().webhookUrl().isBlank();
    }

    @Override
    public void send(String message) {
        try {
            restClient.post()
                    .uri(props.slack().webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", message))   // Slack 은 "text" 필드
                    .retrieve()
                    .toBodilessEntity();
            log.info("Slack 알림 전송 완료");
        } catch (Exception e) {
            log.error("Slack 알림 전송 실패", e);
        }
    }
}
