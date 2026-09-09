package com.example.demo.pattern.ch01

import com.example.demo.pattern.*
import org.springframework.stereotype.Component

@Component
class PromptChaining : PatternRunner {
    override val spec = PatternSpec(
        1, "프롬프트 체이닝", "Prompt Chaining",
        summary = "파이프라인 패턴이라고도 부르는 분할 정복입니다. 체인의 각 단계는 한 가지 연산에만 집중하고, 앞 단계의 출력이 다음 단계의 입력이 됩니다.",
        code = """
            // 모델이 채워 줄 그릇. 이 클래스로 JSON 스키마가 만들어져 프롬프트에 함께 실리고,
            // 돌아온 JSON 은 Jackson 이 생성자 파라미터 이름을 맞춰 그대로 채운다
            data class Outline(val sections: List<String>)

            val outline = chatClient.prompt()
                .user("${'$'}주제 로 목차를 잡아줘")
                .call()
                .entity(Outline::class.java)      // 답을 문자열이 아니라 객체로 받는다

            val draft = chatClient.prompt()
                .user("아래 목차로 초안을 써줘\n" + outline.sections.joinToString("\n"))
                .call()
                .content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                A[주제] --> M1((모델))
                M1 --> O[Outline 객체]
                O --> M2((모델))
                M2 --> R[초안]
        """.trimIndent(),
        defaultInput = "사내 개발자 대상 Spring AI 스터디",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val outline = ctx.step("① 목차 잡기") {
            it.user("${ctx.input} 로 발표 목차를 3개 항목으로 잡아줘").call().entity(Outline::class.java)
        } ?: return ctx.result("목차를 타입으로 받지 못했습니다", ok = false)

        ctx.note("한 프롬프트에 여러 지시를 몰아넣으면 모델이 일부를 빠뜨립니다. 그래서 쪼갭니다")
        ctx.note("중간 결과가 타입이라 파싱이 없다 — sections ${outline.sections.size}개")

        val draft = ctx.step("② 초안 쓰기") {
            it.user("아래 목차를 아우르는 소개를 한 문장으로 써줘. 제목 없이.\n" + outline.sections.joinToString("\n")).call().content()
        }
        return ctx.result(draft)
    }
}

/** 모델이 채워 줄 그릇. 이 클래스로 JSON 스키마가 만들어져 프롬프트에 실린다. */
data class Outline(val sections: List<String>)
