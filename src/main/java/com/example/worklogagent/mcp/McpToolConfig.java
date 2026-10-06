package com.example.worklogagent.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 서버에 노출할 도구를 등록한다.
 *
 * Spring AI 1.0.x 안정 패턴: @Tool 이 붙은 객체를 MethodToolCallbackProvider 로 감싸
 * ToolCallbackProvider 빈으로 등록하면, MCP 서버 스타터가 자동으로 MCP 도구로 변환해 노출한다.
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider workLogToolCallbackProvider(WorkLogMcpTools workLogMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(workLogMcpTools)
                .build();
    }
}
