package com.example.worklogagent.notify;

import com.example.worklogagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Discord Webhook 으로 알림 전송. 무료.
 *
 * 준비:
 *   1) Discord 서버 → 채널 설정(톱니) → 연동 → 웹후크 → "새 웹후크"
 *   2) 웹후크 URL 복사
 *      (예: https://discord.com/api/webhooks/000/XXXX)
 *   3) application.yml 의 app.discord.webhook-url 에 입력
 *
 * webhook-url 이 비어 있으면 이 채널은 자동으로 비활성화된다.
 */
@Component
public class DiscordNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();

    public DiscordNotifier(AppProperties props) {
        this.props = props;
    }

    @Override
    public boolean isEnabled() {
        return props.discord() != null
                && props.discord().webhookUrl() != null
                && !props.discord().webhookUrl().isBlank();
    }

    @Override
    public void send(String message) {
        try {
            restClient.post()
                    .uri(props.discord().webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", message))   // Discord 는 "content" 필드
                    .retrieve()
                    .toBodilessEntity();
            log.info("Discord 알림 전송 완료");
        } catch (Exception e) {
            log.error("Discord 알림 전송 실패", e);
        }
    }
}
