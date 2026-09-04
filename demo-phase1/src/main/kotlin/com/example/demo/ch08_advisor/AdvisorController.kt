package com.example.demo.ch08_advisor

import com.example.demo.support.ChatRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 커스텀 Advisor 데모.
 * 직접 만든 PiiMaskingAdvisor 와 기본 제공 SimpleLoggerAdvisor 를 함께 건다.
 * order 가 작은 쪽이 먼저 실행된다.
 */
@RestController
@RequestMapping("/api/advisor")
class AdvisorController(builder: ChatClient.Builder) {

    private val chatClient = builder.build()

    @PostMapping("/chat")
    fun chat(@RequestBody req: ChatRequest): MaskedReply {
        val response = chatClient.prompt()
            .advisors(
                PiiMaskingAdvisor(order = 0),          // 직접 만든 것
                SimpleLoggerAdvisor.builder().build(), // 기본 제공 (로그로 확인)
            )
            .user(req.message)
            .call()
            .chatClientResponse()
            ?: error("Model returned no response")

        return MaskedReply(
            original = req.message,
            // advisor 가 컨텍스트에 남긴 값. 마스킹이 없었다면 null.
            sentToModel = response.context()[PiiMaskingAdvisor.MASKED_PROMPT] as String?,
            reply = response.chatResponse()?.result?.output?.text,
        )
    }
}

/** 마스킹 결과를 함께 돌려줘, advisor 가 실제로 프롬프트를 바꿨는지 보여준다. */
data class MaskedReply(
    val original: String,
    val sentToModel: String?,
    val reply: String?,
)
