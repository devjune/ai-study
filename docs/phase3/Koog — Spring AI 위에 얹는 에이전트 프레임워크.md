# Koog — Spring AI 위에 얹는 에이전트 프레임워크

앞 편에서는 에이전틱 패턴 21개를 Spring AI의 원시 재료로 직접 조립했습니다. `ChatClient` · `@Tool` · `.entity()` 같은 범용 기능의 조합이었고, 전용 타입이 있는 장은 다섯뿐이었습니다. 나머지 열여섯은 전부 손으로 짠 코드입니다.

이 문서는 반대편을 봅니다. **같은 일을 전용 에이전트 프레임워크는 어떻게 해 주는가.** 대상은 JetBrains의 [Koog](https://github.com/JetBrains/koog)입니다. Kotlin 네이티브라 기존 코드베이스와 언어가 같고, 공식 Spring AI 브리지가 있어 앞 편에서 쓰던 Ollama 빈을 그대로 물려받습니다.

목표는 앞 편과 대칭입니다. 손조립의 경계를 봤으니, 이번에는 **프레임워크 추상화의 경계**를 봅니다. 장은 Koog의 기능 순서로 갑니다. 앞 편 패턴과의 대응은 마지막 장에서 표로 회수합니다.

## 9 개 장을 한 눈에

| 장   | 주제                                               |
| --- | ------------------------------------------------ |
| 1   | Koog는 무엇이고 왜 — 손조립과 무엇이 다른가                      |
| 2   | 연결 — 기존 `ChatModel` 빈이 `PromptExecutor`가 되기까지    |
| 3   | 단일 에이전트 — `AIAgent` 기본형                          |
| 4   | 그래프 전략 — 노드·엣지로 흐름 짜기                            |
| 5   | 도구 — 실행은 Koog가 하고 Spring AI는 스키마만 받는다            |
| 6   | 메모리 — `ChatMemoryRepository` 어댑터와 제약, 히스토리 압축    |
| 7   | RAG — `EmbeddingModel` · `VectorStore` 어댑터       |
| 8   | MCP 연동                                           |
| 9   | 마무리 — 손으로 만든 것이 Koog에선 무엇이 되나                    |

## 1장 Koog는 무엇이고 왜

Koog는 JetBrains가 만든 Kotlin 에이전트 프레임워크입니다. 에이전트를 굴리는 데 필요한 부품이 이미 타입으로 들어 있습니다. 모델을 부르는 실행기, 도구 목록, 대화 이력, 흐름을 그리는 그래프, 그 위에 얹는 관측 기능까지 프레임워크가 들고 있습니다.

앞 편에서 이것들은 전부 애플리케이션 코드였습니다. 자기 검토는 `while` 루프에 판정 프롬프트와 종료 조건을 직접 붙인 것이고, 라우팅은 모델의 판정을 받아 `when`으로 갈라낸 것이고, 멀티 에이전트는 서비스 클래스를 역할별로 나눠 순서대로 부른 것입니다. 원시 재료만으로도 21개가 전부 만들어지긴 했습니다. 다만 만들어진 것이 **패턴이라는 이름의 우리 코드**였을 뿐입니다. 루프 상한을 빠뜨리면 거기서 무한히 돕니다.

프레임워크로 넘어가면 그 자리에 이미 만들어진 물건이 들어옵니다. 흐름은 코드의 제어문이 아니라 **노드와 엣지로 선언**합니다. 한 단계가 실패했을 때의 재시도와 상태 복원은 실행기 쪽 관심사가 됩니다. 대화가 길어지면 이력을 요약해 접는 **히스토리 압축**이 옵션으로 있습니다. 어느 노드에서 몇 번 모델을 불렀는지 보는 **관측성**도 기능으로 켭니다. 손으로 짜면 하나하나가 며칠짜리인 것들입니다.

대신 잃는 것이 있습니다. 프레임워크는 그 아래 것을 가립니다. 여기서는 가리는 물건이 하나 더 끼어 있습니다 — Spring AI를 Koog에 물리는 **브리지 어댑터**입니다. 어댑터는 두 쪽의 교집합만 옮깁니다. 기존 빈을 재사용한다는 말은 그 빈의 기능 전부를 쓴다는 뜻이 아닙니다. 대화 이력 어댑터는 도구 호출과 추론 메시지를 저장하지 않고(6장), 도구는 정의만 Spring AI 쪽 규약을 따르고 실행은 Koog가 가져갑니다(5장).

그리고 첫 제약은 코드를 한 줄 쓰기도 전에, 버전 자리에서 나옵니다. 바로 다음 장입니다.

## 2장 연결 — 기존 `ChatModel` 빈이 `PromptExecutor`가 되기까지

Koog에서 모델을 부르는 입구는 `PromptExecutor`입니다. Spring AI에서 그 자리에 있던 것이 `ChatModel`입니다. 브리지가 하는 일은 하나입니다 — 컨텍스트에 이미 떠 있는 `ChatModel` 빈을 찾아 `PromptExecutor` 빈으로 내놓는 것입니다.

### 의존성은 두 개

```toml
koogSpringAi = "1.2.0-beta"
koogAgents = "1.2.0"
```

```kotlin
// Spring AI의 ChatModel을 Koog의 PromptExecutor로 감싸는 공식 브리지 스타터
implementation(libs.koog.spring.ai.starter.model.chat)
// AIAgent는 브리지 스타터가 아니라 이 아티팩트에 있다
implementation(libs.koog.agents.jvm)
```

좌표는 `ai.koog:koog-spring-ai-starter-model-chat`과 `ai.koog:koog-agents-jvm`입니다. 두 가지를 짚어 둡니다.

**브리지 스타터는 1.0.0부터 정식 릴리스가 없습니다.** Maven Central의 이 아티팩트 이력은 `0.7.x` · `0.8.0`까지 정식으로 올라오다가, `1.0.0-beta` 계열부터 최신 `1.2.0-beta`까지 전부 beta입니다. 1.x 라인에는 아직 정식 버전이 없습니다.

**`AIAgent`는 스타터가 끌어오지 않습니다.** 브리지 스타터의 POM이 데려오는 것은 `koog-spring-ai-common` · `prompt-executor-clients-jvm` · `prompt-executor-model-jvm`까지입니다. 3장부터 쓰는 `AIAgent`는 `ai.koog.agents.core.agent` 패키지에 있고, 이 패키지가 든 `koog-agents-jvm`은 전이 의존성에 없습니다. 직접 넣어야 합니다. 브리지는 실행기까지만 놔 주고 에이전트는 별도라는 뜻입니다.

### 버전 — 브리지가 겨냥한 스택을 따라가야 합니다

phase3의 코드만 Spring Boot와 Spring AI 버전이 앞 편과 다릅니다.

| 축          | phase1 · phase2 | phase3   |
| ---------- | --------------- | -------- |
| Spring Boot | 4.0.0          | 3.5.14   |
| Spring AI   | 2.0.1          | 1.1.6    |
| Kotlin      | 2.2.21         | 2.2.21   |

취향이 아니라 강제입니다. `koog-spring-ai-common-1.2.0-beta.pom`은 `spring-ai-bom:1.1.6`과 `spring-boot-dependencies:3.5.14`를 명시적으로 임포트합니다. 브리지는 그 조합에서 빌드되고 테스트됐습니다. Spring AI 2.0.1에 올리면 컴파일은 전부 통과하고, 실제로 Ollama를 부르는 순간 죽습니다.

```
ai.koog.prompt.executor.clients.LLMClientException: Error from client: SpringAiLLMClient
ChatModel.call() failed: class org.springframework.ai.chat.prompt.DefaultChatOptions cannot be cast to class org.springframework.ai.ollama.api.OllamaChatOptions
```

Koog의 `SpringAiLLMClient`는 벤더 중립 옵션인 `DefaultChatOptions`를 만들어 `chatModel.call(prompt)`에 그대로 넘깁니다. Spring AI 2.0.1의 `OllamaChatModel`은 넘어온 옵션을 곧바로 `OllamaChatOptions`로 캐스트합니다. 1.1.x에서는 이 경계에서 포터블 옵션을 벤더 옵션으로 변환해 주던 관용이 2.0 계열에서 사라졌습니다. Koog 버전을 `1.1.1-beta`로 낮춰도 같은 자리에서 같은 예외가 납니다. 두 버전이 선언한 Spring AI 타깃이 똑같기 때문입니다. Spring AI 2.x를 겨냥한 Koog 릴리스는 아직 없습니다.

Kotlin은 손대지 않았습니다. Koog 문서는 2.3.10 이상을 적어 두지만 2.2.21로 컴파일도 테스트도 그대로 통과합니다.

**이것이 1장에서 예고한 어댑터 제약의 첫 사례입니다.** "기존 빈을 그대로 재사용한다"는 문장에는 조건이 붙습니다 — 그 빈이 브리지가 겨냥한 버전일 때만입니다. 프레임워크는 아래층을 가려 주는 대신, 아래층의 버전을 자기 쪽에 맞추라고 요구합니다.

### 설정은 앞 편 그대로

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=qwen3:4b-instruct
```

Koog 전용 프로퍼티는 없습니다. 브리지는 Spring AI가 이미 만들어 둔 `ChatModel` 빈을 집어 갈 뿐이라 모델 지정도 접속 주소도 Spring AI 쪽 키를 그대로 씁니다. 두 키 모두 1.1.6에 그대로 있습니다.

### 조립 구조

```
Spring AI ChatModel 빈  →  Koog SpringAiLLMClient  →  PromptExecutor 빈
```

스타터의 오토컨피규레이션이 이 세 칸을 이어 붙입니다. 애플리케이션 코드가 할 일은 마지막 칸을 주입받는 것뿐입니다.

```kotlin
@SpringBootTest
class ContextLoadsTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor

    @Test
    fun `Koog 스타터가 Spring AI ChatModel을 PromptExecutor로 감싼다`() {
        assertNotNull(promptExecutor)
    }
}
```

가운데 `SpringAiLLMClient`가 어댑터 본체입니다. Koog 쪽 프롬프트 타입을 Spring AI의 `Prompt`로 옮기고, 돌아온 응답을 Koog 메시지로 되돌립니다. 앞의 `ClassCastException`이 난 자리도 여기입니다.

경계가 갈리는 지점이 하나 더 있습니다. **도구를 실제로 실행하는 쪽은 Koog입니다.** Spring AI는 도구 스키마만 받아 모델에 실어 보냅니다. 앞 편에서 `@Tool` 함수를 `ChatClient`가 호출까지 대신해 주던 것과 다릅니다. 모델이 도구를 부르겠다고 답하면 그 호출을 받아 함수를 돌리고 결과를 다시 프롬프트에 넣는 루프가 전부 Koog 쪽에 있습니다. 5장에서 봅니다.

### 모델은 직접 정의합니다

Koog는 모델을 `LLModel` 값으로 다룹니다. 자주 쓰는 모델은 프리셋 상수가 준비돼 있는데, `qwen3:4b-instruct`는 목록에 없습니다. 없으면 만들어 씁니다.

```kotlin
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

