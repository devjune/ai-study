package com.example.demo.pattern.ch09

import com.example.demo.pattern.*
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.document.Document
import org.springframework.stereotype.Component

@Component
class LearningAndAdaptation(private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        9, "사례로 배우기", "Learning and Adaptation",
        summary = "모델은 호출마다 백지입니다. 사람이 고쳐 준 답을 쌓아 두고, 다음 답부터 예시로 반영합니다.",
        code = """
            // 사람이 고쳐 준 좋은 답을 저장해 두고
            vectorStore.add(listOf(Document("질문: ${'$'}질문\n모범 답: ${'$'}고친답")))

            // 다음 질문에는 비슷한 사례가 예시로 딸려 들어간다
            chatClient.prompt().user(새질문)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                A[사람이 고쳐 준 답] --> S[(VectorStore)]
                Q[다음 질문] --> S
                Q --> M((모델))
                S -->|비슷한 사례를 예시로| M
        """.trimIndent(),
        defaultInput = "환불 배송비 얼마야?",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val before = ctx.step("① 사례를 쌓기 전") { it.user("${ctx.input} 한 문장으로만 답해라.").call().content() }

        docs.lessons.add(
            listOf(
                Document("질문: 환불 배송비 얼마야?\n모범 답: 단순 변심이면 왕복 배송비 6,000원을 구매자가 부담합니다. 규정 문서를 근거로 답하고 추측하지 않습니다."),
            ),
        )
        ctx.note("사람이 고쳐 준 답 1건을 VectorStore 에 저장했습니다")

        val after = ctx.step("② 사례를 쌓은 뒤") {
            it.user("${ctx.input} 한 문장으로만 답해라.").advisors(QuestionAnswerAdvisor.builder(docs.lessons).build()).call().content()
        }
        ctx.note("모델을 다시 학습시킨 것이 아니라 프롬프트에 예시를 넣은 것입니다")
        return ctx.result("전 — $before\n\n후 — $after")
    }
}
