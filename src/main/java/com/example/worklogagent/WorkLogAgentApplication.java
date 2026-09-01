package com.example.worklogagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 업무일지 확인 에이전트 진입점.
 *
 * @EnableScheduling            이 있어야 @Scheduled 메서드가 동작한다.
 * @ConfigurationPropertiesScan 이 있어야 AppProperties 가 빈으로 등록된다.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class WorkLogAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkLogAgentApplication.class, args);
    }
}
