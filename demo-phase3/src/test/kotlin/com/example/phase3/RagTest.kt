package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.spring.ai.embedding.SpringAiLLMEmbeddingProvider
import ai.koog.spring.ai.vectorstore.DocumentWithMetadata
import ai.koog.spring.ai.vectorstore.KoogVectorStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RagTest(
    val vectorStore: KoogVectorStore,
    val embeddingProvider: SpringAiLLMEmbeddingProvider,
    val promptExecutor: PromptExecutor,
) : FunSpec({
    extensions(SpringExtension)

    val vpn = DocumentWithMetadata(
        content = "사내 VPN은 재택 시 필수",
        metadata = mapOf("category" to "보안"),
        id = "policy-vpn",
    )
    val lunch = DocumentWithMetadata(
        content = "점심 법인카드 한도는 1.5만원",
        metadata = mapOf("category" to "총무"),
        id = "policy-lunch",
    )
    var ids = emptyList<String>()

    beforeSpec { ids = vectorStore.add(listOf(vpn, lunch)) }
    afterSpec { vectorStore.delete(ids) }

    test("벡터 검색이 관련 문서를 찾는다") {
        val hits = vectorStore.search(
            SimilaritySearchRequest(queryText = "재택근무 네트워크 규정", limit = 2),
        )

        hits.forEach { println("RagTest 검색 결과: ${it.score.value} | ${it.document.content}") }
        hits shouldHaveSize 2
        hits.first().document.content shouldBe "사내 VPN은 재택 시 필수"
        hits.first().document.metadata["category"] shouldBe "보안"
    }

    test("검색을 전략 노드로 세운다") {
        val rag = strategy<String, String>("rag") {
            val retrieve by node<String, String>("retrieve") { question ->
                val found = vectorStore.search(
                    SimilaritySearchRequest(queryText = question, limit = 1),
                )
                "아래 규정만 근거로 답한다.\n규정: ${found.first().document.content}\n질문: $question"
            }
            val answer by nodeLLMRequest()

            edge(nodeStart forwardTo retrieve)
            edge(retrieve forwardTo answer)
            edge(answer forwardTo nodeFinish transformed { it.textContent() })
        }
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = rag,
            systemPrompt = "한 문장으로만 답한다.",
            temperature = 0.0,
        )

        val result = agent.run("재택근무할 때 뭘 켜야 하지?")

        println("RagTest 전략 노드 응답: $result")
        result shouldContain "VPN"
    }

    test("어댑터가 받는 검색 요청은 유사도뿐이다") {
        val thrown = shouldThrow<IllegalArgumentException> {
            vectorStore.search(KeywordSearchRequest(queryText = "VPN"))
        }

        println("RagTest 거절 메시지: ${thrown.message}")
        thrown.message shouldBe "Spring AI VectorStore supports only similarity search requests"
    }

    test("같은 임베딩 모델을 Koog 쪽 인터페이스로도 부른다") {
        val embedding = embeddingProvider.embed("사내 VPN은 재택 시 필수", KoogModels.BGE_M3)

        println("RagTest 임베딩 차원: ${embedding.size}")
        embedding shouldHaveSize 1024
    }
})
