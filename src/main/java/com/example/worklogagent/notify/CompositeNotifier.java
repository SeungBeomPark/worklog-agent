package com.example.worklogagent.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 활성화된 모든 알림 채널로 한 번에 전송하는 복합 알림기.
 *
 * Spring 이 Notifier 구현체(Telegram/Slack/Discord)를 List 로 모아 주입한다.
 * 이 클래스는 일부러 Notifier 를 구현하지 않는다.
 * 만약 Notifier 를 구현하면 List<Notifier> 주입 시 자기 자신이 포함되려다
 * 순환 의존이 발생하기 때문이다. 서비스들은 이 CompositeNotifier 를 직접 주입받는다.
 *
 * 설정된(isEnabled=true) 채널이 하나도 없으면 로그만 남긴다.
 */
@Component
public class CompositeNotifier {

    private static final Logger log = LoggerFactory.getLogger(CompositeNotifier.class);

    private final List<Notifier> channels;

    public CompositeNotifier(List<Notifier> channels) {
        this.channels = channels;
    }

    /** 활성화된 모든 채널로 전송. 한 채널이 실패해도 나머지는 계속 전송. */
    public void send(String message) {
        List<Notifier> active = channels.stream()
                .filter(Notifier::isEnabled)
                .toList();

        if (active.isEmpty()) {
            log.warn("활성화된 알림 채널이 없습니다. application.yml 설정을 확인하세요.");
            return;
        }

        for (Notifier channel : active) {
            channel.send(message);
        }
        log.info("{}개 채널로 알림 전송 시도 완료", active.size());
    }
}
