package com.example.demo.pattern.ch09

import com.example.demo.pattern.*
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.document.Document
import org.springframework.stereotype.Component

@Component
class LearningAndAdaptation(private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        9, "학습과 적응", "Learning and Adaptation",
        summary = "모델은 호출마다 백지입니다. 겪은 일을 쌓아 두면, 다르게 물어도 지난 교정이 다음 답에 반영됩니다.",
        code = """
            // 겪은 일을 저장한다 — 틀렸던 답까지 함께 남겨 같은 함정을 피하게 한다
            vectorStore.add(listOf(Document("질문: ${'$'}질문\n처음 답(틀림): ${'$'}틀린답\n고친 답: ${'$'}고친답")))

            // 다르게 물어도 뜻이 가까우면 그 사례가 예시로 딸려 들어간다 (14장 의미 검색)
            chatClient.prompt().user(새질문)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                A[겪은 일<br/>틀린 답 + 고친 답] --> S[(VectorStore)]
                Q[새 질문] --> S
                Q --> M((모델))
                S -->|뜻이 가까운 사례를 예시로| M
        """.trimIndent(),
        defaultInput = "단순 변심으로 반품하면 비용이 얼마나 드나요?",
        model = "qwen3:4b-instruct",
    )

    /** 사례는 한 번만 쌓는다. 여러 번 눌러도 같은 것이 중복되지 않게 */
    @Volatile private var seeded = false

    override fun run(ctx: RunContext): RunResult {
        val before = ctx.step("① 사례 없이") { it.user("${ctx.input} 한 문장으로만 답해라.").call().content() }

        if (!seeded) {
            docs.lessons.add(
                listOf(
                    // 저장하는 것은 문서가 아니라 겪은 일이다 — 질문, 틀렸던 답, 사람이 고쳐 준 답
                    Document(
                        "질문: 환불 배송비 얼마야?\n" +
                            "처음 답(틀림): 주문 후 7일 이내 반품 시 10,000원을 환불합니다.\n" +
                            "고친 답: 단순 변심이면 왕복 배송비 6,000원을 구매자가 부담합니다.",
                    ),
                ),
            )
            seeded = true
        }
        ctx.note("저장한 사례의 질문은 \"환불 배송비 얼마야?\" — 지금 물은 말과 다릅니다")
        ctx.note("틀렸던 답까지 함께 저장해 같은 함정을 피하게 합니다")

        val after = ctx.step("② 사례를 쌓은 뒤") {
            it.user("${ctx.input} 한 문장으로만 답해라.")
                .advisors(QuestionAnswerAdvisor.builder(docs.lessons).build())
                .call().content()
        }
        ctx.note("말이 달라도 뜻이 가까우면 걸립니다 — 검색은 14장과 같은 의미 검색입니다")
        ctx.note("모델을 다시 학습시킨 것이 아니라 프롬프트에 예시를 넣은 것입니다")
        return ctx.result("전 — $before\n\n후 — $after")
    }
}
