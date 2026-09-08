package com.example.demo.pattern

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Component

/**
 * 사내 규정 문서. **모델이 사전 지식으로는 알 수 없는 내용**으로 채웠다.
 * 일반 상식으로 답할 수 있으면 검색 결과를 참조했는지 무시했는지 구분되지 않는다.
 *
 * `by lazy` 로 첫 사용까지 미룬다. 빈 생성 시점에 임베딩하면 Ollama 가 없을 때
 * 앱이 기동조차 못 해 나머지 패턴도 못 본다.
 */
@Component
class Docs(private val embeddingModel: EmbeddingModel) {

    val store: VectorStore by lazy {
        SimpleVectorStore.builder(embeddingModel).build().apply { add(POLICIES) }
    }

    /** 9장 학습과 적응 — 사람이 고쳐 준 답을 쌓아 두는 자리 */
    val lessons: VectorStore by lazy { SimpleVectorStore.builder(embeddingModel).build() }

    companion object {
        val POLICIES = listOf(
            Document("환불은 구매 후 14일 이내에 가능합니다. 단순 변심이면 왕복 배송비 6,000원을 구매자가 부담합니다."),
            Document("디지털 상품은 다운로드 이력이 있으면 환불이 제한됩니다. 미다운로드 상태면 30일까지 환불됩니다."),
            Document("사내 장비 대여는 총무팀 승인 후 최대 3개월까지 가능하며, 연장은 1회에 한합니다."),
            Document("교환은 수령 후 7일 이내 1회만 가능하고, 색상 교환은 재고가 있을 때만 처리됩니다."),
        )
    }
}
