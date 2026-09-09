package com.example.demo.pattern.ch06

import com.example.demo.pattern.*
import org.springframework.stereotype.Component

@Component
class Planning : PatternRunner {
    override val spec = PatternSpec(
        6, "계획 수립", "Planning",
        summary = "단계를 미리 정할 수 없는 일이 있습니다. 단계 나누기부터 모델에게 맡깁니다.",
        code = """
            val report = chatClient.prompt()
                .user(${'"'}""
                    1. '${'$'}목표' 를 이룰 계획을 불릿 3개로 세워라. 불릿은 하나에 20자 안으로.
                    2. 그 계획을 팀장에게 보고하듯 두 문장으로 요약해라.

                    아래 두 절로만 답한다.
                    ### Plan
                    ### Summary
                ${'"'}"".trimIndent())
                .call()
                .content()      // 계획도 요약도 이 문자열 하나에 함께 온다
        """.trimIndent(),
        diagram = """
            flowchart LR
                G[목표] --> M((모델))
                M --> R[한 응답]
                R --> P["### Plan"]
                R --> S["### Summary"]
        """.trimIndent(),
        defaultInput = "신입 개발자 첫 주 온보딩",
        model = "qwen3:4b-instruct",
        maxTokens = 500,
    )

    override fun run(ctx: RunContext): RunResult {
        val report = ctx.step("계획과 실행을 한 호출에") {
            it.user(BRIEF.format(ctx.input)).call().content()
        }.orEmpty()

        val plan = report.substringAfter("### Plan", "").substringBefore("### Summary").trim()
        ctx.note(
            if (plan.isEmpty()) "### Plan 절을 못 찾았습니다 — 형식은 부탁일 뿐 보장이 아닙니다"
            else "모델이 세운 계획 — " + plan.lines().joinToString(" / ") { it.trim().removePrefix("-").trim() },
        )
        ctx.note("이 단계들은 코드 어디에도 없습니다. 같은 목표로 한 번 더 눌러 보면 다르게 옵니다")
        return ctx.result(report)
    }

    private companion object {
        val BRIEF = """
            1. '%s' 를 이룰 계획을 불릿 3개로 세워라. 불릿은 하나에 20자 안으로.
            2. 그 계획을 팀장에게 보고하듯 두 문장으로 요약해라.

            아래 두 절로만 답한다.
            ### Plan
            ### Summary
        """.trimIndent()
    }
}
