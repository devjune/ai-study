package com.example.phase3

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Path

/**
 * Demo 슬라이드의 미니 하네스: 입력 루프 하나에 전담 에이전트 둘(DemoAgents).
 *
 *   ./gradlew bootRun --args='--spring.profiles.active=harness'
 */
@Component
@Profile("harness")
class Harness12(
    private val promptExecutor: PromptExecutor,
    private val historyProvider: ChatHistoryProvider,
) : CommandLineRunner {

    override fun run(vararg args: String?) = runBlocking {
        // 둘 다 재시작 뒤에도 남아야 하므로 지우지 않는다. data/는 git이 무시한다.
        val calendar = CalendarToolSet(Path.of("data/koog-calendar.txt"))
        val checkpoints = JVMFilePersistenceStorageProvider(Path.of("data/koog-checkpoints"))
        val agents = DemoAgents.create(promptExecutor, historyProvider, calendar, checkpoints)

        resumeIfInterrupted(checkpoints, agents)
        val router = agents.router()
        Repl().run { router.run(it) }
    }

    // 세션 "demo"는 챗 메모리의 대화 키이자 체크포인트의 실행 키다.
    // 완료 표시 없는 체크포인트가 남아 있으면 그 노드 다음부터 마저 돌린다.
    private suspend fun resumeIfInterrupted(checkpoints: JVMFilePersistenceStorageProvider, agents: DemoAgents) {
        checkpoints.getLatestCheckpoint("demo")?.takeUnless { it.isTombstone() }?.let {
            val node = it.graphProperties?.nodePath?.substringAfterLast('/')
            traceLine("persistence", "중단된 실행 발견, $node 다음부터 이어감")
            println(agents.assistantAgent().run("", "demo"))
        }
    }
}
