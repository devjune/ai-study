package com.example.demo.ch05_rag

import com.example.demo.support.ChatReply
import com.example.demo.support.ChatRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * RAG — QuestionAnswerAdvisor 하나로 검색→증강→생성.
 * "우리 환불 정책이 뭐야?" 처럼 시드 문서에 근거해 답한다.
 * 문서 "핵심 기능 5. RAG" 예제에 대응.
 */
@RestController
@RequestMapping("/api/rag")
class RagController(builder: ChatClient.Builder, private val vectorStore: VectorStore) {

    private val chatClient = builder.build()

    @PostMapping("/chat")
    fun chat(@RequestBody req: ChatRequest): ChatReply =
        ChatReply(
            chatClient.prompt()
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .user(req.message)
                .call()
                .content(),
        )
}
