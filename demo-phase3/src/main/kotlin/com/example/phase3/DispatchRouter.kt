package com.example.phase3

import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy

/**
 * 슬라이드 10의 라우팅 전략. 요청을 스킬 담당과 비서 담당으로 가른다.
 * 하네스가 실제로 이 전략으로 분기한다.
 */
fun dispatchRouter() = strategy<String, String>("dispatch") {
    val skill by node<String, String>("skill") { "SKILL" }
    val assistant by node<String, String>("assistant") { "ASSISTANT" }

    edge(nodeStart forwardTo skill onCondition { "판교 사투리" in it })
    edge(nodeStart forwardTo assistant onCondition { true })
    edge(skill forwardTo nodeFinish)
    edge(assistant forwardTo nodeFinish)
}
