package com.example.demo.ch10_agent

import com.example.demo.support.ChatReply
import com.example.demo.support.ConversationRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 실제 조회 도구. §4의 WeatherTools 와 똑같은 모양. 데모라 고정값. */
class OrderTools {

    @Tool(description = "특정 사용자의 최근 주문 상태를 조회한다")
    fun getOrder(@ToolParam(description = "사용자 ID") userId: String): String =
        "$userId 의 최근 주문: 2026-07-14 결제, 배송 완료"
}

/**
 * 직접 만들어 보기 — 사내 문서 Q&A 에이전트.
 * 앞의 기능을 하나의 ChatClient(supportAgent)에 결합한다:
 * RAG(사내 문서 근거) + 대화 메모리(맥락 유지) + 실제 조회 도구(OrderTools).
 * 문서 "직접 만들어 보기 — 사내 문서 Q&A 에이전트" 예제에 대응.
 *
 * 메모리 Advisor 를 얹은 순간부터 대화 ID(CONVERSATION_ID)는 필수다(§3).
 */
@RestController
@RequestMapping("/api/agent")
class AgentController(private val supportAgent: ChatClient) {

    @PostMapping("/chat")
    fun chat(@RequestBody req: ConversationRequest): ChatReply =
        ChatReply(
            supportAgent.prompt()
                .user(req.message)
                .advisors { it.param(ChatMemory.CONVERSATION_ID, req.conversationId) }
                .call()
                .content(),
        )
}
