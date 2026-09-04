## 들어가며

### Spring AI란

**Java/Spring 생태계에서 AI 모델을 통합하는 표준 추상화입니다.** Python 진영의 LangChain에 대응합니다.

엔터프라이즈 백엔드는 여전히 Java/Spring 위에서 동작합니다. Spring AI는 그 위에서 Spring 방식으로 AI를 통합합니다. 핵심 철학은 **이식성**입니다 

>  OpenAI든 Anthropic이든 로컬 Ollama든, 코드는 그대로 두고 의존성·설정만 바꿔 모델을 교체할 수 있습니다. `JdbcTemplate`이 DB 벤더를 추상화하던 발상과 같습니다.

### 1.x에서 2.0으로

- **1.0 (2025-05)** — 기본 추상화 등장: `ChatClient`, Structured Output, Tool Calling, RAG.
- **1.1 (2025-11)** — 안정화, Advisor 체계 정비.
- **2.0 (2026-06)** — Spring Boot 4로 토대를 다시 세운 릴리스. Tool Calling 정리, ToolSearch, MCP 편입.

이 문서는 **2.0만 기준으로** 설명합니다. 1.x를 몰라도 읽는 데 지장이 없습니다. 기존 코드를 올려야 하는 경우에 필요한 변경점은 마지막 "1.x → 2.0 한눈에" 절에 모아 두었습니다.

---

## 무엇을 만들 수 있나

| 유스케이스               | 설명                              | 핵심 기능                   |
| ------------------- | ------------------------------- | ----------------------- |
| **챗봇**              | 대화 맥락을 기억하는 대화형 응답              | ChatClient + ChatMemory |
| **문서·텍스트 자동화**      | 리뷰·문의·계약서를 구조화된 데이터로 (분류·필드 추출) | Structured Output       |
| **사내 문서 Q&A (RAG)** | 회사 문서에 근거해 답하기                  | RAG + VectorStore       |
| **에이전트**            | LLM이 실제 함수·시스템을 호출해 일을 처리       | Tool Calling            |
| **도구·서비스 상호운용**     | 외부/타 팀 도구를 표준 프로토콜로 연결          | MCP                     |

### 지원 범위

- **모델 종류** — Chat, Embedding, Text-to-Image, Audio Transcription, Text-to-Speech, Moderation.
- **벤더** — Anthropic, OpenAI, Amazon Bedrock, Google(Gemini/Vertex), Ollama, Mistral, DeepSeek 등.
- **VectorStore** — pgvector, Redis, Chroma, Qdrant, Elasticsearch, MongoDB Atlas 등 **21종**.
- **로컬 실행** — `spring-ai-starter-model-transformers`로 임베딩을 로컬 ONNX 모델에서 생성할 수 있음 (외부 API 키 불필요).

---

## 큰 그림

새 이름이 여럿 나오지만, 지금 기억할 것은 **`ChatClient` 하나**입니다. 나머지는 그 주변에 어떻게 붙는지만 보면 됩니다.

```mermaid
graph LR
    App["내 코드<br/>(Controller · Service)"] --> CC["ChatClient"]
    CC --> CM["ChatModel<br/>(벤더 구현)"]
    CM --> LLM["LLM<br/>Anthropic · OpenAI · Ollama"]
    Adv["Advisor<br/>(RAG · 대화 기억 · 도구)"] -. 끼워 넣는다 .-> CC
    style CC fill:#ffe08a,stroke:#e0a800,stroke-width:2px
```

**1. 내가 만지는 것은 `ChatClient` 하나입니다.** 이 문서의 모든 예제가 `chatClient.prompt()...`로 시작합니다.

**2. 벤더는 그 아래 `ChatModel`이 감춥니다.** 벤더를 바꾼다는 것은 이 구현체가 바뀐다는 뜻이고 위의 호출 코드는 그대로입니다. 앞서 든 `JdbcTemplate` 비유가 여기입니다 — 인터페이스는 두고 드라이버만 갑니다.

**3. 기능은 `Advisor`로 끼워 넣습니다.** RAG도 대화 기억도 도구 호출도 전부 `ChatClient` 호출을 가로채는 Advisor입니다. 서블릿 필터나 `HandlerInterceptor`와 같은 자리라고 보면 됩니다.

이름은 지금 외우지 않아도 됩니다. §1부터 이 그림의 자리를 하나씩 채웁니다.

---

## 시작하기

**의존성 1개 + 설정 2줄이면 첫 호출을 할 수 있습니다.**

### 1. 의존성

```kotlin
implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
```

### 2. 설정

```properties
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.model=claude-sonnet-4-5
```

### 3. 첫 호출

`ChatClient.Builder`가 자동으로 빈 등록됩니다. 이를 주입받아 사용합니다.

