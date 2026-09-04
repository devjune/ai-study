package com.example.demo.ch03_memory

import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MemoryConfig {

    /**
     * 대화 메모리 (창 크기만큼 최근 메시지 유지).
     *
     * ChatMemory 와 ChatMemoryRepository 는 다른 역할이다.
     * - ChatMemory: 무엇을 기억할지 정하는 정책 (여기선 최근 N개 창)
     * - ChatMemoryRepository: 그것을 어디에 둘지 정하는 저장소
     *
     * 저장소만 갈아 끼우면 되고 정책 코드는 그대로다. 여기서 주입받는
     * JdbcChatMemoryRepository 는 starter 가 자동 구성한 빈이며,
     * Postgres 로 바꿔도 이 코드는 변하지 않는다.
     */
    @Bean
    fun chatMemory(chatMemoryRepository: ChatMemoryRepository): ChatMemory =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .build()
}
