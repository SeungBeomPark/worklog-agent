package com.example.worklogagent.notify;

/**
 * 알림 전송 추상화.
 * 새 채널을 추가하려면 이 인터페이스를 구현하고 @Component 만 붙이면
 * CompositeNotifier 가 자동으로 수집해 함께 전송한다.
 */
public interface Notifier {

    /** 이 채널로 실제 전송한다. */
    void send(String message);

    /** 설정값이 채워져 있어 이 채널을 쓸 수 있으면 true. */
    boolean isEnabled();

    /** 로그용 채널 이름. 기본은 클래스명. */
    default String name() {
        return getClass().getSimpleName();
    }
}