```kotlin
@RestController
class ChatController(builder: ChatClient.Builder) {
    private val chatClient = builder.build()

    @GetMapping("/ai")
    fun ai(@RequestParam message: String): String =
        chatClient.prompt()
            .user(message)
            .call()
            .content() ?: ""      // 2.0의 응답 메서드는 nullable
}
```

`prompt().user(...).call().content()` — 이 체인 하나가 Spring AI 호출의 기본형입니다. `WebClient.Builder`를 주입받아 커스터마이징하던 패턴과 동일합니다.

### 벤더 바꾸기

여기까지가 전부입니다. 벤더를 바꿔도 **위 컨트롤러 코드는 한 글자도 손대지 않습니다** — 1번과 2번만 갈아 끼웁니다.

| 벤더 | 스타터 | 설정 |
| --- | --- | --- |
| Anthropic | `spring-ai-starter-model-anthropic` | `spring.ai.anthropic.api-key=...`<br>`spring.ai.anthropic.chat.model=claude-sonnet-4-5` |
| OpenAI | `spring-ai-starter-model-openai` | `spring.ai.openai.api-key=...`<br>`spring.ai.openai.chat.model=gpt-5` |
| Ollama | `spring-ai-starter-model-ollama` | `spring.ai.ollama.chat.model=llama3.2` |

Ollama만 API 키가 없습니다. 로컬에서 모델을 직접 돌리므로 외부망이 막힌 환경의 선택지입니다.

---

## 핵심 기능 (2.0 기준 상세)

### 1. ChatClient — LLM 호출의 중심

**모든 LLM 호출이 통과하는 fluent API입니다.** 호출 방식·응답 형태·시스템 프롬프트를 여기서 다룹니다.

#### 응답 받는 세 가지 방법

시작하기에서 쓴 `.content()`가 셋 중 하나입니다. **무엇을 받을지에 따라 마지막 메서드만 바뀝니다.**

```kotlin
// 1) 텍스트 — 시작하기에서 쓴 것
val text: String? = chatClient.prompt().user(msg).call().content()

// 2) 전체 응답 — 토큰 사용량 등 메타데이터가 필요할 때 (§9)
val resp: ChatResponse? = chatClient.prompt().user(msg).call().chatResponse()
val tokens = resp?.metadata?.usage?.totalTokens

// 3) 타입 객체 — 바로 다음 §2에서 다룬다
val movie: Movie? = chatClient.prompt().user(msg).call().entity(Movie::class.java)
```

> `.call()` 자체는 모델을 호출하지 않습니다. `.content()` / `.chatResponse()` / `.entity()`를 호출하는 순간 실제 요청이 나갑니다.

**세 메서드 모두 nullable입니다.** 2.0이 JSpecify로 널 안전성을 API에 명시(`@Nullable`)한 결과입니다. 널 처리는 호출부 책임입니다(`?: ""` 또는 `?: error(...)`).

#### 스트리밍

토큰이 오는 대로 흘려보내 첫 글자까지의 시간을 줄입니다(타이핑 효과). `.call()` 대신 `.stream()`을 쓰면 반환 타입이 `Flux`가 됩니다.

```kotlin
val flux: Flux<String> = chatClient.prompt().user(msg).stream().content()
```

이 `Flux`를 브라우저까지 내보내려면 컨트롤러에서 **SSE**(Server-Sent Events)로 반환합니다. `produces`를 지정하는 것이 핵심입니다.

```kotlin
@GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun stream(@RequestParam message: String): Flux<String> =
    chatClient.prompt().user(message).stream().content()
```

- **WebFlux가 필수는 아닙니다.** Spring MVC도 리액티브 반환 타입을 처리하므로 `spring-boot-starter-web`만으로 동작합니다.
- 브라우저 쪽은 `EventSource`로 받습니다.

> 스트리밍에는 제약이 따릅니다. `.entity()`(Structured Output)처럼 응답 전체가 모여야 성립하는 기능은 함께 쓸 수 없습니다. 토큰 사용량 같은 메타데이터도 스트림이 끝나야 확정됩니다.

#### 시스템 프롬프트 (페르소나)

호출 시점에 `.system()`으로 페르소나를 지정합니다. 빌더의 `defaultSystem()`에 기본값을 두고 호출마다 덮어쓸 수도 있습니다.

```kotlin
chatClient.prompt()
    .system("너는 해적처럼 말하는 챗봇이다. 항상 해적 말투로 답하라.")
    .user(message)
    .call()
    .content()
```

### 2. Structured Output — 응답을 타입 객체로

**`.content()` 대신 `.entity()` 한 줄로, LLM 응답을 타입 객체로 받습니다.** LLM을 타입 안전한 함수처럼 쓰는 것입니다.

```kotlin
data class Movie(val title: String, val year: Int, val director: String)

val movie: Movie = chatClient.prompt()
    .user("SF 영화 하나 추천해줘")
    .call()
    .entity(Movie::class.java)
    ?: error("Failed to convert model response to Movie")
```

