package com.example.demo.axis

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Component

/**
 * RAG 축이 쓰는 인메모리 VectorStore.
 *
 * `by lazy` 로 **첫 사용 시점에** 만든다. 빈 생성 시점에 시드하면 문서를 임베딩하느라
 * Ollama 를 부르고, Ollama 가 없으면 앱이 기동조차 못 해 나머지 세 축도 못 본다.
 * (`by lazy` 는 기본이 thread-safe 라, 화면이 모델 수만큼 병렬로 때려도 한 번만 시드된다.)
 */
@Component
class RagStore(private val embeddingModel: EmbeddingModel) {

    val store: VectorStore by lazy {
        SimpleVectorStore.builder(embeddingModel).build().apply { add(DOCUMENTS) }
    }

    private companion object {
        /**
         * 일부러 **모델이 사전지식으로는 알 수 없는 내용**으로 채웠다. 일반 상식으로
         * 답할 수 있는 질문이면 검색 결과를 참조했는지 무시했는지 구분되지 않는다.
         */
        val DOCUMENTS = listOf(
            Document("환불은 구매 후 14일 이내에 가능합니다. 단순 변심이면 왕복 배송비 6,000원을 구매자가 부담합니다."),
            Document("디지털 상품은 다운로드 이력이 있으면 환불이 제한됩니다. 미다운로드 상태면 30일까지 환불됩니다."),
            Document("사내 장비 대여는 총무팀 승인 후 최대 3개월까지 가능하며, 연장은 1회에 한합니다."),
            Document("교환은 수령 후 7일 이내 1회만 가능하고, 색상 교환은 재고가 있을 때만 처리됩니다."),
        )
    }
}