`capabilities`가 이 타입의 핵심입니다. 이 모델에 도구를 붙여도 되는지, 온도를 조절할 수 있는지, 구조화 출력을 요구해도 되는지를 값으로 적어 둡니다. Spring AI가 문자열 모델 이름 하나로 끝내던 자리에 능력 선언이 들어온 셈입니다. `contextLength`와 `maxOutputTokens`도 인자로 있지만 기본값이 있어 생략됩니다.

`provider`는 `LLMProvider.Ollama`입니다. 다만 이 값은 프롬프트를 어떤 규약으로 직렬화할지 정하는 표시일 뿐, 실제 접속은 브리지 뒤의 Spring AI `OllamaChatModel`이 합니다. Ollama 주소를 Koog에 알려 줄 필요가 없는 이유입니다.

이제 실행기와 모델이 준비됐습니다. 다음 장에서 이 둘로 에이전트 하나를 돌립니다.

## 3장 단일 에이전트 — `AIAgent` 기본형

실행기와 모델을 그대로 받아 에이전트 하나를 만듭니다. 최소형은 이만큼입니다.

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

주입받는 것은 스타터가 만들어 준 `PromptExecutor` 하나입니다. 모델은 `LLModel` 값으로 따로 넘깁니다. 실행기와 모델이 따로 놀기 때문에 같은 실행기에 다른 모델을 물려 에이전트를 여러 개 만들 수 있습니다.

