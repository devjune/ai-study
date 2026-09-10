# phase3 Koog 문서 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Koog를 Spring AI 브리지로 다루는 phase3 스터디 문서 한 편을, 모든 코드 블록을 `demo-phase3` 검증 프로젝트에서 실행 확인하며 완성한다.

**Architecture:** `demo-phase3`는 UI 없는 Spring Boot 프로젝트로, 장별 예제를 `@SpringBootTest` 통합 테스트로 담는다(로컬 Ollama 필요). 문서는 `docs/phase3/`에 한 편, 각 장은 "예제를 먼저 돌려 보고 → 그 코드로 집필" 순서를 지킨다.

**Tech Stack:** Spring Boot 4.0.0 · Spring AI 2.0.1 · Koog 1.2.0 (`koog-spring-ai-starter-*`) · Kotlin · Ollama (qwen3:4b-instruct, granite4:micro, bge-m3)

**Spec:** `docs/superpowers/specs/2026-09-10-phase3-koog-design.md`

## Global Constraints

- Kotlin **2.2.21**로 먼저 시도(저장소 고정 버전). 실패 시에만 demo-phase3 한정 2.3.x — Task 1이 판정한다
- JDK 툴체인 21 (demo-phase2와 동일)
- Spring Boot **4.0.0** · Spring AI BOM **2.0.1** · Koog **1.2.0** (spring-ai 스타터는 `1.2.0-beta`일 수 있음 — Task 1에서 Maven Central로 실버전 확인)
- 모델: Ollama `qwen3:4b-instruct`(채팅) · `bge-m3`(임베딩). API 키 불필요 원칙
- demo-phase1 · docs/phase1은 불가침. 위키도 손대지 않는다
- 문서 문체: 담백하게, 각주(`[^x]`) 금지, "이 데모는…" 식 데모 지목 서술 금지, 이름만 봐도 아는 주석 금지
- 커밋 컨벤션: `feat(demo-phase3): …` / `docs(phase3): …` 한글 체언 종결형
- 각 장 예제 테스트는 로컬 Ollama가 떠 있어야 통과한다. 실행 전 `curl -s localhost:11434/api/tags`로 확인

---

### Task 1: demo-phase3 골격 + Koog 호환 스파이크 (빌드)

**Files:**
- Create: `demo-phase3/settings.gradle.kts`
- Create: `demo-phase3/build.gradle.kts`
- Create: `demo-phase3/gradle/libs.versions.toml` (demo-phase2가 자체 카탈로그를 갖고 있으면 같은 방식으로, 루트 공유 카탈로그면 거기에 추가)
- Create: `demo-phase3/src/main/kotlin/dev/june/phase3/Phase3Application.kt`
- Create: `demo-phase3/src/main/resources/application.properties`
- Test: `demo-phase3/src/test/kotlin/dev/june/phase3/ContextLoadsTest.kt`

**Interfaces:**
- Consumes: demo-phase2의 빌드 구조(카탈로그 위치·패키지 네이밍은 demo-phase2를 열어 그대로 따른다 — 위 경로의 `dev.june.phase3`는 예시이며 실제 네이밍이 우선)
- Produces: 이후 모든 Task가 쓰는 Gradle 프로젝트, Spring AI Ollama 설정, Koog 의존성

- [ ] **Step 1: Koog 스타터 실버전 확인**

```bash
curl -s "https://search.maven.org/solrsearch/select?q=g:ai.koog+AND+a:koog-spring-ai-starter-model-chat&rows=5&wt=json" | python3 -m json.tool | grep -E '"latestVersion"|"v"'
```

`1.2.0`인지 `1.2.0-beta`인지 기록하고 이후 버전 표기에 그대로 쓴다.

- [ ] **Step 2: demo-phase2 빌드 구조 확인**

`demo-phase2/settings.gradle.kts`, `build.gradle.kts`, 카탈로그 파일, 메인 클래스 패키지명을 읽고 demo-phase3에 그대로 옮길 형태를 정한다.

- [ ] **Step 3: 골격 작성**

demo-phase2를 본떠 작성하되 의존성은 최소로:

```kotlin
// demo-phase3/build.gradle.kts 의존성 블록 (카탈로그 별칭은 demo-phase2 방식대로)
dependencies {
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.ai.starter.model.ollama)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.reflect)
    implementation("ai.koog:koog-spring-ai-starter-model-chat:${'$'}{Step 1의 버전}")
    testImplementation(libs.spring.boot.starter.test)  // demo-phase2에 같은 별칭이 없으면 좌표 직접 선언
}
```

```properties
# demo-phase3/src/main/resources/application.properties — demo-phase2의 Ollama 설정을 복사
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=qwen3:4b-instruct
spring.ai.ollama.chat.options.keep-alive=2h
```

(demo-phase2의 실제 프로퍼티 키를 우선한다 — 위는 형태 예시)

- [ ] **Step 4: 컨텍스트 로드 테스트 작성**

```kotlin
@SpringBootTest
class ContextLoadsTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor  // ai.koog.prompt.executor.model.PromptExecutor

    @Test
    fun `Koog 스타터가 Spring AI ChatModel을 PromptExecutor로 감싼다`() {
        assertNotNull(promptExecutor)
    }
}
```

- [ ] **Step 5: 빌드 실행 — 호환 판정 1차**

Run: `cd demo-phase3 && ./gradlew test --tests ContextLoadsTest`
Expected: PASS. **Kotlin 메타데이터 오류가 나면**(예: "requires a newer version of the Kotlin compiler") demo-phase3의 Kotlin만 2.3.x로 올리고 재시도, 결과를 기록한다.

- [ ] **Step 6: Commit**

```bash
git add demo-phase3
git commit -m "feat(demo-phase3): Koog 검증 프로젝트 골격 — Spring AI 브리지 컨텍스트 로드 확인"
```

---

### Task 2: 최소 AIAgent 실행 — 호환 스파이크 (런타임) + 판정 기록

**Files:**
- Create: `demo-phase3/src/main/kotlin/<패키지>/KoogModels.kt`
- Create: `docs/phase3/decisions.md`
- Test: `demo-phase3/src/test/kotlin/<패키지>/Ch03SingleAgentTest.kt`

**Interfaces:**
- Consumes: Task 1의 `PromptExecutor` 빈
- Produces: `KoogModels.QWEN3_4B_INSTRUCT: LLModel` — 이후 모든 장이 쓰는 모델 상수

- [ ] **Step 1: Ollama용 LLModel 상수 정의**

Koog에 `qwen3:4b-instruct` 프리셋은 없으므로 직접 정의한다. `LLModel` 생성자 시그니처는 IDE/소스에서 확인해 맞춘다(1.x에 `contextLength` 필수 여부 확인):

```kotlin
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

object KoogModels {
    val QWEN3_4B_INSTRUCT = LLModel(
        provider = LLMProvider.Ollama,
        id = "qwen3:4b-instruct",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.Schema.JSON.Basic,
        ),
    )
}
```

- [ ] **Step 2: 최소 에이전트 테스트 작성**

```kotlin
@SpringBootTest
class Ch03SingleAgentTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor

    @Test
    fun `AIAgent 한 개가 Ollama로 답한다`() = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "한 문장으로만 답한다.",
        )
        val result = agent.run("Spring AI가 무엇인지 한 문장으로")
        assertTrue(result.isNotBlank())
    }
}
```

- [ ] **Step 3: Ollama 확인 후 실행 — 호환 판정 2차**

Run: `curl -s localhost:11434/api/tags | grep qwen3 && cd demo-phase3 && ./gradlew test --tests Ch03SingleAgentTest`
Expected: PASS + 실제 응답 문자열. 실패 시 스펙의 리스크 절차대로(Kotlin 2.3.x → Koog 다운그레이드 → 주제 재검토) 단계적으로 시도하고 각 결과를 기록한다.

- [ ] **Step 4: 판정을 decisions.md에 기록**

`docs/phase3/decisions.md`를 만들고 첫 항목으로 스파이크 결과를 적는다: 시도한 Kotlin 버전, Koog 실버전, 오류가 있었다면 원문, 최종 조합.

- [ ] **Step 5: Commit**

```bash
git add demo-phase3 docs/phase3/decisions.md
git commit -m "feat(demo-phase3): 최소 AIAgent 실행 — Kotlin 호환 스파이크 판정"
```

---

### Task 3: 문서 골격 + 1·2장 (소개, 연결 구조)

