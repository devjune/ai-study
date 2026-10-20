@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.phase3

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
 * 비서 전략. 기본 전략의 도구 왕복에 검증 노드를 하나 더했다.
 * 추가한 일정이 같은 시각의 다른 일정과 겹치면 도구 실행 전 체크포인트로 롤백한다.
 * 롤백하면 Persistence가 RollbackToolRegistry에 등록된 반대 도구(remove)를 같은 인자로 부른다.
 */
fun assistantStrategy(calendar: CalendarToolSet, checkpoints: PersistenceStorageProvider<*>): ai.koog.agents.core.agent.entity.AIAgentGraphStrategy<String, String> {
    var conflictWith: String? = null
    return strategy<String, String>("assistant") {
        val request by nodeLLMRequest("request")
        val execute by nodeExecuteTools("execute")
        val verify by node<ReceivedToolResults, ReceivedToolResults>("verify") { results: ReceivedToolResults ->
            conflictWith = calendar.conflictOfLastAdded()
            val conflict = conflictWith
            if (conflict != null) {
                val added = calendar.lastAdded!!
                traceLine("assistant", "검증: ${added.time} ${added.title}이 ${conflict}와 겹침 → 도구 실행 전 체크포인트로 롤백")
                val before = checkpoints.getCheckpoints(runId)
                    .filter { it.graphProperties?.nodePath?.endsWith("/request") == true }
                    .maxByOrNull { it.createdAt }
                if (before != null) persistence().rollbackToCheckpoint(before.checkpointId, this)
                traceLine("assistant", "되돌린 뒤 ${added.day} 일정: ${calendar.schedules(added.day)}")
            }
            results
        }
        val report by nodeLLMSendToolResults("report")
        val reject by node<ReceivedToolResults, String>("reject") { _: ReceivedToolResults ->
            val added = calendar.lastAdded!!
            "${added.day} ${added.time}은 ${conflictWith}과 겹쳐 ${added.title}을 추가하지 않았습니다."
        }

        edge(nodeStart forwardTo request)
        edge(request forwardTo execute onToolCalls { true })
        edge(request forwardTo nodeFinish onTextMessage { true })
        edge(execute forwardTo verify)
        edge(verify forwardTo reject onCondition { conflictWith != null })
        edge(verify forwardTo report onCondition { true })
        edge(report forwardTo execute onToolCalls { true })
        edge(report forwardTo nodeFinish onTextMessage { true })
        edge(reject forwardTo nodeFinish)
    }
}
