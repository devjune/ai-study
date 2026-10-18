package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.skills.discovery.discoverSkills
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File

/**
 * 슬라이드 12 Demo의 미니 하네스 — 입력 루프 하나에 전담 에이전트 둘.
 *
 * qwen3:4b는 성격이 다른 도구 둘(일정 조회, 파일 읽기)을 한 에이전트에 주면
 * 프롬프트의 우선순위 지시를 무시하고 엉뚱한 도구를 짚는다. 그래서 요청을
 * 트리거 단어로 갈라 역할이 하나뿐인 에이전트에 넘긴다.
 *
 *   ./gradlew bootRun --args='--spring.profiles.active=harness --spring.main.web-application-type=none'
 */
@Component
@Profile("harness")
class Harness12(private val promptExecutor: PromptExecutor) : CommandLineRunner {

    override fun run(vararg args: String?) = runBlocking {
        // qwen3:4b가 저장소의 긴 절대 경로를 복사하다 글자를 틀려서 짧은 경로로 복사해 쓴다.
        val skillsRoot = File("/tmp/koog-skills").also {
            it.deleteRecursively()
            File("src/main/resources/skills").copyRecursively(it)
        }.absolutePath
        val skills = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(skillsRoot))
        val skillsPrompt = generateSkillsPrompt(skills, SkillsPromptFormat.XML)

        fun skillAgent() = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "비서다. 아래 스킬 카탈로그가 있다. 요청이 스킬에 해당하면 " +
                "그 스킬의 location 경로를 한 글자도 바꾸지 말고 그대로 읽기 도구에 넘겨 " +
                "본문을 읽고, 지침을 따라 답한다.\n" + skillsPrompt,
            temperature = 0.0,
            toolRegistry = ToolRegistry { tool(ReadFileTool(JVMFileSystemProvider.ReadOnly)) },
        )

        fun assistantAgent() = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "비서다. 짧게 답한다. 일정 질문에는 schedules 도구를 호출하고 그 결과만 답한다.",
            temperature = 0.0,
            toolRegistry = ToolRegistry { tools(CalendarToolSet()) },
        )

        while (true) {
            print("❯ ")
            val line = readLine() ?: break
            if (line.isBlank()) continue
            if (line == "exit") break
            val agent = if ("판교 사투리" in line) skillAgent() else assistantAgent()
            println(agent.run(line))
        }
    }
}
