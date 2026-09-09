package com.example.demo.pattern.ch20

import com.example.demo.pattern.*
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component

@Component
class Prioritization : PatternRunner {
    override val spec = PatternSpec(
        20, "우선순위 결정", "Prioritization",
        summary = "할 일은 많고 손은 한정돼 있습니다. 순위는 모델이 매기고, 정렬과 처리는 코드가 합니다 — 판단과 실행을 가르는 자리입니다.",
        code = """
            data class Task(val title: String, val priority: Int, val reason: String)

            val tasks = chatClient.prompt()
                .system("고객 문의마다 처리 우선순위를 매긴다. 기준은 고객이 지금 겪는 손실과 시급성이다. title 은 문의를 10자 안팎 명사구로 요약한다. priority 는 1~5, 클수록 급하다. reason 에는 그 순위인 근거를 20자 안팎으로 적는다.")
                .user(할일들)
                .call()
                .entity(object : ParameterizedTypeReference<List<Task>>() {})

            tasks.sortedByDescending { it.priority }.forEach { 처리(it) }
        """.trimIndent(),
        diagram = """
            flowchart LR
                T[할 일 목록] --> M((모델))
                M --> L[Task 목록<br/>priority 가 붙어 온다]
                L --> S[코드가 정렬]
                S --> P[순서대로 처리]
        """.trimIndent(),
        defaultInput = "단순 가격 문의, 결제 실패 신고, 신규 가입 문의, 배송 지연 항의",
        model = "qwen3:4b-instruct",
        maxTokens = 500,
    )

    override fun run(ctx: RunContext): RunResult {
        val tasks = ctx.step("우선순위 매기기") {
            it.system("고객 문의마다 처리 우선순위를 매긴다. 기준은 고객이 지금 겪는 손실과 시급성이다. title 은 문의를 10자 안팎 명사구로 요약한다. priority 는 1~5, 클수록 급하다. reason 에는 제목을 반복하지 말고 그 순위인 근거를 20자 안팎으로 적는다.")
                .user(ctx.input).call().entity(object : ParameterizedTypeReference<List<Task>>() {})
        } ?: return ctx.result("목록을 타입으로 받지 못했습니다", ok = false)

        val sorted = tasks.sortedByDescending { it.priority }
        sorted.forEach { ctx.note("[${it.priority}] ${it.title} — ${it.reason.take(50)}") }
        ctx.note("순위는 모델이 매기고, 정렬과 처리는 코드가 합니다")
        ctx.note("입력 순서를 바꿔 넣어 보면 순위가 순서에 끌리는지 보입니다")
        return ctx.result(sorted.joinToString("\n") { "${it.priority} · ${it.title}" })
    }
}

/** 모델이 채워 줄 그릇. priority 를 보고 정렬하는 것은 코드의 몫이다. */
data class Task(val title: String, val priority: Int, val reason: String)
