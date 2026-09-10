package com.example.phase3

import ai.koog.spring.ai.embedding.SpringAiLLMEmbeddingProvider
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RagConfig {
    // SimpleVectorStore는 자동 구성 대상이 아니다. 이 빈이 있어야 Koog 쪽
    // SpringAiVectorStoreAutoConfiguration이 KoogVectorStore를 만든다(7장).
    @Bean
    fun vectorStore(embeddingModel: EmbeddingModel): VectorStore =
        SimpleVectorStore.builder(embeddingModel).build()

    // 임베딩 스타터의 빈은 @ConditionalOnMissingBean(LLMEmbeddingProvider::class)라
    // 채팅 스타터의 LLMClient에 밀려 만들어지지 않는다. 직접 짓는다(7장).
    @Bean
    fun embeddingProvider(embeddingModel: EmbeddingModel): SpringAiLLMEmbeddingProvider =
        SpringAiLLMEmbeddingProvider.builder()
            .embeddingModel(embeddingModel)
            .build()
}
