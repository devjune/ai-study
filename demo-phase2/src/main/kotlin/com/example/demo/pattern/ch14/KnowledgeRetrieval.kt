package com.example.demo.pattern.ch14

import com.example.demo.pattern.*
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.stereotype.Component

@Component
class KnowledgeRetrieval(private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        14, "지식 검색", "Knowledge Retrieval (RAG)",
        summary = "모델은 학습 데이터 밖의 것을 모릅니다. 답하기 전에 관련 문서를 찾아 프롬프트에 넣어 근거를 만듭니다.",
        code = """
            .advisors(
                QuestionAnswerAdvisor            // RAG 전용 Advisor. 검색부터 주입까지 한다
                    .builder(vectorStore)        // 어디서 찾을지
                    .searchRequest(SEARCH)       // 어떻게 찾을지 (아래)
                    .build(),
            )

            val SEARCH = SearchRequest.builder()
                .topK(3)                   // ① 점수 높은 순으로 3건을 뽑고
                .similarityThreshold(0.5)  // ② 그중 0.5 미만은 버린다 (전부 미달이면 주입 없음)
                .build()
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> S[(VectorStore)]
                S -->|임계값을 넘은 문서만| P[프롬프트에 주입]
                Q --> P
                P --> M((모델))
        """.trimIndent(),
        defaultInput = "단순 변심으로 환불하면 배송비 얼마 내야 해?",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val search = SearchRequest.builder().topK(3).similarityThreshold(0.5).build()
        val answer = ctx.step("검색 + 답") {
            it.user(ctx.input)
                .advisors(QuestionAnswerAdvisor.builder(docs.store).searchRequest(search).build())
                .call().content()
        }
        val found = docs.store.similaritySearch(SearchRequest.from(search).query(ctx.input).build()).orEmpty()
        if (found.isEmpty()) ctx.note("검색 0건 — 임계값 0.5 를 넘는 문서가 없어 아무것도 주입되지 않았습니다")
        found.forEach { ctx.note("%.3f · %s".format(it.score ?: 0.0, it.text.orEmpty().take(50))) }
        return ctx.result(answer)
    }
}
