package com.example.phase3

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.skills.discovery.discoverSkills
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import java.io.File

/**
 * 데모의 에이전트 셋: 요청을 가르는 라우터, 캘린더만 가진 비서, 스킬만 가진 스킬 에이전트.
 *
 * qwen3:4b는 성격이 다른 도구 둘(일정 조회, 파일 읽기)을 한 에이전트에 주면 프롬프트의
 * 우선순위 지시를 무시하고 엉뚱한 도구를 짚는다. 그래서 담당을 나누고 라우터가 요청을 보낸다.
 * 비서와 스킬 에이전트는 요청마다 새로 만들고, 캘린더·대화 이력·체크포인트는 밖에서 받아 공유한다.
 */
class DemoAgents private constructor(
    private val promptExecutor: PromptExecutor,
    private val historyProvider: ChatHistoryProvider,
    private val calendar: CalendarToolSet,
    private val checkpoints: PersistenceStorageProvider<*>,
    private val skillsPrompt: String,
) {

    /** 비서 대화는 세션 하나로 이어진다. 스킬 요청만 기억 없는 전담 에이전트가 받는다. */
    fun router() = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        strategy = dispatchRouter(
            askSkill = { skillAgent().run(withLastAnswer(it)) },
            askAssistant = { assistantAgent().run(it, "demo") },
        ),
    ) {
        install(EventHandler, trace("dispatch", nodes = true))
    }

    fun assistantAgent() = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        systemPrompt = "비서다. 짧게 답한다. " +
            "일정 질문에는 날짜마다 schedules 도구를 호출해야 한다. 예: '모레 일정은?' → schedules(day=모레). " +
            "도구 결과가 없음이면 없다고 답한다. 이력의 일정을 다른 날짜에 쓰지 않는다. 일정 추가에는 add 도구를 호출한다.",
        temperature = 0.0,
        toolRegistry = ToolRegistry { tools(calendar) },
        strategy = assistantStrategy(calendar, checkpoints),
    ) {
        install(ChatMemory) {
            chatHistoryProvider = historyProvider
        }
        install(Persistence) {
            storage = checkpoints
            enableAutomaticPersistence = true
            rollbackToolRegistry = RollbackToolRegistry.builder()
                .registerRollback(calendar::add, calendar::remove).build()
        }
        install(EventHandler, trace("assistant"))
    }

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

    // 스킬 에이전트는 이력이 없다. 이력을 주면 4B가 파일을 안 읽고 앞 대화 말투를 따라간다.
    // 대신 콜론 뒤에 바꿀 문장이 없는 요청에는 직전 비서 답변을 원문으로 붙여
    // "방금 그 일정을 판교 사투리로" 같은 요청이 되게 한다.
    private suspend fun withLastAnswer(input: String): String {
        if (':' in input) return input
        val last = historyProvider.load("demo").lastOrNull { it is Message.Assistant }?.textContent()
        return if (last == null) input else "$input\n\n[판교 사투리로 바꿀 원문]\n$last"
    }

    companion object {
        suspend fun create(
            promptExecutor: PromptExecutor,
            historyProvider: ChatHistoryProvider,
            calendar: CalendarToolSet,
            checkpoints: PersistenceStorageProvider<*>,
        ) = DemoAgents(promptExecutor, historyProvider, calendar, checkpoints, skillsCatalog())

        // qwen3:4b가 저장소의 긴 절대 경로를 복사하다 글자를 틀려서 짧은 경로로 복사해 쓴다.
        private suspend fun skillsCatalog(): String {
            val root = File("/tmp/koog-skills").also {
                it.deleteRecursively()
                File("src/main/resources/skills").copyRecursively(it)
            }
            val skills = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(root.absolutePath))
            return generateSkillsPrompt(skills, SkillsPromptFormat.XML)
        }
    }
}
