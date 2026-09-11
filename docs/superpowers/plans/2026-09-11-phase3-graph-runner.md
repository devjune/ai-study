# 전략 그래프 러너 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 발표 목업 "전략 그래프 러너"를 실물로 — 진짜 Koog 에이전트 실행이 SSE로 그래프를 점등시키는 demo-phase3 웹 데모.

**Architecture:** demo-phase3 main에 runner 패키지(시나리오 4개 + SSE 컨트롤러)와 static/index.html(목업 이식)을 추가한다. 기존 테스트 19개와 docs/phase3 문서는 불변 — 전략 코드는 main에 복제한다(의도된 중복).

**Tech Stack:** 기존 demo-phase3 스택 그대로 (Kotlin 2.2.21 · Boot 3.5.14 · Spring AI 1.1.6 · Koog 1.2.0/브리지 1.2.0-beta · Kotest 5.9.1). SSE는 Spring MVC `SseEmitter`.

**Spec:** `docs/superpowers/specs/2026-09-11-phase3-graph-runner-design.md`

## Global Constraints

- demo-phase3의 기존 테스트 19개·docs/phase3 문서·demo-phase1/2는 불변. 전체 스위트가 계속 통과해야 한다
- 화면은 목업의 시각 언어 그대로: 흑백 터미널 미학, IBM Plex, 점등 색·범례 동일. 목업 파일이 스펙이다 (아티팩트 "전략 그래프 러너" — 로컬 사본을 Task 5 브리프에 첨부)
- 이벤트 계약(전 시나리오 공통): `RunEvent(type: NODE|EDGE|TOOL|LOG|ANSWER|STORED|DONE|ERROR, id: String?, text: String?)` — 프런트·백이 이 하나만 본다
- Ollama 로컬 필수(qwen3:4b-instruct·bge-m3), API 키 없음. MCP 시나리오는 phase2 서버 프로브 실패 시 "비활성"으로 내려준다(오류 아님)
- 주석은 제약 설명만, 커밋은 `feat(demo-phase3): <한글 체언 종결형>`, 푸시는 전체 완료 후 1회

---

### Task 1: 이벤트 훅 스파이크 — EventHandler가 노드 이벤트를 주는가

**Files:**
- Test: `demo-phase3/src/test/kotlin/com/example/phase3/RunnerEventSpike.kt` (스파이크 — 판정 후 유지 여부 결정)
- Modify: `demo-phase3/build.gradle.kts` (EventHandler feature가 별도 아티팩트면 추가 — koog-agents-jvm에 포함인지 먼저 확인)

**Interfaces:**
- Produces: 판정 — "노드 진입/종료 훅 존재 여부"와 실제 훅 이름 목록. Task 2가 이 판정대로 이벤트 소스를 고른다

- [ ] **Step 1: 공식 문서·바이트코드 대조**

https://docs.koog.ai/agent-events/ (또는 docs 내 event handlers 페이지)를 읽고, `ai.koog.agents.features.eventHandler.feature.EventHandler`의 `handleEvents { ... }`가 제공하는 훅 전체를 javap/소스로 나열한다. 확인 대상: 노드 수준(진입·종료), LLM 호출 전후, 도구 호출 전후(onToolCallStarting은 문서 확인됨), 에이전트 시작·종료.

- [ ] **Step 2: 스파이크 테스트 작성·실행**

Ch04의 라우팅 전략에 `handleEvents`를 붙여 실행하고, 어떤 훅이 어떤 순서로 불리는지 println으로 채증한다:

```kotlin
val agent = AIAgent(..., strategy = router) {
    handleEvents {
        // Step 1에서 확인된 훅들을 전부 등록해 순서 채증
        onToolCallStarting { println("EVT tool ${'$'}{it.toolName}") }
        // onNodeExecutionStarting { ... } ← 존재가 확인되면
    }
}
```

Run: `cd demo-phase3 && ./gradlew test --tests RunnerEventSpike`
Expected: 훅 호출 로그. **노드 훅이 없으면** 폴백 확정: 시나리오 전략을 만들 때 각 노드 람다를 `emit(NODE, id)` 래퍼로 감싼다(Task 2의 ScenarioBuilder가 담당).

- [ ] **Step 3: 판정 기록 + Commit**

docs/phase3/decisions.md에 스파이크 판정(훅 목록, 채택 경로) 추가.

```bash
git add demo-phase3 docs/phase3/decisions.md
git commit -m "feat(demo-phase3): 러너 이벤트 스파이크 — EventHandler 훅 판정"
```

---

### Task 2: 러너 코어 + 라우팅 시나리오 end-to-end

**Files:**
- Create: `demo-phase3/src/main/kotlin/com/example/phase3/runner/RunEvent.kt`
- Create: `demo-phase3/src/main/kotlin/com/example/phase3/runner/Scenario.kt`
- Create: `demo-phase3/src/main/kotlin/com/example/phase3/runner/RoutingScenario.kt`
- Create: `demo-phase3/src/main/kotlin/com/example/phase3/runner/RunnerController.kt`
- Test: `demo-phase3/src/test/kotlin/com/example/phase3/RunnerControllerTest.kt`

