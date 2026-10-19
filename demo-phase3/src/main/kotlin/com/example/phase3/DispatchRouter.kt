package com.example.phase3

import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy

/**
 * 슬라이드 10의 라우팅 전략. 판교 사투리 요청은 스킬 담당에게, 나머지는 비서에게 보낸다.
 * 담당을 함수로 받아서 테스트는 LLM 없이 분기만 검증한다.
 */
fun dispatchRouter(
    askSkill: suspend (String) -> String,
    askAssistant: suspend (String) -> String,
) = strategy<String, String>("dispatch") {
    val skill by node<String, String>("skill") { askSkill(it) }
    val assistant by node<String, String>("assistant") { askAssistant(it) }

    edge(nodeStart forwardTo skill onCondition { "판교 사투리" in it })
    edge(nodeStart forwardTo assistant onCondition { true })
    edge(skill forwardTo nodeFinish)
    edge(assistant forwardTo nodeFinish)
}