- 내부 동작: data class로 **JSON 스키마를 만들어 요청에 함께 보내고**("이 형식으로만 답하라"는 지시와 함께), **LLM이 처음부터 JSON으로 답하면** `BeanOutputConverter`가 역직렬화합니다. 응답 텍스트에서 필드를 뽑아내는 게 아니라 애초에 그 형식으로 달라고 시키는 방식입니다.
- 컬렉션은 `entity(object : ParameterizedTypeReference<List<Movie>>() {})`로 받습니다.
- `.entity()` 자체는 재시도하지 않습니다. 재시도는 `StructuredOutputValidationAdvisor`가 별도로 담당합니다.

> **LLM 출력을 신뢰하지 말고 외부 입력처럼 다룹니다.** 객체로 받았어도 값 검증(범위·필수값)은 애플리케이션의 책임입니다.

### 3. 대화 메모리 (ChatMemory) — 맥락을 기억하기

**LLM은 기본적으로 이전 대화를 기억하지 못합니다.** 매 호출이 독립적입니다. 대화형 챗봇을 만들려면 이전 메시지를 다시 넣어줘야 합니다. `ChatMemory` + `MessageChatMemoryAdvisor`가 이를 자동화합니다.

```kotlin
// builder: ChatClient.Builder, chatMemory: ChatMemory 를 주입받는다
val chatClient = builder
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build()

// 호출 시 대화 ID를 넘긴다 (세션 구분)
chatClient.prompt()
    .user("방금 뭐라고 했지?")
    .advisors { it.param(ChatMemory.CONVERSATION_ID, "user-42") }
    .call()
    .content()
```

> 대화 ID는 **호출 시점에** 넘겨야 합니다. 빌더에 미리 박아둘 수 없고 누락하면 예외가 납니다.

#### 대화를 어디에 저장할 것인가

위 예제의 `MessageWindowChatMemory`는 기본적으로 **메모리에만** 담습니다. 서버를 재시작하면 대화가 사라집니다. 인스턴스를 2대로 늘리면 어느 서버로 붙느냐에 따라 맥락이 갈립니다. 운영에서는 저장소를 붙여야 합니다.

역할이 둘로 나뉘어 있습니다.

- **`ChatMemory`** — 무엇을 기억할지 정하는 **정책** (예: 최근 N개 메시지만 유지)
- **`ChatMemoryRepository`** — 그것을 어디에 둘지 정하는 **저장소** (JDBC, Redis, MongoDB, Cassandra, Neo4j)

```kotlin
@Bean
fun chatMemory(chatMemoryRepository: ChatMemoryRepository): ChatMemory =
    MessageWindowChatMemory.builder()
        .chatMemoryRepository(chatMemoryRepository)   // 저장소만 갈아 끼운다
        .build()
```

저장소는 스타터로 선택합니다. 스타터와 설정만 바꾸면 되고 **위 코드는 그대로**입니다.

```kotlin
implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
```

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/app
spring.ai.chat.memory.repository.jdbc.initialize-schema=always
```

JDBC 저장소는 플랫폼별 스키마(postgresql, mysql, mariadb, oracle, sqlserver, h2 등)를 내장하고 있고 `initialize-schema` 같은 **기존 Boot의 스키마 초기화 방식을 그대로** 씁니다. 애플리케이션의 `DataSource`를 공유하는 구조입니다.

> 별도 설치 없이 돌려 보려면 같은 JDBC 저장소를 H2 파일 모드(`jdbc:h2:file:./data/chatmemory`)로 쓸 수 있습니다. 앱을 재시작해도 대화가 유지됩니다.

### 4. Tool Calling — LLM이 함수를 호출한다

Tool Calling은 **LLM이 혼자 처리하지 못하는 일을 외부 함수(도구)에 맡기는 기능**입니다. 날씨 조회나 DB 검색처럼 모델이 알 수 없는 정보를 도구로 가져와 답에 반영합니다.

단, **정작 도구를 실행하는 건 LLM이 아니라 우리 앱입니다.** LLM이 하는 일은 "이 함수를 이 인자로 불러 달라"는 요청까지입니다. 실제 호출과 결과 전달은 앱의 몫입니다. LLM은 함수(API)에 직접 접근하지 못합니다. 일부러 그렇게 막아 둔, 보안상 중요한 경계입니다.

```mermaid
sequenceDiagram
    participant U as 사용자
    participant App as Spring AI
    participant LLM as LLM
    participant Fn as 내 함수 (@Tool)
    U->>App: "서울 날씨 어때?"
    App->>LLM: 질문 + 사용 가능한 도구 목록
    LLM->>App: getWeather("서울") 호출 요청
    App->>Fn: 실제 함수 실행
    Fn->>App: "맑음, 22도"
    App->>LLM: 함수 결과 전달
    LLM->>App: "서울은 맑고 22도입니다"
    App->>U: 최종 답변