**Files:**
- Create: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: Task 1·2에서 검증된 의존성 좌표·설정·`KoogModels` 코드 (문서 코드 블록은 검증본에서 복사)
- Produces: 3장 이후가 이어 붙을 문서 파일과 장 제목 체계 (`## N장 제목` 형식 — phase2 문서와 동일)

- [ ] **Step 1: 서문과 목차 작성**

스펙의 "배경 · 왜 이 주제인가"를 산문으로 옮긴다. phase2 문서처럼 21… 아니, 9개 장 표를 서두에 둔다. phase2 문서의 서두 형식(`docs/phase2/Spring AI로 구현하는 에이전틱 패턴.md` 상단)을 참고하되 문장은 새로 쓴다.

- [ ] **Step 2: 1장 — Koog는 무엇이고 왜**

손조립(phase2) 대 프레임워크 구도, Koog가 공짜로 주는 것(그래프 전략·재시도·히스토리 압축·관측성)과 브리지가 가리는 것(어댑터 제약)을 예고. 코드 블록 없음.

- [ ] **Step 3: 2장 — 연결 구조**

Task 1에서 검증된 것만 쓴다: 의존성 좌표(실버전), application.properties, "ChatModel 빈 → `SpringAiLLMClient` → `PromptExecutor`" 조립 구조, "도구 실행은 Koog가 하고 Spring AI는 스키마만 받는다"는 브리지 원칙, `KoogModels`의 LLModel 직접 정의(프리셋이 없는 이유 포함).

- [ ] **Step 4: Commit**

```bash
git add "docs/phase3"
git commit -m "docs(phase3): 서문과 1·2장 — Koog 소개, Spring AI 브리지 연결 구조"
```

---

### Task 4: 3장 — 단일 에이전트

**Files:**
- Modify: `demo-phase3/src/test/kotlin/<패키지>/Ch03SingleAgentTest.kt`
- Modify: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: `KoogModels.QWEN3_4B_INSTRUCT`, `promptExecutor` 빈
- Produces: 문서 3장. 이후 장이 전제하는 "AIAgent 기본형" 서술

- [ ] **Step 1: 예제 확장 — 파라미터가 있는 에이전트**

Task 2의 최소 예제에 `temperature`, `maxIterations`를 더한 두 번째 테스트를 추가하고 실행한다:

```kotlin
@Test
fun `temperature와 maxIterations를 지정한다`() = runBlocking {
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        systemPrompt = "한 문장으로만 답한다.",
        temperature = 0.2,
        maxIterations = 5,
    )
    assertTrue(agent.run("코루틴이 무엇인지 한 문장으로").isNotBlank())
}
```

Run: `cd demo-phase3 && ./gradlew test --tests Ch03SingleAgentTest`
Expected: PASS

- [ ] **Step 2: 3장 집필**

검증된 코드로: `AIAgent` 생성자(promptExecutor · llmModel · systemPrompt · temperature · maxIterations), `agent.run()`이 suspend라는 점과 코루틴 호출 형태, phase2의 `ChatClient.prompt().call()`과 한 줄 대조.

- [ ] **Step 3: Commit**

```bash
git add demo-phase3 "docs/phase3"
git commit -m "docs(phase3): 3장 — AIAgent 기본형"
```

---

### Task 5: 4장 — 그래프 전략

**Files:**
- Test: `demo-phase3/src/test/kotlin/<패키지>/Ch04StrategyTest.kt`
- Modify: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: `KoogModels.QWEN3_4B_INSTRUCT`, `promptExecutor` 빈
- Produces: 문서 4장. `strategy` DSL 서술 — 5장(도구 루프)이 이 위에 선다

- [ ] **Step 1: 공식 문서 대조**

https://docs.koog.ai/custom-strategy-graphs/ 를 읽고 아래 초안의 노드·엣지 이름이 1.2.0과 일치하는지 확인한다(`nodeLLMRequest` · `nodeExecuteTools` · `nodeLLMSendToolResults` · `edge` · `forwardTo` · `onTextMessage` · `onToolCalls` · `onCondition` · `transformed`).

- [ ] **Step 2: 전략 예제 테스트 작성·실행**

도구 없는 2단 체인(phase2 1장의 프롬프트 체이닝 재현)을 커스텀 전략으로:

