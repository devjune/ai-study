package com.example.demo.pattern.ch08

import com.example.demo.pattern.*
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.stereotype.Component

@Component
class MemoryManagement : PatternRunner {
    override val spec = PatternSpec(
        8, "대화 기억", "Memory Management",
        summary = "LLM은 호출마다 아무것도 기억하지 못합니다. 맥락을 유지하려면 지난 메시지를 다시 넣어 줘야 합니다.",
        code = """
            // 무엇을 기억할지 정하는 정책 — 최근 20개 메시지만 유지한다
            val chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build()

            val chatClient = builder
                // 지난 대화를 프롬프트에 자동으로 붙여 주는 Advisor
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build()

            chatClient.prompt()
                .user("방금 뭐라고 했지?")
                // 대화 ID는 호출 시점에 넘긴다. 빠뜨리면 예외가 난다
                .advisors { it.param(ChatMemory.CONVERSATION_ID, "user-42") }
                .call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                U1[1턴] --> M1((모델))
                M1 --> S[(ChatMemory)]
                U2[2턴] --> S
                S -->|지난 대화를 얹어| M2((모델))
        """.trimIndent(),
        defaultInput = "내 이름은 준이야",
        model = "granite4:micro",
    )

    override fun run(ctx: RunContext): RunResult {
        val memory: ChatMemory = MessageWindowChatMemory.builder().maxMessages(20).build()
        val advisor = MessageChatMemoryAdvisor.builder(memory).build()
        val id = "demo-" + System.nanoTime()

        val first = ctx.step("① 첫 턴") {
            it.user(ctx.input).advisors(advisor).advisors { a -> a.param(ChatMemory.CONVERSATION_ID, id) }
                .call().content()
        }
        val second = ctx.step("② 다음 턴 — 기억하나") {
            it.user("방금 내가 뭐라고 했지?").advisors(advisor).advisors { a -> a.param(ChatMemory.CONVERSATION_ID, id) }
                .call().content()
        }
        ctx.note("두 번째 호출의 프롬프트를 펼쳐 보면 첫 턴이 그대로 실려 있습니다")
        ctx.note("이어지는 대화처럼 보이는 채팅 서비스도 실은 매번 이렇게 다시 보냅니다. 그 다시 보내기가 단기 기억이고, 한 번에 실을 수 있는 만큼(컨텍스트 창)이 한계입니다")
        return ctx.result("1턴 — $first\n\n2턴 — $second")
    }
}
