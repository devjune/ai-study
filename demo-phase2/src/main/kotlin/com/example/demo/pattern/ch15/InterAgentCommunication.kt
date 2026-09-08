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
        summary = "상대의 함수가 아니라 상대의 신분증을 읽고 일을 통째로 맡깁니다. 안에서 무엇을 하는지는 몰라도 됩니다.",
        code = """
            // ① 상대의 신분증을 읽는다 — 약속된 자리에 걸려 있다
            val card = rest.get().uri("/.well-known/agent.json").retrieve().body(JsonNode::class.java)
            card.at("/skills/0/id")        // "order_status" — 할 수 있는 일

            // ② 함수가 아니라 문장을 넘긴다. 봉투는 JSON-RPC
            {"jsonrpc":"2.0","id":1,"method":"message/send",
             "params":{"message":{"role":"user","parts":[{"kind":"text","text":"..."}]}}}

            // ③ 돌아오는 것은 값이 아니라 작업(task)이다
            {"result":{"id":"task-1a2b","status":{"state":"completed"},
                       "history":[{"state":"submitted"},{"state":"working"},{"state":"completed"}],
                       "artifacts":[{"parts":[{"kind":"text","text":"..."}]}]}}
        """.trimIndent(),
        diagram = """
            flowchart LR
                C[클라이언트 에이전트] -.① 신분증 조회.-> D["/.well-known/agent.json"]
                C -->|② 문장을 위임| R((원격 에이전트))
                R -->|③ 작업 상태 + 결과물| C
                R -.속은 안 보인다.-> X[모델 · 도구]
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
            rest.post().uri("/a2a").header("Content-Type", "application/json")
                .body(body).retrieve().body(JsonNode::class.java)
        }.getOrNull()?.path("result") ?: return ctx.result("위임에 실패했습니다", ok = false)

        // ③ 값이 아니라 작업이 돌아온다. 상태가 바뀌며 진행된다
        val states = task.path("history").joinToString(" → ") { it.path("state").asString() }
        ctx.note("작업 ${task.path("id").asString()} — $states")
        ctx.note("타임라인이 비어 있습니다 — 모델 호출은 상대 안에서 일어났고, 클라이언트는 그것을 볼 수 없습니다")
        ctx.note("10장 MCP 는 함수 이름을 알고 불렀습니다. 여기서는 문장만 넘겼습니다")
        ctx.note("화면에서 고른 모델도 적용되지 않습니다 — 무슨 모델을 쓸지는 상대가 정합니다")

        return ctx.result(task.at("/artifacts/0/parts/0/text").asString())
    }
}
