package com.example.demo.pattern.ch11

import com.example.demo.pattern.*
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component

/** 목표 하나에 대한 점검 결과. 이 목록이 곧 진행 상황판이다 */
data class Check(val goal: String, val met: Boolean, val why: String)

@Component
class GoalSetting(private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        11, "목표 설정과 모니터링", "Goal Setting and Monitoring",
        summary = "목표가 없으면 잘 됐는지 알 길이 없습니다. 측정할 수 있는 목표를 미리 정해 두고, 다 채울 때까지 대조하며 반복합니다.",
        code = """
            // 사람이 정해 둔 목표. 모델이 만드는 것이 아니라 측정할 수 있게 미리 적어 둔다
            val 목표들 = listOf(
                "환불 가능 기한을 숫자로 밝힌다",
                "단순 변심일 때 배송비를 누가 얼마 부담하는지 밝힌다",
                "교환 조건도 함께 안내한다",
            )

            // 목표마다 충족 여부를 따로 받는다. 이 목록이 곧 진행 상황판이다
            data class Check(val goal: String, val met: Boolean, val why: String)

            var 답 = ""
            var 미달 = emptyList<Check>()

            for (round in 1..2) {                       // 원서는 최대 5회
                답 = chatClient.prompt()
                    .user(질문 + 규정 + 미달.joinToString { it.goal })   // 못 채운 목표만 다시 싣는다
                    .call().content()

                val 점검 = chatClient.prompt().system(MONITOR)
                    .user(목표들.joinToString() + 답)
                    .call()
                    .entity(object : ParameterizedTypeReference<List<Check>>() {})

                미달 = 점검.filterNot { it.met }
                if (미달.isEmpty()) break               // 목표를 다 채웠으면 끝
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                G[목표 목록<br/>사람이 정한 것] --> M1((모델<br/>작업))
                M1 --> M2((모델<br/>기준 대조))
                M2 --> C{모두 충족?}
                C -->|예| OK[끝]
                C -->|아니오, 미달 목표만| M1
        """.trimIndent(),
        defaultInput = "환불 규정을 고객에게 안내해줘",
        model = "qwen3:4b-instruct",
        maxTokens = 160,
    )

    /** 사람이 정해 둔 목표. 이 셋을 다 채워야 끝난다 */
    private val goals = listOf(
        "환불 가능 기한을 숫자로 밝힌다",
        "단순 변심일 때 배송비를 누가 얼마 부담하는지 밝힌다",
        "교환 조건도 함께 안내한다",
    )

    private val monitor = """
        답이 아래 목표를 각각 채웠는지 판정한다. 목표 수만큼 항목을 낸다.
        goal 에는 목표를 8자 안으로 줄여 적고, met 에는 true 또는 false 를, why 에는 판단 근거를 15자 이내로 적는다.
    """.trimIndent()

    override fun run(ctx: RunContext): RunResult {
        val rules = Docs.POLICIES.joinToString("\n") { "- ${it.text}" }
        ctx.note("목표 ${goals.size}개 — " + goals.joinToString(" / "))

        var answer = ""
        var unmet = goals

        for (round in 1..2) {          // 원서는 최대 5회. 시연이라 둘로 묶었다
            val todo = if (round == 1) "" else "\n아직 못 채운 목표: " + unmet.joinToString()
            answer = ctx.step("①-$round 작업") {
                it.system("고객에게 보낼 안내문만 쓴다. 인사말·머리말·마크다운 장식 없이 세 문장 안으로.")
                    .user("규정:\n$rules\n\n${ctx.input}$todo").call().content()
            }.orEmpty()

            val checks = runCatching {
                ctx.step("②-$round 기준 대조") {
                    it.system(monitor)
                        .options(ChatOptions.builder().model(ctx.model).maxTokens(180))
                        .user("목표:\n" + goals.joinToString("\n") { g -> "- $g" } + "\n\n답: $answer")
                        .call().entity(object : ParameterizedTypeReference<List<Check>>() {})
                }
            }.getOrNull().orEmpty()

            if (checks.isEmpty()) { ctx.note("판정 형식을 못 맞췄습니다"); break }
            checks.forEach { ctx.note("${if (it.met) "달성" else "미달"} · ${it.goal} — ${it.why.take(30)}") }

            unmet = checks.filterNot { it.met }.map { it.goal }
            if (unmet.isEmpty()) { ctx.note("${round}회차에 목표를 모두 채웠습니다"); break }
            ctx.note("남은 목표 ${unmet.size}개를 다음 회차 프롬프트에 싣습니다")
        }

        ctx.note("4장은 답이 더 나아졌는지 보고, 여기는 미리 정한 기준을 채웠는지 봅니다")
        return ctx.result(answer)
    }
}
