package com.example.demo.pattern.ch18

import com.example.demo.pattern.*
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.springframework.stereotype.Component

@Component
class Guardrails : PatternRunner {
    override val spec = PatternSpec(
        18, "가드레일", "Guardrails",
        summary = "자율성이 커질수록 예측 못 한 출력이 나올 위험도 커집니다. 입력과 출력을 검사해 범위를 제한합니다.",
        code = """
            // BaseAdvisor — before / after / getOrder 셋만 구현하면 된다
            class MaskingAdvisor : BaseAdvisor {

                // 모델로 나가기 직전. 프롬프트를 고쳐서 돌려준다
                override fun before(req: ChatClientRequest, chain: AdvisorChain): ChatClientRequest =
                    req.mutate().prompt(마스킹(req.prompt())).build()

                // 모델에서 돌아온 직후. 응답을 검사하거나 바꿔치기한다
                override fun after(res: ChatClientResponse, chain: AdvisorChain): ChatClientResponse =
                    if (금칙어(res)) 거절응답() else res

                // 작을수록 먼저 실행된다. ToolCallingAdvisor 는 Integer.MIN_VALUE + 300
                override fun getOrder(): Int = 1000
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[프롬프트] --> B[before<br/>카드번호 마스킹]
                B --> M((모델))
                M --> A[after<br/>금칙어 검사]
                A --> R[응답]
        """.trimIndent(),
        defaultInput = "내 카드번호는 1234-5678-9012-3456 인데 환불해줘",
        model = "granite4:micro",
    )

    override fun run(ctx: RunContext): RunResult {
        val guard = MaskingAdvisor()
        val answer = ctx.step("마스킹 후 호출") { it.user(ctx.input).advisors(guard).call().content() }
        ctx.note("원본 — ${guard.original}")
        ctx.note("모델에 나간 것 — ${guard.masked}")
        ctx.note("위 타임라인의 프롬프트를 펼쳐 보면 카드번호가 없습니다")
        return ctx.result(answer)
    }
}

/** 18장 가드레일 — 나가는 프롬프트를 가로채 마스킹한다. */
class MaskingAdvisor : BaseAdvisor {
    var original: String? = null
        private set
    var masked: String? = null
        private set

    override fun before(request: ChatClientRequest, chain: AdvisorChain): ChatClientRequest {
        val text = request.prompt().userMessage.text ?: return request
        original = text
        val hidden = CARD.replace(text, "****-****-****-****")
        masked = hidden
        if (hidden == text) return request
        return request.mutate()
            .prompt(request.prompt().augmentUserMessage { it.mutate().text(hidden).build() })
            .build()
    }

    override fun after(response: ChatClientResponse, chain: AdvisorChain) = response
    override fun getOrder(): Int = GUARDRAIL_ORDER

    private companion object {
        val CARD = Regex("""\d{4}-\d{4}-\d{4}-\d{4}""")
    }
}
