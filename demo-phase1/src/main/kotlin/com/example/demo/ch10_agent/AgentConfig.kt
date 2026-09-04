package com.example.demo.ch10_agent

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 세 부품(RAG · 메모리 · 도구)을 하나의 빌더에 얹어 에이전트를 만든다.
 * vectorStore(§5)·chatMemory(§3)는 앞 장에서 만든 빈을 그대로 주입받는다.
 * 이 앱에서 유일한 ChatClient 빈이라, AgentController 가 이름 없이 주입받는다.
 */
@Configuration
class AgentConfig {

    @Bean
    fun supportAgent(
        builder: ChatClient.Builder,
        vectorStore: VectorStore,
        chatMemory: ChatMemory,
    ): ChatClient =
        builder
            .defaultSystem("너는 사내 고객지원 상담원이다. 문서에 근거해 답하라.")
            .defaultAdvisors(
                QuestionAnswerAdvisor.builder(vectorStore).build(),   // RAG: 사내 문서 근거
                MessageChatMemoryAdvisor.builder(chatMemory).build(), // 대화 맥락 유지
            )
            .defaultTools(OrderTools())                               // 실제 주문 조회 도구
            .build()
}
