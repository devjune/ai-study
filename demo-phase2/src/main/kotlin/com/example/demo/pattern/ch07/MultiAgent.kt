package com.example.demo.pattern.ch07

import com.example.demo.pattern.*
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.stereotype.Component

@Component
class MultiAgent(private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        7, "멀티 에이전트", "Multi-Agent",
        summary = "하나의 에이전트가 모든 전문성을 갖출 수는 없습니다. 프롬프트 체이닝이 일의 단계를 나눴다면, 여기는 일꾼을 나눠 역할마다 다른 장비를 답니다.",
        code = """
            // defaultSystem — 이 ChatClient 로 나가는 모든 호출에 붙는 고정 시스템 메시지
            @Bean fun 조사자(b: ChatClient.Builder) = b.defaultSystem("규정에서 문의와 관련된 대목만 뽑는다").build()
            @Bean fun 작성자(b: ChatClient.Builder) = b.defaultSystem("조사 결과로 고객 안내문을 쓴다").build()

            // 누가 먼저 하고 무엇을 넘길지는 직접 짠다
            val 요약 = 조사자.prompt().user(문의).call().content()
            val 안내문 = 작성자.prompt().user("문의: ${'$'}문의\n조사 결과: ${'$'}요약").call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                T[문의] --> M1((조사자))
                M1 --> D[규정 요약]
                D --> M2((작성자))
                M2 --> R[고객 안내문]
        """.trimIndent(),
        defaultInput = "환불하고 싶은데 배송비는 어떻게 되나요?",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val summary = ctx.step("① 조사자") {
            it.system("규정에서 문의와 관련된 대목만 뽑는다. 한두 문장으로 정리한다.")
                .advisors(QuestionAnswerAdvisor.builder(docs.store).build())   // 검색은 이 역할에만
                .user(ctx.input).call().content()
        }
        val reply = ctx.step("② 작성자") {
            it.system("조사 결과로 고객 안내문을 쓴다. 두 문장으로.")
                .user("문의: ${ctx.input}\n조사 결과: $summary").call().content()
        }
        ctx.note("1장 체이닝과 코드가 닮았지만 나눈 것이 다릅니다. 체이닝은 프롬프트를 나눴고, 여기는 역할을 빈으로 나눕니다")
        ctx.note("역할마다 장비가 다릅니다. ① 프롬프트를 펼치면 검색된 규정이 실려 있고, ② 에는 없습니다")
        ctx.note("빈은 둘, 순서와 무엇을 넘길지는 코드가 정했습니다 — 조율 클래스는 없습니다")
        return ctx.result("조사 — $summary\n\n안내문 — $reply")
    }
}