### `AIAgent`는 클래스가 아니라 팩토리 함수입니다

생성자 호출처럼 보이지만 아닙니다. `ai.koog.agents.core.agent.AIAgent`는 추상 클래스이고, 위에서 부른 `AIAgent(...)`는 같은 패키지의 최상위 함수입니다. 돌려주는 것은 `GraphAIAgent`입니다.

전략을 한 줄도 쓰지 않았는데 이미 그래프라는 뜻입니다. 오버로드 중에 `AIAgentGraphStrategy`를 받는 것이 따로 있고, 그 자리를 비우면 기본 그래프가 들어갑니다. 4장에서 그 자리를 직접 채웁니다.

### 파라미터

온도와 반복 상한을 지정하면 이렇게 됩니다.

```kotlin
val agent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = KoogModels.QWEN3_4B_INSTRUCT,
    systemPrompt = "숫자만 답한다. 다른 말은 붙이지 않는다.",
    temperature = 0.2,
    maxIterations = 5,
)
```

`temperature`는 `Double?`, `maxIterations`는 `Int`이고 둘 다 기본값이 있습니다. 앞 편에서 온도는 호출할 때 `ChatOptions`에 실어 보내는 값이었습니다. 여기서는 에이전트를 만들 때 박히는 설정입니다.