```

도구는 `@Tool` 애노테이션 하나로 정의합니다. `description`이 LLM에게 "언제 이 도구를 쓸지" 알려주는 힌트라 가장 중요합니다.

```kotlin
class WeatherTools {
    @Tool(description = "특정 도시의 현재 날씨를 조회한다")
    fun getWeather(@ToolParam(description = "도시 이름") city: String): String {
        // 실제 조회 로직 (DB, 외부 API 등)
        return "$city: 맑음, 22도"
    }
}

chatClient.prompt()
    .user("서울 날씨 어때?")
    .tools(WeatherTools())   // @Tool 메서드를 가진 객체를 넘긴다
    .call()
    .content()
```

도구를 **쓸지 말지도 LLM이 정합니다.** 같은 코드에 `"안녕하세요"`를 넣으면 도구를 부르지 않고 인사만 돌아옵니다. `description`이 중요한 이유가 이것입니다. LLM은 그 한 문장만 보고 판단합니다.

이 두 번의 왕복을 돌리는 건 `ToolCallingAdvisor`입니다. 위 예제에서 `.tools(WeatherTools())`를 넘긴 순간 자동으로 등록되므로, 정작 코드에는 드러나지 않습니다.

무엇보다 **이 루프는 Advisor 체인 안에서 돕니다**(큰 그림에서 필터에 빗댄 그 자리). 도구 실행 앞뒤에 다른 Advisor를 끼워 넣어 로깅·권한 확인·마스킹 같은 것을 걸 수 있습니다. 만드는 방법은 §8에서 다룹니다.

체인은 **재진입**도 지원합니다. Advisor가 하위 체인으로 다시 들어갈 수 있어서 도구 호출 루프·Structured Output 재시도(`StructuredOutputValidationAdvisor`)·평가 루프가 모두 이 하나의 메커니즘 위에서 돌아갑니다.

> **제어의 역전(IoC).** 기존 백엔드는 개발자가 모든 분기를 작성했습니다(`if A else B`). Tool Calling은 "도구를 제공할 테니 목표를 달성하라"고 **위임**하는 방식입니다. LLM이 오케스트레이터가 되고 개발자는 검증된 도구만 제공합니다. 이것이 **에이전트의 본질**입니다.

### 5. RAG — 회사 내부 문서에 근거해 답하기

**LLM은 특정 조직의 내부 문서를 알지 못합니다.** 내부 정책을 물으면 사실과 다른 내용을 생성합니다(할루시네이션). RAG는 답하기 전에 관련 문서를 검색해 프롬프트에 끼워 넣습니다. 모델은 그대로 두고 지식만 주입하는 방식입니다.

- **Retrieve(검색)** — 질문과 의미가 가까운 문서를 `VectorStore`에서 찾습니다.
- **Augment(증강)** — 찾은 문서를 질문과 함께 프롬프트에 붙입니다.
- **Generate(생성)** — LLM이 그 문서를 근거로 답합니다.

Spring AI에서는 `QuestionAnswerAdvisor` 하나만 붙이면 이 흐름이 자동으로 돌아갑니다.

```kotlin
chatClient.prompt()
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())  // 이 한 줄이 RAG
    .user("우리 환불 정책이 뭐야?")
    .call()
    .content()