```kotlin
@Test
fun `두 노드를 잇는 커스텀 전략이 돈다`() = runBlocking {
    val chain = strategy<String, String>("two-step") {
        val draft by nodeLLMRequest()
        val polish by nodeLLMRequest()
        edge(nodeStart forwardTo draft)
        edge(draft forwardTo polish transformed { "다음 문장을 더 짧게: ${'$'}it" })
        edge(polish forwardTo nodeFinish)
    }
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        strategy = chain,
        systemPrompt = "한 문장으로만 답한다.",
    )
    assertTrue(agent.run("Kotlin의 장점 하나").isNotBlank())
}
```

노드 간 메시지 전달 형태(엣지 출력 타입)가 문서와 다르면 실행 결과에 맞춰 고치고, 고친 지점을 기록해 4장에 반영한다. `AIAgent`에 전략을 넘기는 파라미터명(`strategy`)도 실물로 확인한다.

Run: `cd demo-phase3 && ./gradlew test --tests Ch04StrategyTest`
Expected: PASS

- [ ] **Step 3: 4장 집필**

전략 = 노드·엣지 그래프, `nodeStart`/`nodeFinish`, 조건 엣지(`onCondition` · `onToolCalls` · `onTextMessage`), `transformed`. phase2의 1장(체이닝)·2장(라우팅)·4장(리플렉션)이 이 DSL에서 각각 어떤 모양이 되는지 문장으로 대응.

- [ ] **Step 4: Commit**

```bash
git add demo-phase3 "docs/phase3"
git commit -m "docs(phase3): 4장 — 그래프 전략 DSL"
```

---

### Task 6: 5장 — 도구

**Files:**
- Test: `demo-phase3/src/test/kotlin/<패키지>/Ch05ToolsTest.kt`
- Modify: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: `KoogModels.QWEN3_4B_INSTRUCT`, `promptExecutor` 빈
- Produces: 문서 5장. `ToolSet`/`ToolRegistry` 서술 — 8장(MCP 도구)이 대조 대상으로 쓴다

- [ ] **Step 1: 도구 예제 테스트 작성·실행**

```kotlin
@LLMDescription("환율 조회 도구")
class ExchangeToolSet : ToolSet {
    @Tool
    @LLMDescription("통화 코드의 원화 환율을 돌려준다")
    fun exchangeRate(@LLMDescription("통화 코드, 예: USD") code: String): String =
        when (code.uppercase()) {
            "USD" -> "1385"
            "JPY" -> "9.2"
            else -> "미지원 통화"
        }
}

@Test
fun `에이전트가 도구를 호출해 답한다`() = runBlocking {
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        systemPrompt = "환율 질문에는 반드시 도구를 사용한다.",
        toolRegistry = ToolRegistry { tools(ExchangeToolSet()) },
    )
    val result = agent.run("1달러는 몇 원이야?")
    assertTrue(result.contains("1385") || result.contains("1,385"))
}
```

Run: `cd demo-phase3 && ./gradlew test --tests Ch05ToolsTest`
Expected: PASS. qwen3:4b이 도구를 안 부르면 시스템 프롬프트를 조여 재시도하고, 그래도 불안정하면 단정을 `isNotBlank()`로 낮추고 그 관찰을 5장에 적는다(작은 모델의 도구 호출 신뢰도는 phase2에서도 다룬 주제).

- [ ] **Step 2: 5장 집필**

`@Tool` · `@LLMDescription` · `ToolSet` · `ToolRegistry`, phase2 `@Tool`(Spring AI)과 이름이 같지만 다른 타입이라는 함정 명시(임포트 대조 표), "실행은 Koog, 스키마만 Spring AI" 구조 재확인.

- [ ] **Step 3: Commit**

```bash
git add demo-phase3 "docs/phase3"
git commit -m "docs(phase3): 5장 — Koog 도구와 Spring AI @Tool의 대조"
```

---

### Task 7: 6장 — 메모리와 히스토리 압축

**Files:**
- Modify: `demo-phase3/build.gradle.kts` (`koog-spring-ai-starter-chat-memory` 추가)
- Test: `demo-phase3/src/test/kotlin/<패키지>/Ch06MemoryTest.kt`
- Modify: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: Task 1의 프로젝트, Spring AI `ChatMemoryRepository` 빈(인메모리 기본)
- Produces: 문서 6장

