package com.example.demo.ch07_observability

import com.example.demo.support.ChatRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관측성 — 응답 메타데이터에서 토큰 사용량을 읽는다.
 * content() 대신 chatResponse() 로 받아야 메타데이터에 접근할 수 있다.
 * 문서 "핵심 기능 9. 관측성" 예제에 대응.
 */
@RestController
@RequestMapping("/api/observability")
class ObservabilityController(builder: ChatClient.Builder) {

    private val chatClient = builder.build()

    @PostMapping("/chat")
    fun chat(@RequestBody req: ChatRequest): UsageReply {
        val response = chatClient.prompt().user(req.message).call().chatResponse()
            ?: error("Model returned no response")
        val usage = response.metadata.usage
        return UsageReply(
            reply = response.result?.output?.text,
            promptTokens = usage.promptTokens,
            completionTokens = usage.completionTokens,
            totalTokens = usage.totalTokens,
        )
    }
}

/** 응답 + 토큰 사용량. 화면이 필드 이름 그대로 표시한다. */
data class UsageReply(
    val reply: String?,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