```

증강(Augment)의 실체는 **검색한 문서를 프롬프트 텍스트에 붙이는 것**입니다. LLM이 실제로 받는 프롬프트는 이렇게 바뀝니다.

```
아래 컨텍스트를 근거로 답하라.
[컨텍스트: "환불은 구매 후 7일 이내… 디지털 상품은 제외…"]
[질문] 우리 환불 정책이 뭐야?
```

#### VectorStore — 검색을 담당하는 부품

`VectorStore`는 문서를 **임베딩(의미를 담은 벡터)** 으로 저장하고 유사도로 검색하는 **공통 인터페이스**입니다.

```kotlin
vectorStore.add(documents)                    // 문서 저장 (임베딩 자동 생성)
vectorStore.similaritySearch("환불 정책")       // 의미가 가까운 문서 top-N 검색
```

실제 저장소(pgvector, Redis, Chroma 등)는 구현체를 갈아 끼우는 식이라 어느 것을 쓰든 앱 코드는 그대로입니다. 스타터만 추가하면 됩니다. (`spring-ai-starter-vector-store-pgvector` 등)

가장 간단한 구현은 별도 설치가 필요 없는 **인메모리 `SimpleVectorStore`** 입니다. `EmbeddingModel`을 주입받아 만들고 문서를 넣어 두면 됩니다.

```kotlin
@Bean
fun vectorStore(embeddingModel: EmbeddingModel): VectorStore {
    val store = SimpleVectorStore.builder(embeddingModel).build()
    store.add(
        listOf(
            Document("환불은 구매 후 7일 이내에 가능합니다. 단순 변심으로 인한 환불은 왕복 배송비를 구매자가 부담합니다."),
            Document("디지털 상품은 다운로드하거나 사용한 이력이 있으면 환불이 제한됩니다."),
            Document("고객센터 영업시간은 평일 09시부터 18시까지이며, 주말과 공휴일은 휴무입니다."),
        ),
    )
    return store
}
```

여기서는 문서를 문자열로 직접 박았지만, 실제로는 파일에서 읽어 넣는 경우가 많습니다. Spring AI는 소스별 `DocumentReader`를 제공합니다 — `TextReader`(텍스트), `PagePdfDocumentReader`(PDF), `MarkdownDocumentReader`(마크다운), `JsonReader`(JSON). 긴 문서는 `TokenTextSplitter`로 잘라 넣습니다.

```kotlin
val reader = PagePdfDocumentReader("classpath:/policy.pdf")
vectorStore.accept(TokenTextSplitter().apply(reader.read()))   // 읽기 → 분할 → 저장
```

#### 임베딩 모델 — 기본값은 어디서 오나

`vectorStore.add(documents)`가 임베딩을 "자동 생성"한다고 했는데, 그 벡터를 만드는 모델은 무엇일까요?

> **Spring AI에 전역 기본 임베딩 모델이 따로 있는 게 아니라, 클래스패스에 올라온 모델 계열 스타터(`spring-ai-starter-model-*`)가 정합니다.**

예컨대 `spring-ai-starter-model-transformers`를 넣으면 `TransformersEmbeddingModel`이 자동 구성됩니다. 

> 기본 모델 **all-MiniLM-L6-v2**

ONNX 모델을 앱 안에서 직접 돌리므로 **Anthropic 외 추가 API 키가 필요 없고** 첫 기동에만 모델을 내려받아 로컬에 캐시합니다. 

### 6. ToolSearch — 도구가 수백 개일 때 (2.0 신규)

**도구가 수백 개여도 질문에 맞는 것만 골라 노출합니다.** 도구를 전부 프롬프트에 넣으면 토큰이 커지고 LLM의 선택 정확도가 떨어집니다. 

2.0의 `ToolSearchToolCallingAdvisor`는 이 문제를 점진적 노출(progressive tool disclosure)로 풉니다.

먼저 검색 대상이 될 도구들입니다. 서로 무관한 도구 넷을 한 객체에 담았습니다 — `description`이 곧 검색되는 텍스트라, ToolIndex는 이 문장들에서 질문과 맞는 것을 찾습니다.

```kotlin
class ManyTools {                                          // 서로 무관한 도구 4개
    @Tool(description = "특정 도시의 현재 날씨를 조회한다")
    fun getWeather(city: String) = "$city: 맑음, 22도"         // 날씨

    @Tool(description = "두 수를 더한다")
    fun add(a: Int, b: Int) = a + b                          // 계산

    @Tool(description = "특정 사용자의 최근 주문 상태를 조회한다")
    fun getOrder(userId: String) = "$userId 의 최근 주문: 배송중"  // 주문

    @Tool(description = "현재 서버 시간을 반환한다")
    fun now() = LocalDateTime.now().toString()               // 시간
}
```

Advisor를 직접 구성해 필요한 호출에만 붙입니다. 부품 두 개만 주면 됩니다 — **도구를 어떻게 찾을지**(`ToolIndex`: 정규식·Lucene·벡터 중 택1)와 **찾은 도구를 실행할 것**(`ToolCallingManager`, §4에서 본 그 실행 루프).

```kotlin
private val toolSearchAdvisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(RegexToolIndex())                     // regex / lucene / vector
    .toolCallingManager(ToolCallingManager.builder().build())
    .build()

chatClient.prompt()
    .advisors(toolSearchAdvisor)
    // 세션별 도구 인덱스를 캐시하므로 세션 ID가 필요하다
    .advisors { it.param(ChatMemory.CONVERSATION_ID, "toolsearch-demo") }
    .tools(ManyTools())
    .user("사용자 준의 최근 주문 상태 알려줘")
    .call()
    .content()