- [ ] **Step 1: 공식 문서 대조**

https://docs.koog.ai/spring-ai-integration/ 의 chat-memory 절을 읽고 `ChatHistoryProvider`를 에이전트에 연결하는 실제 API(파라미터명·기능 설치 방식)를 확인해 아래 초안을 고친다.

- [ ] **Step 2: 메모리 예제 테스트 작성·실행**

두 번의 `run` 사이에 대화가 이어지는지 확인하는 테스트. 형태는 Step 1에서 확인한 API에 따르되, 검증 골자는:

```kotlin
@Test
fun `이전 대화를 기억한다`() = runBlocking {
    // Step 1에서 확인한 방식으로 ChatHistoryProvider를 붙인 에이전트 구성
    agent.run("내 이름은 준이야")
    val result = agent.run("내 이름이 뭐라고 했지?")
    assertTrue(result.contains("준"))
}
```

Run: `cd demo-phase3 && ./gradlew test --tests Ch06MemoryTest`
Expected: PASS

- [ ] **Step 3: 히스토리 압축 확인**

전략에 `nodeLLMCompressHistory()` 노드를 넣은 변형 테스트를 추가해 압축 호출이 도는 것까지만 확인한다(효과 측정은 범위 밖).

- [ ] **Step 4: 6장 집필**

`ChatMemoryRepository` → `ChatHistoryProvider` 어댑터, **제약을 정면으로**: 평문 System·User·Assistant만 저장되고 도구 호출·추론 메시지는 조용히 버려진다는 것(스펙의 핵심 관찰). 히스토리 압축은 프레임워크가 공짜로 주는 것의 예로.

- [ ] **Step 5: Commit**

```bash
git add demo-phase3 "docs/phase3"
git commit -m "docs(phase3): 6장 — 메모리 어댑터의 제약과 히스토리 압축"
```

---

### Task 8: 7장 — RAG

**Files:**
- Modify: `demo-phase3/build.gradle.kts` (`koog-spring-ai-starter-model-embedding` · `koog-spring-ai-starter-vector-store`, Spring AI 쪽 `spring-ai-vector-store` 추가)
- Modify: `demo-phase3/src/main/resources/application.properties` (bge-m3 임베딩 설정 — demo-phase2에서 복사)
- Test: `demo-phase3/src/test/kotlin/<패키지>/Ch07RagTest.kt`
- Modify: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: Spring AI `EmbeddingModel`(bge-m3) · `SimpleVectorStore` 빈
- Produces: 문서 7장

- [ ] **Step 1: 공식 문서 대조**

https://docs.koog.ai/spring-ai-integration/ 의 vector-store 절에서 `KoogVectorStore`와 `LLMEmbeddingProvider`의 사용 API를 확인한다. "update는 delete+add라 비트랜잭션, read-by-id 없음" 제약도 원문으로 확보.

- [ ] **Step 2: RAG 예제 테스트 작성·실행**

문서 2~3개를 넣고 의미 검색이 맞는 문서를 돌려주는지 확인:

```kotlin
@Test
fun `벡터 검색이 관련 문서를 찾는다`() = runBlocking {
    // Step 1에서 확인한 API로 KoogVectorStore에 문서 저장 후 검색
    // 저장: "사내 VPN은 재택 시 필수" / "점심 법인카드 한도는 1.5만원"
    // 검색: "재택근무 네트워크 규정" → VPN 문서가 상위
}
```

Run: `cd demo-phase3 && ./gradlew test --tests Ch07RagTest`
Expected: PASS

- [ ] **Step 3: 7장 집필**

두 어댑터 구조, phase2 9장(`QuestionAnswerAdvisor`)과의 대조 — Spring AI는 어드바이저가 자동 주입, Koog는 검색을 전략 노드로 명시. 비트랜잭션 제약 명시.

- [ ] **Step 4: Commit**

```bash
git add demo-phase3 "docs/phase3"
git commit -m "docs(phase3): 7장 — RAG 어댑터"
```

---

### Task 9: 8장 — MCP 연동

