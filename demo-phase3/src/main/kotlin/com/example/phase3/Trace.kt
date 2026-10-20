package com.example.phase3

import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 하네스가 터미널에 흐리게 찍는 실행 흐름. 모델 호출마다 무엇을 결정했는지(도구 호출인지 답인지),
 * 도구가 무엇을 돌려줬는지, 이력이 몇 개 실렸는지, 얼마나 걸렸는지가 보이게 한다.
 * nodes = true면 노드 진입만 찍는다. 라우터처럼 노드 이름이 곧 분기 결과인 전략에 쓴다.
 */
fun trace(label: String, nodes: Boolean = false): EventHandlerConfig.() -> Unit = {
    var runStart = 0L
    var callStart = 0L
    var calls = 0
    var messages = 0
    var history = 0

    if (nodes) {
        onNodeExecutionStarting { ctx ->
            if (!ctx.node.name.startsWith("__")) traceLine(label, "→ ${ctx.node.name}")
        }
    } else {
        onAgentStarting { runStart = System.nanoTime(); calls = 0 }
        onLLMCallStarting { ctx ->
            callStart = System.nanoTime()
            calls++
            messages = ctx.prompt.messages.size
            history = if (calls == 1) ctx.prompt.messages.history() else 0
        }
        onLLMCallCompleted { ctx ->
            val toolCalls = ctx.response?.parts.orEmpty().filterIsInstance<MessagePart.Tool.Call>()
            val decision = if (toolCalls.isEmpty()) "답"
            else "도구 호출 " + toolCalls.joinToString(", ") { "${it.tool}(${it.argsJson.flat()})" }
            val loaded = if (history > 0) " (이력 ${history}개)" else ""
            traceLine(label, "LLM #$calls 메시지 ${messages}개$loaded → $decision  ${elapsed(callStart)}")
        }
        onToolCallCompleted { ctx ->
            traceLine(label, "${ctx.toolName} → ${ctx.toolResult?.summary().orEmpty()}")
        }
        onAgentCompleted { traceLine(label, "완료 ${elapsed(runStart)}") }
    }
}

/** 시스템 프롬프트와 이번 질문을 뺀 앞 대화 수. 체크포인트에서 이어 받은 실행(도구 메시지 포함)은 세지 않는다. */
private fun List<Message>.history(): Int =
    if (any { m -> m.parts.any { it is MessagePart.Tool } }) 0
    else count { it is Message.User || it is Message.Assistant } - 1

private fun JsonObject.flat() = entries.joinToString(", ") { (k, v) -> "$k=${(v.jsonPrimitive.contentOrNull ?: v.toString()).tail()}" }

/** 긴 인자 값(파일 경로 등)은 뒤쪽만 보인다. */
private fun String.tail() = if (length > 28) "…" + takeLast(24) else this

private fun JSONElement.summary() =
    if (this is JSONPrimitive) content.replace('\n', ' ') else "%,d자".format(toString().length)

private fun elapsed(start: Long) = "%.1fs".format((System.nanoTime() - start) / 1e9)

fun traceLine(label: String, text: String) =
    println("\u001b[2m  ${label.padEnd(12)}$text\u001b[0m")