```

RAG가 문서를 검색해 주입하듯 도구를 검색해 주입하는 셈입니다.

`spring.ai.chat.client.tool-search-advisor.enabled=true` 프로퍼티로 자동 구성하는 경로도 있습니다. 다만 2.0.0 기준으로 적용 범위가 전역이고 starter가 BOM에 없는 등 제약이 있어 이 문서는 수동 구성을 권합니다.

### 7. MCP — 도구의 상호운용 표준 (2.0 핵심)

**애플리케이션 밖에 흩어진 도구까지 표준 프로토콜로 연결합니다.**

앞에서 다룬 도구는 모두 애플리케이션 내부 함수였습니다. 실제 도구는 여러 곳에 흩어져 있습니다(자체 서비스, 다른 팀 서비스, 외부 SaaS). MCP(Model Context Protocol)는 도구·리소스·프롬프트를 노출하는 표준 프로토콜입니다. 규격만 맞추면 제공자·소비자가 서로의 구현을 몰라도 연결됩니다.

```mermaid
graph LR
    Agent["내 에이전트<br/>(MCP Client)"] --> P((MCP 프로토콜))
    P --> S1["우리 팀 서비스<br/>(MCP Server)"]
    P --> S2["옆 팀 서비스<br/>(MCP Server)"]
    P --> S3["외부 SaaS<br/>(MCP Server)"]
    style P fill:#ffe08a,stroke:#e0a800,stroke-width:2px
```

Spring AI 2.0은 MCP를 1급으로 지원합니다.

- **서버 애노테이션** — `@McpTool` / `@McpResource` / `@McpPrompt` / `@McpComplete`. `@Tool`처럼 선언만 하면 함수가 MCP 서버로 노출되고 JSON 스키마는 자동 생성됩니다.
- **클라이언트** — 다른 곳에서 만든 MCP 서버를 내 에이전트의 도구로 가져다 씁니다.
- **전송 내장** — WebMVC/WebFlux 전송이 Spring AI에 들어 있어 별도 SDK 의존성이 필요 없습니다. 기본 전송은 Streamable HTTP입니다. 로컬 프로세스끼리 붙일 때 쓰는 STDIO도 그대로 있습니다.

```kotlin
@Component
class CalculatorTools {
    @McpTool(name = "add", description = "두 수를 더한다")
    fun add(
        @McpToolParam(description = "첫 번째 수") a: Int,
        @McpToolParam(description = "두 번째 수") b: Int,
    ): Int = a + b
}
```

이 선언만으로 MCP 클라이언트가 `tools/list`로 받는 스키마가 자동으로 만들어집니다. 애노테이션이 곧 JSON이 됩니다 — 이름·설명은 애노테이션에서, 파라미터 타입(`Int` → `integer`)과 필수 여부는 시그니처에서 유도됩니다.

```json
{
  "name": "add",
  "description": "두 수를 더한다",
  "inputSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "properties": {
      "a": { "type": "integer", "format": "int32", "description": "첫 번째 수" },
      "b": { "type": "integer", "format": "int32", "description": "두 번째 수" }
    },
    "required": ["a", "b"]
  }
}
```

§2 Structured Output과 같은 원리입니다. 거기선 data class로 스키마를 만들어 LLM에 보냈고, 여기선 메서드 시그니처로 스키마를 만들어 MCP 클라이언트에 노출합니다. 개발자가 스키마 JSON을 직접 쓰는 일은 없습니다.

> 지금까지 이야기한 이식성은 "벤더를 바꿔도 코드 유지"였습니다. MCP는 이식성을 **"도구와 에이전트가 벤더·언어를 넘어 연결된다"** 로 확장합니다. 한 서비스가 다른 에이전트의 도구가 되고, 반대로 다른 도구가 그 에이전트의 수단이 됩니다.

### 8. Advisor 직접 만들기

**RAG, 대화 메모리, ToolSearch는 모두 Advisor였습니다.** `QuestionAnswerAdvisor`, `MessageChatMemoryAdvisor`, `ToolSearchToolCallingAdvisor` — 앞에서 `.advisors(...)`로 붙인 것들이 전부 같은 확장점입니다. 직접 만들 수도 있습니다.

큰 그림에서 서블릿 필터에 빗댄 그 자리입니다. Advisor는 `ChatClient` 호출을 가로채는 체인입니다. 요청이 모델로 나가기 전과 응답이 돌아온 후에 개입합니다.

```mermaid
graph LR
    App["ChatClient 호출"] --> A1["Advisor 1<br/>before"]
    A1 --> A2["Advisor 2<br/>before"]
    A2 --> LLM["LLM"]
    LLM --> B2["Advisor 2<br/>after"]
    B2 --> B1["Advisor 1<br/>after"]
    B1 --> Res["응답"]
    style A1 fill:#ffe08a,stroke:#e0a800,stroke-width:2px
    style A2 fill:#ffe08a,stroke:#e0a800,stroke-width:2px
    style B2 fill:#ffe08a,stroke:#e0a800,stroke-width:2px
    style B1 fill:#ffe08a,stroke:#e0a800,stroke-width:2px
```

`BaseAdvisor`를 구현하면 `before`/`after` 두 개만 채우면 됩니다.

```kotlin
// CARD_NUMBER(정규식) · MASKED_PROMPT(컨텍스트 키) 상수 정의는 생략. 전체는 demo/ 참고
class PiiMaskingAdvisor(private val order: Int = 0) : BaseAdvisor {