눈에 띄는 쪽은 `maxIterations`입니다. 1장에서 손조립의 위험으로 적었던 것 — 루프 상한을 빠뜨리면 거기서 무한히 돈다 — 이 프레임워크에서는 생성자 인자 자리에 있습니다. 모델을 부르고 도구를 돌리고 다시 부르는 왕복의 상한이고, 안 쓰면 기본값이 들어갑니다. 빠뜨릴 수 있는 자리에서 빠뜨릴 수 없는 자리로 옮긴 셈입니다.

### `run()`은 suspend입니다

코루틴 밖에서 부르면 컴파일이 거부합니다.

```
Suspend function 'suspend fun run(agentInput: String, sessionId: String? = null): String'
can only be called from a coroutine or another suspend function.
```

에러 메시지에 시그니처가 그대로 나옵니다. 입력은 `String`, 둘째 인자 `sessionId`는 생략 가능, 반환도 `String`입니다. 그래서 테스트는 `runBlocking`으로 감쌉니다.

```kotlin
fun `temperature와 maxIterations를 지정한다`() = runBlocking {
```

Kotlin 코루틴을 쓰는 코드에서는 자연스럽습니다. 다만 앞 편처럼 블로킹 호출이 기본인 자리에 얹으면 경계가 하나 생깁니다. 부르는 쪽도 `suspend`가 되거나, 아니면 `runBlocking`으로 막아야 합니다.

### 앞 편과 한 줄 대조

```kotlin
chatClient.prompt().user(질문).call().content()  // phase2
agent.run(질문)                                  // phase3
```

빌더 체인이 사라지고 호출 하나가 남았습니다. 대신 체인의 각 칸에 넣던 것 — 시스템 프롬프트, 옵션, 도구 — 이 전부 에이전트를 만드는 시점으로 옮겨 갔습니다. `ChatClient`는 부를 때 조립하고, `AIAgent`는 만들 때 조립합니다.

한 가지 더 있습니다. 왼쪽은 모델을 한 번 부르고 끝납니다. 오른쪽은 `maxIterations`가 인자로 있다는 데서 드러나듯, 한 번으로 끝난다는 보장이 없습니다. 겉보기에 같은 한 줄이지만 안에서 도는 횟수가 다릅니다.

다음 장에서 그 안을 열어 노드와 엣지로 직접 그립니다.

## 4장 그래프 전략 — 노드·엣지로 흐름 짜기

3장에서 전략 자리를 비워 두고 기본 그래프를 받았습니다. 이번엔 그 자리를 채웁니다. 전략은 **노드와 엣지로 된 그래프**이고, 노드가 한 단계의 일을, 엣지가 다음 단계로 가는 조건과 값 변환을 맡습니다.

### 두 노드를 잇는 최소 전략

앞 편 1장의 프롬프트 체이닝 — 모델을 부르고, 그 답을 다음 프롬프트에 넣어 다시 부르는 것 — 을 그래프로 옮기면 이만큼입니다.

```kotlin
val chain = strategy<String, String>("two-step") {
    val draft by nodeLLMRequest()
    val polish by nodeLLMRequest()

    edge(nodeStart forwardTo draft)
    edge(draft forwardTo polish transformed { "그 숫자에 10을 더하면?" })
    edge(polish forwardTo nodeFinish transformed { it.textContent() })
}
val agent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = KoogModels.QWEN3_4B_INSTRUCT,
    strategy = chain,
    systemPrompt = "숫자만 답한다. 다른 말은 붙이지 않는다.",
    temperature = 0.2,
)
val result = agent.run("2 더하기 3은?")
```

`"2 더하기 3은?"`을 넣으면 `15`가 나옵니다. 첫 노드가 `5`를 만들고, 둘째 노드가 같은 대화 위에서 거기에 10을 더합니다. 값이 두 노드를 실제로 통과했다는 증거이기도 합니다.