**Interfaces:**
- Consumes: Task 1의 이벤트 소스 판정, `PromptExecutor` 빈, `KoogModels.QWEN3_4B_INSTRUCT`
- Produces: `Scenario` 인터페이스(모든 시나리오가 구현), `GET /api/scenarios`, `POST /api/run/{id}`(SSE). Task 3·4가 Scenario를 구현하고 Task 5가 API를 소비한다

- [ ] **Step 1: 이벤트·시나리오 계약 작성**

```kotlin
data class RunEvent(val type: Type, val id: String? = null, val text: String? = null) {
    enum class Type { NODE, EDGE, TOOL, LOG, ANSWER, STORED, DONE, ERROR }
}

interface Scenario {
    val id: String                    // "routing" 등 — 프런트 탭과 1:1
    val title: String
    val question: String              // ❯ 프롬프트에 보일 입력
    val graph: GraphMeta              // 노드·엣지 좌표 (프런트가 그림)
    val enabled: Boolean get() = true // MCP는 프로브 결과
    suspend fun run(emit: suspend (RunEvent) -> Unit): String  // 반환 = 최종 답
}
```

`GraphMeta`는 목업 SVG의 노드 id·좌표·엣지 경로를 데이터로 옮긴 것 (data class 셋: Node(id,x,y,w,label), Edge(id,from,to,label?,path), GraphMeta(nodes,edges)).

- [ ] **Step 2: RoutingScenario 구현**

Ch04 라우팅 전략을 복제하되, Task 1 판정에 따라 이벤트를 발행한다(훅 or 래퍼 노드). 그래프 메타는 목업의 라우팅 SVG 좌표를 그대로 옮긴다.

- [ ] **Step 3: SSE 컨트롤러**

```kotlin
@RestController
class RunnerController(private val scenarios: List<Scenario>) {
    @GetMapping("/api/scenarios")
    fun list() = scenarios.map { /* id, title, question, graph, enabled */ }

    @PostMapping("/api/run/{id}")
    fun run(@PathVariable id: String): SseEmitter {
        val emitter = SseEmitter(120_000)
        // 코루틴 스코프에서 scenario.run { emitter.send(...) } 실행,
        // 완료 시 DONE 전송 후 complete(), 예외는 ERROR 전송 — 스레드 경계 주의(스파이크 확인 사항)
        return emitter
    }
}
```

- [ ] **Step 4: 통합 테스트 작성·실행**

Kotest FunSpec: `/api/scenarios`가 routing을 포함하고, `/api/run/routing`의 SSE 응답에 NODE 이벤트들과 ANSWER("수학 경로")·DONE이 순서대로 담기는지. (SSE는 MockMvc 비동기 or WebTestClient — 실물 확인)

Run: `cd demo-phase3 && ./gradlew test --tests RunnerControllerTest`
Expected: PASS (실 Ollama)

- [ ] **Step 5: 전체 스위트 + Commit**

Run: `./gradlew test` — 기존 19개 그대로 통과 확인.

```bash
git add demo-phase3
git commit -m "feat(demo-phase3): 러너 코어 — SSE 이벤트 계약과 라우팅 시나리오"
```

---

### Task 3: 도구 루프 · 메모리 시나리오

**Files:**
- Create: `demo-phase3/src/main/kotlin/com/example/phase3/runner/ToolLoopScenario.kt`
- Create: `demo-phase3/src/main/kotlin/com/example/phase3/runner/MemoryScenario.kt`
- Modify: `demo-phase3/src/test/kotlin/com/example/phase3/RunnerControllerTest.kt` (시나리오별 테스트 추가)

**Interfaces:**
- Consumes: `Scenario` 계약, ExchangeToolSet(도구 — main에 복제하거나 test fixture 위치 판단해 기록), `ChatHistoryProvider`·`ChatMemoryRepository` 빈
- Produces: 시나리오 id "tool-loop" · "memory"

- [ ] **Step 1: ToolLoopScenario** — Ch05 그래프 전략 복제 + TOOL 이벤트(`exchangeRate(JPY)` 호출·결과). 도구 훅은 Task 1에서 확인된 onToolCallStarting 계열 사용
- [ ] **Step 2: MemoryScenario** — Ch06 도구+메모리 실행 복제. 실행 후 `chatMemoryRepository.findByConversationId(...)`를 읽어 행마다 `STORED` 이벤트로 내려보낸다(화면이 "도구 턴 없음"을 그대로 보여주는 근거). 세션 id는 실행마다 새로 발급
- [ ] **Step 3: 테스트** — tool-loop: TOOL 이벤트에 JPY 포함 + ANSWER. memory: STORED 이벤트가 정확히 SYSTEM·USER·ASSISTANT 3건
- [ ] **Step 4: 전체 스위트 + Commit** — `feat(demo-phase3): 도구 루프·메모리 시나리오 — 저장소 스냅샷 이벤트`