    // 모델로 나가기 전 — 카드번호를 마스킹한다
    override fun before(request: ChatClientRequest, chain: AdvisorChain): ChatClientRequest {
        val original = request.prompt().userMessage.text ?: return request
        val masked = CARD_NUMBER.replace(original, "****-****-****-****")
        if (masked == original) return request

        return request.mutate()
            .prompt(request.prompt().augmentUserMessage { it.mutate().text(masked).build() })
            .context(MASKED_PROMPT, masked)   // 호출부가 확인할 수 있게 기록
            .build()
    }

    // 모델에서 돌아온 후 — 여기선 통과
    override fun after(response: ChatClientResponse, chain: AdvisorChain) = response

    override fun getOrder(): Int = order
}
```

```kotlin
chatClient.prompt()
    .advisors(PiiMaskingAdvisor(order = 0), SimpleLoggerAdvisor.builder().build())
    .user(message)
    .call()
    .chatClientResponse()
```

- **실행 순서** — `getOrder()`가 작은 쪽이 먼저 `before`를 실행합니다. `after`는 역순입니다.
- **컨텍스트** — `ChatClientRequest`/`ChatClientResponse`는 `context` 맵을 함께 나릅니다. Advisor가 남긴 값을 호출부에서 `chatClientResponse().context()`로 읽을 수 있습니다.
- **기본 제공** — `SimpleLoggerAdvisor`는 요청·응답을 로그로 남깁니다(DEBUG). 별도 구현 없이 붙이기만 하면 됩니다.

이 확장점이 있는 덕분에 감사 로그·마스킹·비용 측정·금칙어 필터 같은 **횡단 관심사를 비즈니스 코드 밖에서** 처리할 수 있습니다. 문서 마지막에서 이야기하는 "가드레일"이 실제로 구현되는 자리이기도 합니다.

### 9. 관측성

**LLM은 토큰 단위로 과금됩니다.** 호출 한 번의 비용이 입력·출력 길이에 따라 달라지므로 사용량 추적은 선택이 아니라 운영 요건입니다.

읽는 방법은 두 가지입니다. 호출 단위로 직접 읽거나, Micrometer 지표로 모으거나.

#### 호출 단위로 읽기

토큰 사용량은 `.content()` 대신 `.chatResponse()`로 받아 메타데이터에서 읽습니다.

```kotlin
val response = chatClient.prompt().user(msg).call().chatResponse()
    ?: error("Model returned no response")

val usage = response.metadata.usage
usage.promptTokens                // 입력 토큰
usage.completionTokens            // 출력 토큰
usage.totalTokens                 // 합계
usage.cacheReadInputTokens        // 캐시로 읽은 토큰 (2.0 통합 지표)
```

캐시 토큰처럼 벤더마다 이름이 다르던 지표도 `Usage` 인터페이스가 흡수합니다. 벤더가 바뀌어도 위 코드는 그대로입니다.

#### 지표로 모으기

`ChatObservationAutoConfiguration`이 Micrometer Observation을 자동 구성합니다. `MeterRegistry` 빈이 있으면(actuator를 넣으면) `ChatModelMeterObservationHandler`가 호출별 토큰 사용량과 소요 시간을 지표로 기록합니다. 대시보드·알림은 기존 Micrometer 파이프라인을 그대로 씁니다.

프롬프트와 응답 본문은 기본적으로 로그에 남지 않습니다. 필요할 때만 켭니다.

```properties
spring.ai.chat.observations.log-prompt=true       # 기본 false
spring.ai.chat.observations.log-completion=true   # 기본 false
```

켜면 `ChatModelPromptContentObservationHandler`가 모델에 나간 프롬프트 전문을 INFO로 찍습니다. RAG가 끼워넣은 검색 문서, 메모리가 붙인 과거 대화가 여기서 그대로 보이므로 디버깅에 특히 유용합니다.

> **actuator가 전제입니다.** 없이 프로퍼티만 켜면 NOOP registry로 폴백해 오류 없이 조용히 아무것도 찍히지 않습니다.

> 프롬프트·응답 전문에는 개인정보나 사내 문서가 그대로 남을 수 있으므로 운영 환경에서는 신중해야 합니다. 기본값이 `false`인 이유이기도 합니다.

---

## 직접 만들어 보기 — 사내 문서 Q&A 에이전트

앞의 기능들을 하나의 `ChatClient`에 결합한 예시입니다. RAG(사내 문서), 대화 메모리, 실제 조회 도구(Tool)를 함께 얹으면 에이전트가 됩니다.

도구부터 — §4의 `WeatherTools`와 똑같은 모양입니다.

```kotlin
class OrderTools {
    @Tool(description = "특정 사용자의 최근 주문 상태를 조회한다")
    fun getOrder(@ToolParam(description = "사용자 ID") userId: String): String =
        "$userId 의 최근 주문: 2026-07-14 결제, 배송 완료"
}
```

셋을 하나의 빌더에 얹습니다.

```kotlin
@Bean
fun supportAgent(
    builder: ChatClient.Builder,
    vectorStore: VectorStore,
    chatMemory: ChatMemory,
): ChatClient =
    builder
        .defaultSystem("너는 사내 고객지원 상담원이다. 문서에 근거해 답하라.")
        .defaultAdvisors(
            QuestionAnswerAdvisor.builder(vectorStore).build(),      // RAG: 사내 문서 근거
            MessageChatMemoryAdvisor.builder(chatMemory).build(),    // 대화 맥락 유지
        )
        .defaultTools(OrderTools())                                  // 실제 주문 조회 도구
        .build()
