package com.example.demo.chat

import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor

/**
 * 모델에 실제로 나간 프롬프트를 붙잡아 두는 Advisor.
 *
 * RAG 가 문서를 끼워 넣고 메모리가 과거 대화를 붙이는 일은 전부 프롬프트 조립이다.
 * 로그로만 보면 "마법"처럼 남지만, 원문을 화면에 꺼내 놓으면 마법이 풀린다.
 * **그 원문을 꺼내는 수단이 다시 Spring AI 의 Advisor** 라는 점이 이 클래스의 요점이다.
 *
 * 순서가 중요하다. RAG·메모리 Advisor 보다 **뒤에** 서야 그들이 조립을 끝낸 최종
 * 프롬프트를 본다. [order] 를 크게 준다.
 */
class TraceAdvisor(private val order: Int = 1000) : BaseAdvisor {

    /** 모델이 받은 프롬프트 전문. 요청 하나당 인스턴스 하나라 공유되지 않는다. */
    var prompt: String? = null
        private set

    override fun before(request: ChatClientRequest, chain: AdvisorChain): ChatClientRequest {
        prompt = request.prompt().instructions.joinToString("\n\n") { message ->
            "[${message.messageType}]\n${message.text}"
        }
        return request
    }

    override fun after(response: ChatClientResponse, chain: AdvisorChain): ChatClientResponse =
        response

    override fun getOrder(): Int = order
}
