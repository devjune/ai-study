package com.example.demo.ch08_advisor

import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor

/**
 * 커스텀 Advisor — LLM 에 나가기 전에 카드번호를 마스킹하는 가드레일.
 *
 * BaseAdvisor 를 구현하면 before/after 만 채우면 된다.
 * - before: 모델로 나가는 요청을 가로챈다 (여기서 마스킹)
 * - after:  모델에서 온 응답을 가로챈다 (여기선 통과)
 *
 * HandlerInterceptor 의 preHandle/postHandle 과 같은 자리다.
 * 문서 "핵심 기능 8. Advisor 직접 만들기" 예제에 대응.
 */
class PiiMaskingAdvisor(private val order: Int = 0) : BaseAdvisor {

    companion object {
        /** 데모용 단순 패턴. 실제 가드레일은 더 정교해야 한다. */
        private val CARD_NUMBER = Regex("""\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b""")
        private const val MASK = "****-****-****-****"

        /** 마스킹된 프롬프트를 컨텍스트에 남겨 호출부가 확인할 수 있게 한다. */
        const val MASKED_PROMPT = "maskedPrompt"
    }

    override fun before(request: ChatClientRequest, chain: AdvisorChain): ChatClientRequest {
        val original = request.prompt().userMessage.text ?: return request
        val masked = CARD_NUMBER.replace(original, MASK)
        if (masked == original) return request

        return request.mutate()
            .prompt(
                request.prompt().augmentUserMessage { user ->
                    user.mutate().text(masked).build()
                },
            )
            .context(MASKED_PROMPT, masked)
            .build()
    }

    override fun after(response: ChatClientResponse, chain: AdvisorChain): ChatClientResponse =
        response

    override fun getOrder(): Int = order
}
