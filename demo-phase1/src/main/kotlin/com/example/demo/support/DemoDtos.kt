package com.example.demo.support

/** 단순 채팅 요청. 대부분의 데모가 공용으로 쓴다. */
data class ChatRequest(val message: String)

/** 대화 ID를 포함한 요청 (ChatMemory 데모용) */
data class ConversationRequest(val conversationId: String, val message: String)

/**
 * 텍스트 응답. 화면(index.html)이 reply 필드를 읽는다.
 * ChatClient.content() 가 null 을 반환할 수 있어 nullable 로 둔다.
 */
data class ChatReply(val reply: String?)
