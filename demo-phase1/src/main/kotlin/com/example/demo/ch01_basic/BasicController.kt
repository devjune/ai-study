package com.example.demo.ch01_basic

import com.example.demo.support.ChatReply
import com.example.demo.support.ChatRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

/**
 * ChatClient 기본 — 동기 호출 / 스트리밍 / 시스템 프롬프트(페르소나).
 * 문서 "핵심 기능 1. ChatClient" 예제에 대응.
 */
@RestController
@RequestMapping("/api/basic")
class BasicController(builder: ChatClient.Builder) {

    private val chatClient = builder.build()

    /** 동기 호출: prompt().user().call().content() */
    @PostMapping("/chat")
    fun chat(@RequestBody req: ChatRequest): ChatReply =
        ChatReply(chatClient.prompt().user(req.message).call().content())

    /** 스트리밍: .stream().content() -> SSE 로 토큰을 흘려보냄 */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@RequestParam message: String): Flux<String> =
        chatClient.prompt().user(message).stream().content()

    /** 시스템 프롬프트(페르소나)를 호출 시점에 지정 */
    @PostMapping("/persona")
    fun persona(@RequestBody req: ChatRequest): ChatReply =
        ChatReply(
            chatClient.prompt()
                .system("너는 해적처럼 말하는 챗봇이다. 항상 해적 말투로 답하라.")
                .user(req.message)
                .call()
                .content(),
        )
}
