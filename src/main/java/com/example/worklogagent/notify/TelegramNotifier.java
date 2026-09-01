package com.example.worklogagent.notify;

import com.example.worklogagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 텔레그램 봇으로 알림을 보낸다. 완전 무료.
 *
 * 준비 (한 번만):
 *   1) 텔레그램에서 @BotFather 검색 → /newbot → 봇 토큰 발급
 *   2) 만든 봇과 대화를 한 번 시작(아무 메시지나 전송)
 *   3) https://api.telegram.org/bot<토큰>/getUpdates 를 브라우저에서 열어
 *      chat.id 값 확인 → application.yml 의 app.telegram.chat-id 에 입력
 */
@Component
public class TelegramNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();

    public TelegramNotifier(AppProperties props) {
        this.props = props;
    }

    @Override
    public boolean isEnabled() {
        return props.telegram() != null
                && props.telegram().botToken() != null
                && !props.telegram().botToken().isBlank();
    }

    @Override
    public void send(String message) {
        String url = "https://api.telegram.org/bot"
                + props.telegram().botToken() + "/sendMessage";
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", props.telegram().chatId(),
                            "text", message
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("텔레그램 알림 전송 완료");
        } catch (Exception e) {
            // 알림 실패가 애플리케이션 전체를 죽이지 않도록 방어
            log.error("텔레그램 알림 전송 실패", e);
        }
    }
}