읽을 대목이 다섯 있습니다.

`strategy<String, String>("two-step")`의 두 타입 인자는 그래프 전체의 입력과 출력입니다. `agent.run()`에 넣는 값의 타입이 앞이고, 돌려받는 타입이 뒤입니다.

`nodeStart`와 `nodeFinish`는 빌더가 미리 들고 있는 노드입니다. 선언하지 않고 그냥 씁니다. 각각 `StartNode<TInput>` · `FinishNode<TOutput>`이라 전략의 타입 인자를 그대로 물려받습니다.

노드는 `by`로 선언합니다. `nodeLLMRequest()`가 돌려주는 것은 노드가 아니라 `AIAgentNodeDelegate`입니다. 위임 프로퍼티를 거치는 순간 노드가 만들어지고, 이름을 인자로 주지 않으면 프로퍼티 이름이 노드 이름이 됩니다.

엣지는 `edge(A forwardTo B)`입니다. `forwardTo`는 `AIAgentNodeBase`의 멤버 함수라 노드 사이에 그대로 놓입니다. 뒤에 붙는 `transformed`가 A의 출력을 B의 입력 모양으로 바꿉니다.

둘째 엣지의 `transformed`가 `it`을 쓰지 않는 것이 눈에 걸립니다. 앞 노드의 답을 버리는 것처럼 보이지만 그렇지 않습니다. **`nodeLLMRequest` 노드들은 같은 대화 이력을 공유합니다.** 노드가 받는 문자열은 그 이력에 덧붙는 사용자 메시지이고, 앞의 질문과 답은 이미 그 안에 있습니다. 앞 편 1장에서 중간 결과를 `joinToString`으로 다음 프롬프트에 실어 나르던 일이 여기서는 필요 없습니다. 대신 이력이 계속 자라니 길어지면 접어야 하는데, 6장 히스토리 압축이 그 자리입니다.

### 엣지는 타입이 맞아야 붙습니다

`transformed`가 취향이 아니라 필수인 이유가 있습니다. `nodeLLMRequest()`의 타입은 `AIAgentNodeDelegate<String, Message.Assistant>`입니다. 입력은 `String`인데 출력은 `Message.Assistant`입니다. 그래서 노드 두 개를 그냥 잇지 못합니다.

```kotlin
edge(draft forwardTo polish)
```

```
None of the following candidates is applicable:
fun <IncomingOutput, OutgoingInput, CompatibleOutput : OutgoingInput> edge(
    edgeIntermediate: AIAgentEdgeBuilderIntermediate<IncomingOutput, CompatibleOutput, OutgoingInput>
): Unit
```

거절 이유가 시그니처에 적혀 있습니다. `CompatibleOutput : OutgoingInput` — 나가는 쪽의 출력이 들어가는 쪽의 입력의 서브타입일 때만 `edge`가 받습니다. 손조립이라면 실행해 봐야 알던 연결 오류를 컴파일러가 먼저 잡습니다.

`Message.Assistant`에서 문자열을 꺼내는 것은 `textContent()`입니다. 이 타입에 `content` 프로퍼티는 없습니다. 응답이 `parts` 목록으로 되어 있어서, 텍스트 파트만 이어 붙여 주는 함수를 거칩니다.

### 조건 엣지 — 분기가 엣지로 내려옵니다

한 노드에서 엣지를 여러 개 뽑고 각 엣지에 조건을 걸면 분기입니다. 앞 편 2장 라우팅이 이 모양이 됩니다.

```kotlin
val router = strategy<String, String>("route") {
    val classify by nodeLLMRequest()
    val math by node<String, String>("math") { "수학 경로" }
    val other by node<String, String>("other") { "일반 경로" }

    edge(nodeStart forwardTo classify)
    edge(classify forwardTo math onTextMessage { "MATH" in it.text })
    edge(classify forwardTo other onTextMessage { true })
    edge(math forwardTo nodeFinish)
    edge(other forwardTo nodeFinish)
}
```

`node<Input, Output>("이름") { }`은 모델을 부르지 않는 평범한 노드입니다. 람다가 받는 `it`이 입력이고 반환값이 출력입니다.

조건은 세 종류를 쓰게 됩니다.