```

부르는 쪽입니다. **대화 ID를 넘기는 것을 빼먹으면 안 됩니다**(§3) — 메모리 Advisor를 얹은 순간부터 필수입니다.

```kotlin
@RestController
class SupportController(private val supportAgent: ChatClient) {

    @PostMapping("/support")
    fun ask(@RequestParam conversationId: String, @RequestParam message: String): String? =
        supportAgent.prompt()
            .user(message)
            .advisors { it.param(ChatMemory.CONVERSATION_ID, conversationId) }
            .call()
            .content()
}
```

`"지난주 주문 환불돼요?"` 한 마디에 이렇게 돌아갑니다.

1. **모델 호출 전** — 메모리가 이전 대화를 붙이고("지난주 주문"이 무엇인지), RAG가 환불 정책 문서를 검색해 근거로 붙입니다. 둘 다 Advisor라 프롬프트를 채우는 단계입니다.
2. **모델이 판단** — 정책만으로는 답할 수 없으니 `getOrder(userId)`를 호출해 달라고 요청합니다.
3. **앱이 실행 → 재호출** — 도구 결과를 붙여 다시 물으면 모델이 정책과 실제 주문 데이터를 함께 근거로 답을 만듭니다.

개발자가 만드는 것은 검증된 도구와 신뢰할 수 있는 지식이고 그것들을 언제 어떻게 조합할지는 LLM이 정합니다.

---

## 1.x → 2.0 한눈에

| 영역           | 1.x                | 2.0                                         |
| ------------ | ------------------ | ------------------------------------------- |
| 런타임          | Spring Boot 3      | **Spring Boot 4 / Framework 7 / Jackson 3** |
| 널 안전성        | 명시 없음              | **JSpecify `@Nullable`** (Kotlin에서 `String?`로 드러남) |
| Tool Calling | 실행 루프가 모델 구현 내부<br>(`internalToolExecutionEnabled`) | `ToolCallingAdvisor`로 분리, Advisor 체인에 편입 (자동 등록) |
| 대규모 도구       | —                  | **ToolSearch** (점진적 노출)                     |
| MCP          | SDK 별도             | **Spring AI 1급 편입** (`@McpTool`, 전송 내장)     |
| 대화 메모리       | 빌더에 conversationId | 호출 시점 `CONVERSATION_ID` 필수                  |
| 토큰·캐시 지표     | 벤더별 제각각            | 통합 `Usage` API                              |
| 벤더           | 변형 다수              | 정리 (예: OpenAI 3종 → 1종)                      |

---

## 정리 — 백엔드 개발자의 역할

AI를 도입하면 백엔드 개발자는 더 이상 모든 분기를 직접 작성하지 않습니다. 대신 네 가지를 합니다.

- **검증된 도구를 만든다** — LLM이 호출할 안전한 함수(`@Tool`), 필요하면 `@McpTool`로 표준 노출까지.
- **지식을 공급한다** — RAG로 신뢰할 수 있는 데이터를 주입.
- **가드레일을 친다** — 위험 작업은 도구로 만들지 않거나 노출을 통제(ToolSearch)하거나 사람 승인(human-in-the-loop)을 끼운다.
- **출력을 검증한다** — LLM 출력을 외부 입력처럼 다룬다.

네 가지는 결국 같은 질문입니다 — **어디까지 맡기고, 어디에 선을 그을 것인가.** LLM이 오케스트레이터라면 백엔드는 그것이 움직일 **범위와 규칙**을 정합니다. 에이전트가 커질수록 답하기 어려워집니다.

---

## 참고

- Spring AI Reference (2.0): https://docs.spring.io/spring-ai/reference/2.0/
- Spring AI 2.0 업그레이드 노트: https://docs.spring.io/spring-ai/reference/upgrade-notes.html#upgrading-to-2-0-0
- Spring AI 2.0.0 GA 블로그: https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now
- Spring AI 1.0 GA 블로그: https://spring.io/blog/2025/05/20/spring-ai-1-0-GA-released/
- Spring AI 1.1 GA 블로그: https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/
- 임베딩 모델 `all-MiniLM-L6-v2`: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2