**Files:**
- Modify: `demo-phase3/build.gradle.kts` (Koog MCP 모듈 — Step 1에서 좌표 확인)
- Test: `demo-phase3/src/test/kotlin/<패키지>/Ch08McpTest.kt`
- Modify: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: demo-phase2가 MCP 서버로 뜰 수 있다는 사실(10·15장 — `spring-ai-starter-mcp-server-webmvc`)
- Produces: 문서 8장

- [ ] **Step 1: 공식 문서 대조**

https://docs.koog.ai/model-context-protocol/ 를 읽고 MCP 클라이언트 API(도구 레지스트리 프로바이더, 트랜스포트 종류)와 gradle 좌표를 확보한다.

- [ ] **Step 2: MCP 예제 테스트 작성·실행**

demo-phase2를 MCP 서버로 띄운 상태에서 Koog 에이전트가 그 도구 목록을 가져오는 것까지 검증한다(도구 호출까지 되면 더 좋고, 목록 확인이 최소선):

```kotlin
@Test
fun `Koog가 demo-phase2 MCP 서버의 도구를 읽는다`() = runBlocking {
    // Step 1에서 확인한 API로 MCP 트랜스포트 연결 → ToolRegistry 구성
    // assertTrue(registry.tools.isNotEmpty())
}
```

사전 조건: 별도 터미널에서 demo-phase2 서버 기동. 테스트 주석에 기동 명령을 적는다.
Run: `cd demo-phase3 && ./gradlew test --tests Ch08McpTest`
Expected: PASS (demo-phase2 서버 없으면 SKIP 처리 — `@EnabledIf`로 포트 체크)

- [ ] **Step 3: 8장 집필**

phase2 10장(서버 만들기)과의 연결 — 그때 만든 서버에 이번엔 Koog가 클라이언트로 붙는 구도. 트랜스포트와 도구 레지스트리 코드.

- [ ] **Step 4: Commit**

```bash
git add demo-phase3 "docs/phase3"
git commit -m "docs(phase3): 8장 — MCP 클라이언트로 phase2 서버에 접속"
```

---

### Task 10: 9장 — phase2 패턴 대응표 + 퇴고

**Files:**
- Modify: `docs/phase3/Koog — Spring AI 위에 얹는 에이전트 프레임워크.md`

**Interfaces:**
- Consumes: 완성된 1~8장, phase2 문서의 21개 패턴 표
- Produces: 완성된 문서

- [ ] **Step 1: 9장 대응표 작성**

phase2 21개 패턴 각각에 대해 "Koog에선 무엇인가"를 한 줄씩: 직접 지원(전략 DSL·ToolRegistry·압축 등) / 브리지로 재사용(메모리·RAG) / Koog에도 없어 여전히 손조립(예: phase2 13장 사람 개입 등 — 1~8장 집필 중 확인된 사실만 적는다).

- [ ] **Step 2: 전체 퇴고**

처음부터 끝까지 읽으며: 각주 없음, 데모 지목 서술 없음, 코드 블록이 전부 demo-phase3 검증본과 일치하는지 대조, 긴 문장 분할.

- [ ] **Step 3: 전체 테스트 재실행**

Run: `curl -s localhost:11434/api/tags >/dev/null && cd demo-phase3 && ./gradlew test`
Expected: 전부 PASS (Ch08은 demo-phase2 서버 없으면 SKIP)

- [ ] **Step 4: Commit + Push**

```bash
git add demo-phase3 "docs/phase3"
git commit -m "docs(phase3): 9장 대응표와 전체 퇴고 — 문서 초판 완성"
git push
```

---

## Self-Review 결과

- **스펙 커버리지**: 결정 4개(브리지 각도→전 장, 문서 먼저→Task 3~10, 기능 순서 목차→Task 3~10이 9장 목차와 1:1, 실행 검증→모든 장에 테스트 선행), 리스크(Kotlin)→Task 1·2, decisions.md 기록→Task 2. 데모 웹 앱은 스펙에서 제외이므로 계획에 없음 — 일치.
- **미확정 API 처리**: 6·7·8장은 공식 문서 대조 Step을 먼저 두고 검증 골자를 명시하는 방식으로 처리했다. Koog 1.2.0의 세부 시그니처는 실행이 확정한다.
- **타입 일관성**: `KoogModels.QWEN3_4B_INSTRUCT`와 `promptExecutor` 빈을 전 Task가 같은 이름으로 소비.
