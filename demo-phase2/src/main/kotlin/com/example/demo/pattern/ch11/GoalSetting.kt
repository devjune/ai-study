package com.example.demo.pattern.ch11

import com.example.demo.pattern.*
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.stereotype.Component

@Component
class GoalSetting : PatternRunner {
    override val spec = PatternSpec(
        11, "목표 설정과 모니터링", "Goal Setting and Monitoring",
        summary = "목표가 없으면 에이전트는 시키는 것에만 반응합니다. 목표를 명시하고 달성 여부를 확인합니다.",
        code = """
            data class Goal(val description: String, val successCriteria: String)

            val goal = chatClient.prompt()
                .system("요청을 목표로 정리한다. successCriteria 에는 달성 여부를 판정할 기준을 한 문장으로 적는다.")
                .user(요청)
                .call().entity(Goal::class.java)

            val 작업결과 = chatClient.prompt().user(goal.description).call().content()

            // evaluator 는 4장에서 만든 것과 같다
            // 작업 결과가 성공 기준을 만족하는지 코드가 판정한다
            val done = evaluator
                .evaluate(EvaluationRequest(goal.successCriteria, 작업결과))
                .isPass
        """.trimIndent(),
        diagram = """
            flowchart LR
                R[요청] --> M1((모델<br/>목표 정의))
                M1 --> G[successCriteria]
                G --> M2((모델<br/>작업))
                M2 --> M3((모델<br/>달성 판정))
                M3 --> D{isPass}
        """.trimIndent(),
        defaultInput = "고객에게 환불 절차를 안내한다",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val goal = ctx.step("① 목표 정의") {
            it.system("요청을 목표로 정리한다. successCriteria 에는 달성 여부를 판정할 기준을 한 문장으로 적는다.")
                .user(ctx.input).call().entity(Goal::class.java)
        } ?: return ctx.result("목표를 타입으로 받지 못했습니다", ok = false)

        ctx.note("성공 기준 — ${goal.successCriteria}")

        val work = ctx.step("② 작업 수행") { it.user("${goal.description} 두 문장 이내로 답해라.").call().content() }

        val verdict = ctx.evaluate("③ 달성 판정 (Evaluator)", EvaluationRequest(goal.successCriteria, work.orEmpty()))
        ctx.note(
            if (verdict == null) "채점기 호출 실패"
            else "달성 판정 — ${verdict.verdict()}",
        )
        return ctx.result(work)
    }
}

/** 모델이 채워 줄 그릇. successCriteria 가 달성 판정의 기준이 된다. */
data class Goal(val description: String, val successCriteria: String)
