package com.example.demo.pattern.ch19

import com.example.demo.pattern.*
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.stereotype.Component

@Component
class Evaluation : PatternRunner {
    override val spec = PatternSpec(
        19, "평가와 모니터링", "Evaluation and Monitoring",
        summary = "LLM은 확률적이라 기존 테스트로는 신뢰성을 보장할 수 없습니다. 답의 품질을 코드가 잽니다.",
        code = """
            // EvaluationRequest 는 세 가지를 받는다 — 질문, 근거로 삼을 문서, 채점할 답
            val response = evaluator.evaluate(EvaluationRequest(질문, 검색된문서, 답))

            response.isPass        // 통과 여부 (boolean)
            response.getScore()    // 점수 (float)
            response.getFeedback() // 왜 그렇게 판정했는지 (채점기에 따라 비어 있다)
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델<br/>답))
                M1 --> M2((모델<br/>채점))
                D[(근거 문서)] --> M2
                M2 --> R[통과 여부 · 점수 · 근거]
        """.trimIndent(),
        defaultInput = "단순 변심으로 환불하면 배송비 얼마 내야 해?",
        model = "granite4:micro",
    )

    override fun run(ctx: RunContext): RunResult {
        val good = ctx.step("① 답 만들기") {
            it.user("${ctx.input}\n사내 규정: 단순 변심이면 왕복 배송비 6,000원을 구매자가 부담합니다.").call().content()
        }
        listOf("정상 답" to good.orEmpty(), "엉뚱한 답" to "고객센터 영업시간은 평일 9시부터 6시까지입니다.")
            .forEach { (label, answer) ->
                val v = ctx.evaluate("② 채점 — $label", EvaluationRequest(ctx.input, Docs.POLICIES, answer))
                ctx.note(
                    if (v == null) "$label — 채점기 호출 실패"
                    else "$label — ${v.verdict()}",
                )
            }
        ctx.note("같은 채점기를 회귀 테스트에 그대로 씁니다")
        return ctx.result(good)
    }
}
