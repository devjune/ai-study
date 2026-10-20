@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.phase3

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.snapshot.feature.persistence
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider

/**
 * 비서 전략. 기본 도구 왕복에 검증 노드를 더했다.
 *
 * 추가한 일정이 같은 시각의 다른 일정과 겹치면 receive 뒤 체크포인트(도구 실행 전)로 롤백한다.
 * Persistence는 롤백할 때 체크포인트 뒤의 도구 호출(add)마다 등록된 반대 도구(remove)를
 * 같은 인자로 부르고, 프롬프트와 실행 지점을 receive 다음으로 되돌린다.
 * 그래서 receive의 첫 엣지가 겹침 여부를 보고 reject로 간다.
 */
fun assistantStrategy(calendar: CalendarToolSet, checkpoints: PersistenceStorageProvider<*>): AIAgentGraphStrategy<String, String> {
    var conflict: String? = null
    return strategy<String, String>("assistant") {
        val receive by node<String, String>("receive") { input: String -> input }
        val request by nodeLLMRequest("request")
        val execute by nodeExecuteTools("execute")
        val verify by node<ReceivedToolResults, ReceivedToolResults>("verify") { results: ReceivedToolResults ->
            if (results.toolResults.any { it.tool == "add" }) conflict = calendar.conflictOfLastAdded()
            if (conflict != null) {
                val added = calendar.lastAdded!!
                traceLine("assistant", "검증: ${added.time} ${added.title} 겹침 ($conflict) → 도구 실행 전 체크포인트로 롤백")
                val before = checkpoints.getCheckpoints(runId)
                    .filter { it.graphProperties?.nodePath?.endsWith("/receive") == true }
                    .maxBy { it.createdAt }
                persistence().rollbackToCheckpoint(before.checkpointId, this)
            }
            results
        }
        val report by nodeLLMSendToolResults("report")
        val reject by node<String, String>("reject") { _: String ->
            val added = calendar.lastAdded!!
            traceLine("assistant", "롤백 뒤 ${added.day} 일정: ${calendar.schedules(added.day)}")
            "${added.day} ${added.time}은 이미 ${conflict!!.substringAfter(' ')} 시간이라 ${added.title} 일정을 추가하지 않았습니다."
        }

        edge(nodeStart forwardTo receive)
        edge(receive forwardTo reject onCondition { conflict != null })
        edge(receive forwardTo request onCondition { true })
        edge(request forwardTo execute onToolCalls { true })
        edge(request forwardTo nodeFinish onTextMessage { true })
        edge(execute forwardTo verify)
        edge(verify forwardTo report)
        edge(report forwardTo execute onToolCalls { true })
        edge(report forwardTo nodeFinish onTextMessage { true })
        edge(reject forwardTo nodeFinish)
    }
}
