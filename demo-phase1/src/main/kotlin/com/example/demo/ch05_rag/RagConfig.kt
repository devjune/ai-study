package com.example.demo.ch05_rag

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * RAG용 인메모리 VectorStore. 로컬 ONNX 임베딩(EmbeddingModel)으로
 * 문서를 벡터화해 저장한다. 데모라 사내 문서 몇 개를 시드로 넣는다.
 */
@Configuration
class RagConfig {

    @Bean
    fun vectorStore(embeddingModel: EmbeddingModel): VectorStore {
        val store = SimpleVectorStore.builder(embeddingModel).build()
        store.add(
            listOf(
                Document("환불은 구매 후 7일 이내에 가능합니다. 단순 변심으로 인한 환불은 왕복 배송비를 구매자가 부담합니다."),
                Document("디지털 상품은 다운로드하거나 사용한 이력이 있으면 환불이 제한됩니다."),
                Document("고객센터 영업시간은 평일 09시부터 18시까지이며, 주말과 공휴일은 휴무입니다."),
            ),
        )
        return store
    }
}
