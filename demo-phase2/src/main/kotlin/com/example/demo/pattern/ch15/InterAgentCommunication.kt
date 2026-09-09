package com.example.demo.pattern.ch15

import com.example.demo.pattern.*
import tools.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 15장 — **클라이언트 에이전트** 쪽. 상대의 신분증을 읽고 일을 맡긴다.
 *
 * 이 클래스에는 모델도 도구도 없다. 그래서 타임라인이 비어 있는데, 그게 이 패턴의 요점이다.
 */
@Component
class InterAgentCommunication : PatternRunner {
    override val spec = PatternSpec(
        15, "에이전트 간 통신", "Inter-Agent Communication",
        summary = "MCP가 모델에 도구를 붙이는 규격이라면, A2A는 에이전트가 에이전트에게 일을 맡기는 개방형 규격입니다. 신분증을 읽고 문장을 넘기면 값이 아니라 작업이 돌아옵니다.",
        code = """
            // ① 신분증(Agent Card) — 함수 시그니처가 아니라 상대가 스스로 밝힌 것을 읽는다. 자리(.well-known)도 규격이 정한다
            val card = rest.get().uri("/.well-known/agent.json").retrieve().body(JsonNode::class.java)

            // ② 위임 — 함수 이름도 인자도 없다. 문장 하나를 규격 봉투(JSON-RPC)에 담아 넘긴다
            val task = rest.post().uri(card.path("url").asString()).body(
                ${'"'}${'"'}${'"'}
                {"jsonrpc":"2.0","id":1,"method":"message/send",
                 "params":{"message":{"role":"user","parts":[{"kind":"text","text":"A-1024 지금 어디쯤 왔어?"}]}}}
                ${'"'}${'"'}${'"'},
            ).retrieve().body(JsonNode::class.java)

            // ③ 값이 아니라 작업이 돌아온다 — submitted → working → completed. 안에서 뭘 했는지는 없다
            task.at("/result/artifacts/0/parts/0/text").asString()

            // 상대편이 열어 두는 것은 이 둘이 전부다. 이 데모에서는 이 앱 자신이 상대편도 맡는다
            @GetMapping("/.well-known/agent.json")   // 신분증
            @PostMapping("/a2a")                     // 일을 받는 자리
        """.trimIndent(),
        diagram = """
            flowchart LR
                C((클라이언트<br/>에이전트)) -.① 능력을 조회.-> D["/.well-known<br/>/agent.json"]
                C -->|② 작업 위임| R((원격 에이전트<br/>다른 프레임워크))
                R -->|③ 진행 상태·결과| C
                R -.MCP.-> T[도구 · 데이터]
        """.trimIndent(),
        defaultInput = "A-1024 지금 어디쯤 왔어?",
        model = PatternSpec.DEFAULT_MODEL,
    )

    private val rest = RestClient.create("http://localhost:8080")

    override fun run(ctx: RunContext): RunResult {
        // ① 신분증 — 상대가 누구고 뭘 할 수 있는지는 이 파일 하나로 안다
        val card: JsonNode = runCatching {
            rest.get().uri("/.well-known/agent.json").retrieve().body(JsonNode::class.java)
        }.getOrNull() ?: return ctx.result("신분증을 읽지 못했습니다", ok = false)

        val skill = card.path("skills").path(0)
        ctx.note("신분증 — ${card.path("name").asString()} · ${card.path("url").asString()}")
        ctx.note("할 수 있는 일 — ${skill.path("id").asString()} · ${skill.path("description").asString()}")

        // ② 위임 — 함수 이름도 인자도 없다. 문장 하나를 넘긴다
        val body = """
            {"jsonrpc":"2.0","id":1,"method":"message/send",
             "params":{"message":{"role":"user","parts":[{"kind":"text","text":"${ctx.input}"}]}}}
        """.trimIndent()
        val task: JsonNode = runCatching {
            rest.post().uri(card.path("url").asString()).header("Content-Type", "application/json")
                .body(body).retrieve().body(JsonNode::class.java)
        }.getOrNull()?.path("result") ?: return ctx.result("위임에 실패했습니다", ok = false)

        // ③ 값이 아니라 작업이 돌아온다. 상태가 바뀌며 진행된다
        val states = task.path("history").joinToString(" → ") { it.path("state").asString() }
        ctx.note("타임라인이 비어 있습니다 — 모델 호출은 상대 안에서 일어났고, 클라이언트는 그것을 볼 수 없습니다")
        ctx.note("10장 MCP 는 함수 이름을 알고 불렀습니다. 여기서는 문장만 넘겼습니다")
        ctx.note("화면에서 고른 모델도 적용되지 않습니다 — 무슨 모델을 쓸지는 상대가 정합니다")
        ctx.note("봉투는 A2A 규격 그대로입니다 — 신분증 자리, message/send, 작업 상태. 개방형 표준이라 상대가 다른 프레임워크로 만든 에이전트여도 이쪽 코드는 그대로입니다")
        ctx.note("이 데모에서는 상대편도 이 앱이 맡습니다. Spring AI 2.0.1 에는 A2A 구현이 없어 규격의 최소 조각을 직접 얹었습니다")

        // 답만 내지 않는다. 값이 아니라 작업이 돌아왔다는 것이 결과 화면에서 보여야 5장과 갈린다
        return ctx.result(
            "작업 ${task.path("id").asString()} — $states\n\n" +
                "결과물 — ${task.at("/artifacts/0/parts/0/text").asString()}",
        )
    }
}