- `onCondition { }` — 일반형. 나가는 값을 그대로 받아 `Boolean`을 돌려줍니다. 걸러내기만 하고 타입은 바꾸지 않습니다.
- `onTextMessage { }` — 응답의 텍스트 파트에 조건을 걸고, **통과한 값을 `String`으로 바꿔** 넘깁니다. 걸러내기와 변환을 한 번에 하니 위 코드에 `transformed`가 없습니다. 람다의 `it`은 `MessagePart.Text`라서 `it.text`로 본문을 봅니다.
- `onToolCalls { }` — 모델이 도구를 부르겠다고 답한 경우로 좁힙니다. 5장에서 씁니다.

엣지는 선언한 순서로 검사되고 먼저 통과한 하나만 성립합니다. 그래서 둘째 엣지의 `onTextMessage { true }`가 기본 경로 역할을 합니다. 앞 편의 `when` 마지막 가지가 여기로 온 셈입니다.

앞 편과 나뉘는 자리가 여기입니다. 판정을 모델이 하고 분기를 코드가 한다는 구조는 같습니다. 다른 것은 분기가 어디 적히느냐입니다. `when (decision.route())`은 서비스 코드 한복판의 제어문이었고, 여기서는 그래프 정의의 일부입니다. 흐름을 읽으려고 함수 본문을 따라가지 않고 엣지 목록만 봅니다.

### 되돌아오는 엣지 — 루프

엣지가 지나온 노드로 돌아가도 됩니다. 앞 편 4장 자기 검토의 `for` + `break`가 이 모양이 됩니다.

```kotlin
val loop = strategy<String, String>("loop") {
    val step by node<String, String>("step") { "$it." }

    edge(nodeStart forwardTo step)
    edge(step forwardTo step onCondition { it.length < 3 })
    edge(step forwardTo nodeFinish onCondition { it.length >= 3 })
}
val agent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = KoogModels.QWEN3_4B_INSTRUCT,
    strategy = loop,
    maxIterations = 5,
)
```

`step`이 자기 자신으로 가는 엣지와 끝으로 가는 엣지를 함께 갖습니다. 조건이 서로 배타적이라 셋째 바퀴에서 빠져나오고 `"..."`가 나옵니다. 자기 검토로 읽으면 되돌아가는 엣지가 "고쳐 쓰기", 나가는 엣지가 "통과"입니다.

탈출 조건을 잘못 쓰면 어떻게 되는지가 중요합니다. 되돌아가는 엣지의 조건을 `{ true }`로 바꿔 돌리면 무한히 돌지 않고 이렇게 끊깁니다.

```
ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException:
AI Agent has run into a problem: Agent couldn't finish in given number of steps (5).
Please, consider increasing `maxAgentIterations` value in agent's configuration
```

1장에서 손조립의 위험으로 적었던 것 — 루프 상한을 빠뜨리면 거기서 무한히 돈다 — 의 답이 여기입니다. 상한을 적는 자리가 루프 안이 아니라 에이전트 설정이고, 안 적으면 기본값이 들어갑니다. 빠뜨릴 수 있는 자리가 아예 없어졌습니다. 대신 상한이 그래프 전체의 걸음 수라서, 노드를 늘리면 같은 값이 더 빨리 소진됩니다.

### 앞 편 세 장이 이 DSL에서

| 앞 편                 | 앞 편의 도구            | 이 DSL           |
| ------------------- | ------------------ | -------------- |
| 1장 프롬프트 체이닝 | 호출 두 번, 중간 결과를 다음 프롬프트에 실어 | 노드 두 개 + `transformed` 엣지 |
| 2장 라우팅        | `entity()` 판정 + `when` | 노드 하나에서 갈라지는 조건 엣지 |
| 4장 자기 검토      | `for` + `if … break`  | 되돌아오는 엣지 + `maxIterations` |

세 장 모두 우리 코드에서 사라진 것은 제어문이고, 대신 생긴 것은 그래프 선언입니다. 흐름이 코드가 아니라 값이 되면 실행기가 그것을 읽을 수 있습니다. 어느 노드에서 몇 번 돌았는지 세는 관측성이 프레임워크 기능으로 있는 이유입니다.

노드가 더 커지면 `subgraph("이름") { }`로 그래프 안에 그래프를 넣습니다. 하위 그래프는 자기만의 도구 목록과 모델을 가질 수 있어서, 앞 편 7장 멀티 에이전트가 서비스 클래스를 나누던 자리에 옵니다.

여기까지는 도구 없이 모델만 불렀습니다. 다음 장에서 이 그래프에 도구를 붙입니다.
