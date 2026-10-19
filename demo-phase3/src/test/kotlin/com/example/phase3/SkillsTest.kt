package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.skills.discovery.discoverSkills
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class SkillsTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    // qwen3:4b가 저장소의 긴 절대 경로를 복사하다 글자를 틀려서(demo-phase3 → demo-phase-3),
    // 스킬 폴더를 짧은 경로로 복사해 쓴다.
    val skillsRoot = File("/tmp/koog-skills").also {
        it.deleteRecursively()
        File("src/main/resources/skills").copyRecursively(it)
    }.absolutePath

    test("스킬 폴더에서 판교 사투리 스킬을 찾는다") {
        val skills = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(skillsRoot))
        skills shouldHaveSize 1
        val prompt = generateSkillsPrompt(skills, SkillsPromptFormat.XML)
        println("SkillsTest 카탈로그: $prompt")
        prompt shouldContain "pangyo-dialect"
        prompt shouldContain "판교 사투리"
    }

    test("에이전트가 스킬을 읽어 판교 사투리로 바꾼다") {
        val skills = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(skillsRoot))
        val skillsPrompt = generateSkillsPrompt(skills, SkillsPromptFormat.XML)
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "비서다. 아래 스킬 카탈로그가 있다. 요청이 스킬에 해당하면 " +
                "그 스킬의 location 경로를 한 글자도 바꾸지 말고 그대로 읽기 도구에 넘겨 본문을 읽고, 지침을 따라 답한다.\n" + skillsPrompt,
            temperature = 0.0,
            toolRegistry = ToolRegistry {
                tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            },
        )
        val result = agent.run("판교 사투리 11로 바꿔 줘: 내일 회식 오세요")
        println("SkillsTest 판교 사투리 11: $result")
        result.shouldNotBeBlank()
        result shouldNotContain "찾을 수 없"
    }
})