---

### Task 4: MCP 시나리오

**Files:**
- Create: `demo-phase3/src/main/kotlin/com/example/phase3/runner/McpScenario.kt`
- Modify: `demo-phase3/src/test/kotlin/com/example/phase3/RunnerControllerTest.kt`

**Interfaces:**
- Consumes: Ch08의 streamableHttp 연결·프로브 패턴 (프로브 로직은 main으로 옮겨 시나리오 `enabled`에 사용 — 테스트 쪽 프로브는 그대로 둠)
- Produces: 시나리오 id "mcp" — 서버 없으면 `enabled=false`

- [ ] **Step 1: McpScenario** — 프로브 → 도구 목록 LOG 이벤트(`lookupOrder · businessHours`) → 에이전트 실행(TOOL 이벤트) → ANSWER. `enabled`는 요청 시점 프로브로 갱신
- [ ] **Step 2: 테스트** — 서버 UP이면 ANSWER에 "무선 키보드" 또는 "배송중", 서버 DOWN이면 `/api/scenarios`에서 enabled=false (Ch08과 같은 skip 패턴)
- [ ] **Step 3: 전체 스위트 + Commit** — `feat(demo-phase3): MCP 시나리오 — phase2 서버 프로브와 비활성 처리`

---

### Task 5: 프런트 이식 — 목업을 SSE 구독으로

**Files:**
- Create: `demo-phase3/src/main/resources/static/index.html`
- Test: 수동 검증 (아래 체크리스트) + `RunnerControllerTest`는 그대로 자동 게이트

**Interfaces:**
- Consumes: `GET /api/scenarios`(그래프 메타 포함) · `POST /api/run/{id}` SSE, RunEvent 계약
- Produces: 완성 화면

- [ ] **Step 1: 목업 이식** — 목업 HTML(브리프에 첨부된 사본)을 가져와 ① 시뮬레이션 steps 배열 제거 ② 그래프 SVG를 `/api/scenarios`의 GraphMeta로 렌더(노드 rect·엣지 path 생성) ③ 실행 버튼 → fetch POST + SSE 파싱 → RunEvent type별로 기존 점등 함수 재사용 ④ MEMORY의 STORED 이벤트는 콘솔에 "저장된 행" 블록으로, MCP 비활성 탭은 흐리게+사유 표시
- [ ] **Step 2: 수동 검증 체크리스트 실행** — 서버 기동 후 브라우저에서: 4탭 렌더 / 라우팅 실행 시 노드 점등 순서가 콘솔 이벤트와 일치 / 도구 루프 왕복 / 메모리 저장 행 3건 / phase2 서버 켜고 MCP 실행·끄고 비활성 확인 / 새로고침 후 재실행. 각 항목 결과를 기록
- [ ] **Step 3: Commit** — `feat(demo-phase3): 러너 화면 — SSE 구독으로 실물 점등`

---

### Task 6: 마무리 — README·발표 안정화·푸시

**Files:**
- Modify: `demo-phase3/README.md` (없으면 생성 — 실행법·시나리오·전제)
- Modify: `demo-phase3/src/main/resources/application.properties` (keep-alive 검토)

- [ ] **Step 1: keep-alive** — 발표 중 모델 언로드 방지: `spring.ai.ollama.chat.options.keep-alive` 유효 키를 Spring AI 1.1.6 기준으로 확인해 적용(안 되면 결과만 기록)
- [ ] **Step 2: README** — 기동 명령, 시나리오 4개, MCP 사전 조건(phase2 서버), Ollama 전제
- [ ] **Step 3: 전체 스위트 최종 + Commit + Push**

```bash
cd demo-phase3 && ./gradlew test   # 기존 19 + 러너 테스트 전부
git add demo-phase3 && git commit -m "feat(demo-phase3): 러너 마무리 — README와 발표 안정화"
git push
```

---

## Self-Review 결과

- **스펙 커버리지**: 시나리오 4개→Task 2·3·4, 이벤트 소스 리스크→Task 1 스파이크, 목업=스펙→Task 5 Step 1, MCP 비활성→Task 4, 테스트·문서 불변→Global Constraints + 각 Task의 전체 스위트 확인, keep-alive→Task 6. 일치.
- **미확정 API**: EventHandler 훅 목록·SSE 코루틴 브리지는 Task 1이 판정하고 이후 태스크가 그 판정을 소비 — 기존 플랜과 같은 "실행이 확정" 패턴.
- **타입 일관성**: RunEvent·Scenario·GraphMeta 이름을 전 Task가 동일 소비. 시나리오 id(routing·tool-loop·memory·mcp)는 프런트 탭과 1:1.
