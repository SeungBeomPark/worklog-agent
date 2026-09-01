package com.example.worklogagent.service.llm;

/**
 * LLM 제공자 추상화.
 * 새 제공자(예: Gemini)를 추가하려면 이 인터페이스를 구현하고 @Component 만 붙인 뒤
 * providerName() 이 설정값과 매칭되게 하면 된다.
 */
public interface LlmProvider {
    /** 이 제공자의 식별 이름. 설정의 app.llm.provider 값과 매칭된다. (예: "anthropic") */
    String providerName();

    /** 설정값(키/모델)이 채워져 실제 호출 가능한 상태면 true. */
    boolean isConfigured();

    /** 프롬프트를 보내고 응답 텍스트를 받는다. */
    String complete(String userPrompt);
}
