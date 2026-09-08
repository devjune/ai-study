package com.example.demo.pattern.ch06

import com.example.demo.pattern.*
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.stereotype.Component

@Component
class Planning : PatternRunner {
    override val spec = PatternSpec(
        6, "계획 수립", "Planning",
        summary = "복잡한 요청은 한 번의 행동으로 끝나지 않습니다. 목표를 단계로 나누고 순서대로 실행합니다.",
        code = """
            // 모델이 채워 줄 계획의 모양. Plan 은 모델이 아니라 JSON 을 받을 그릇이다
            data class Plan(val steps: List<Step>)
            data class Step(val order: Int, val instruction: String)

            // ① 계획 세우기 — 목표를 주고 단계 목록을 받는다. 모델 호출 한 번
            val plan = chatClient.prompt()
                .system("목표를 실행 가능한 단계로 쪼개라. instruction 은 한 문장씩.")
                .user("신입 개발자 첫 주 온보딩")
                .call()
                .entity(Plan::class.java)
            // plan 에는 이런 것이 담겨 온다. 단계 수도 내용도 실행할 때마다 모델이 정한다
            // Plan(steps = [
            //     Step(order = 1, instruction = "개발 환경과 계정을 준비한다"),
            //     Step(order = 2, instruction = "첫 주에 만날 팀과 일정을 표로 정리한다"),
            // ])

            // ② 계획 실행 — 단계마다 한 번씩 부른다. 앞 단계의 질문과 답은 ChatMemory 가 다음 호출에 실어 준다 (8장)
            val memory = MessageWindowChatMemory.builder().maxMessages(20).build()
            val executor = chatClient.mutate()
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build()
            val planId = UUID.randomUUID().toString()          // 이 계획 하나가 대화 하나

            for (step in plan.steps.sortedBy { it.order }) {   // 실행은 여기서 직접
                executor.prompt()
                    .user(step.instruction)                    // 앞 단계는 Advisor 가 알아서 붙인다
                    .advisors { it.param(ChatMemory.CONVERSATION_ID, planId) }
                    .call().content()
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                G[목표] --> M1((모델<br/>계획))
                M1 --> P[Plan.steps]
                P --> L[for 문]
                L --> M2((모델<br/>단계 실행))
                M2 --> L
        """.trimIndent(),
        defaultInput = "신입 개발자 첫 주 온보딩",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val plan = ctx.step("① 계획 세우기") {
            it.system("목표를 실행 가능한 단계 2개로만 쪼개라. instruction 은 한 문장.")
                .user(ctx.input).call().entity(Plan::class.java)
        } ?: return ctx.result("계획을 타입으로 받지 못했습니다", ok = false)

        ctx.note("계획 ${plan.steps.size}단계 — " + plan.steps.joinToString(" / ") { it.instruction.take(30) })

        // 계획 하나를 대화 하나로 본다. 앞 단계의 질문·답은 Advisor 가 다음 호출에 실어 준다 (8장)
        val memory: ChatMemory = MessageWindowChatMemory.builder().maxMessages(20).build()
        val advisor = MessageChatMemoryAdvisor.builder(memory).build()
        val planId = "plan-" + System.nanoTime()
        val done = plan.steps.sortedBy { it.order }.take(2).mapIndexed { i, step ->
            ctx.step("② 단계 ${i + 1} 실행") {
                it.user("한 문장으로 답해라: ${step.instruction}")
                    .advisors(advisor).advisors { a -> a.param(ChatMemory.CONVERSATION_ID, planId) }
                    .call().content()
            }
        }
        ctx.note("2단계 프롬프트를 펼치면 [USER] 1단계 / [ASSISTANT] 답이 앞에 붙어 있습니다 — 순서에 뜻이 생기는 자리입니다")
        return ctx.result(done.joinToString("\n\n"))
    }
}

/** 모델이 채워 줄 그릇. 단계 수도 내용도 실행할 때마다 모델이 정한다. */
data class Plan(val steps: List<Step>)

data class Step(val order: Int, val instruction: String)
