package com.example.phase3

import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONPrimitive

/**
 * 하네스가 터미널에 흐리게 찍는 이벤트 줄. 분기, 모델 호출, 도구 호출이 보이게 한다.
 * nodes = true면 노드 진입도 찍는다. 라우터처럼 노드 이름이 곧 분기 결과인 전략에 쓴다.
 */
fun trace(label: String, nodes: Boolean = false): EventHandlerConfig.() -> Unit = {
    if (nodes) {
        onNodeExecutionStarting { ctx ->
            if (!ctx.node.name.startsWith("__")) line(label, "→ ${ctx.node.name}")
        }
    }
    onLLMCallStarting { ctx ->
        line(label, "LLM 호출 (메시지 ${ctx.prompt.messages.size}개)")
    }
    onToolCallCompleted { ctx ->
        val args = ctx.toolArgs.entries.entries.joinToString(", ") { (k, v) -> "$k=${v.text()}" }
        line(label, "${ctx.toolName}($args) → ${ctx.toolResult?.summary().orEmpty()}")
    }
}

private fun JSONElement.text() = (this as? JSONPrimitive)?.content ?: toString()

private fun JSONElement.summary() =
    if (this is JSONPrimitive) content.replace('\n', ' ') else "%,d자".format(toString().length)

private fun line(label: String, text: String) =
    println("\u001b[2m  ${label.padEnd(10)}$text\u001b[0m")
