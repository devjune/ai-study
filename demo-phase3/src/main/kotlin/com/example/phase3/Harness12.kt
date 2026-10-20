package com.example.phase3

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.skills.discovery.discoverSkills
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import kotlinx.coroutines.runBlocking
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Path

/**
 * 슬라이드 12 Demo의 미니 하네스 — 입력 루프 하나에 전담 에이전트 둘.
 *
 * qwen3:4b는 성격이 다른 도구 둘(일정 조회, 파일 읽기)을 한 에이전트에 주면
 * 프롬프트의 우선순위 지시를 무시하고 엉뚱한 도구를 짚는다. 그래서 요청을
 * dispatchRouter 전략으로 갈라 역할이 하나뿐인 에이전트에 넘긴다.
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
        // qwen3:4b가 저장소의 긴 절대 경로를 복사하다 글자를 틀려서 짧은 경로로 복사해 쓴다.
        val skillsRoot = File("/tmp/koog-skills").also {
            it.deleteRecursively()
            File("src/main/resources/skills").copyRecursively(it)
        }.absolutePath
        val skills = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(skillsRoot))
        val skillsPrompt = generateSkillsPrompt(skills, SkillsPromptFormat.XML)
        // 체크포인트는 재시작 뒤에도 남아야 하므로 지우지 않는다.
        val checkpoints = JVMFilePersistenceStorageProvider(Path.of("/tmp/koog-checkpoints"))

        fun skillAgent() = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "비서다. 아래 스킬 카탈로그가 있다. 요청이 스킬에 해당하면 " +
                "그 스킬의 location 경로를 한 글자도 바꾸지 말고 그대로 읽기 도구에 넘겨 " +
                "본문을 읽고, 지침을 따라 답한다. 본문을 읽기 전에는 답하지 않는다.\n" + skillsPrompt,
            temperature = 0.0,
            toolRegistry = ToolRegistry { tool(ReadFileTool(JVMFileSystemProvider.ReadOnly)) },
        ) {
            install(EventHandler, trace("skill"))
        }

        // 일정은 프로세스가 사는 동안 유지된다. 요청마다 새로 만드는 에이전트가 같은 캘린더를 쥔다.
        val calendar = CalendarToolSet()

        fun assistantAgent() = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "비서다. 짧게 답한다. " +
                "일정 질문에는 schedules 도구를, 일정 추가에는 add 도구를 호출하고 그 결과만 답한다.",
            temperature = 0.0,
            toolRegistry = ToolRegistry { tools(calendar) },
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
            }
            install(Persistence) {
                storage = checkpoints
                enableAutomaticPersistence = true
            }
            install(EventHandler, trace("assistant"))
        }

        // 스킬 에이전트는 이력이 없다. 이력을 주면 4B가 파일을 안 읽고 앞 대화 말투를 따라간다.
        // 대신 콜론 뒤에 바꿀 문장이 없는 요청에는 직전 비서 답변을 원문으로 붙여
        // "방금 그 일정을 판교 사투리로" 같은 요청이 되게 한다.
        suspend fun withLastAnswer(input: String): String {
            if (':' in input) return input
            val last = historyProvider.load("demo").lastOrNull { it is Message.Assistant }?.textContent()
            return if (last == null) input else "$input\n\n[판교 사투리로 바꿀 원문]\n$last"
        }

        val terminal = TerminalBuilder.builder().system(true).build()
        val reader = LineReaderBuilder.builder().terminal(terminal).build()
        // 비서 대화는 세션 하나로 이어진다. 스킬 요청만 기억 없는 전담 에이전트가 받는다.
        val router = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = dispatchRouter(
                askSkill = { skillAgent().run(withLastAnswer(it)) },
                askAssistant = { assistantAgent().run(it, "demo") },
            ),
        ) {
            install(EventHandler, trace("dispatch", nodes = true))
        }
        // 세션 "demo"는 챗 메모리의 대화 키이자 체크포인트의 실행 키다.
        // 완료 표시 없는 체크포인트가 남아 있으면 그 노드 다음부터 마저 돌린다.
        checkpoints.getLatestCheckpoint("demo")?.takeUnless { it.isTombstone() }?.let {
            val node = it.graphProperties?.nodePath?.substringAfterLast('/')
            traceLine("persistence", "중단된 실행 발견, $node 다음부터 이어감")
            println(assistantAgent().run("", "demo"))
        }
        while (true) {
            val line = try {
                reader.readLine("❯ ")
            } catch (e: UserInterruptException) {
                break
            } catch (e: EndOfFileException) {
                break
            }
            if (line.isBlank()) continue
            if (line == "exit") break
            println(router.run(line))
        }
        terminal.close()
    }
}
