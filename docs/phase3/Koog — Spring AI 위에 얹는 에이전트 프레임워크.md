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

## 5장 도구 — 실행은 Koog가 하고 Spring AI는 스키마만 받는다

2장 끝에서 경계를 하나 예고했습니다. 도구를 실제로 실행하는 쪽은 Koog이고 Spring AI는 스키마만 실어 보낸다는 것입니다. 이번 장에서 그 문장이 코드로 어떻게 생겼는지 봅니다.

### 도구는 클래스 하나에 모아 붙입니다

환율을 돌려주는 도구 하나짜리 예제입니다.

```kotlin
@LLMDescription("환율 조회 도구")
class ExchangeToolSet : ToolSet {
    val calls = mutableListOf<String>()

    @Tool
    @LLMDescription("통화 코드의 원화 환율을 돌려준다")
    fun exchangeRate(@LLMDescription("통화 코드, 예: USD") code: String): String {
        calls += code
        return when (code.uppercase()) {
            "USD" -> "1385"
            "JPY" -> "9.2"
            else -> "미지원 통화"
        }
    }
}
```

`calls`는 도구가 실제로 돌았는지 보려고 넣은 것이고, 나머지가 도구 정의의 전부입니다. 읽을 것이 셋 있습니다.

`ToolSet`은 멤버가 없는 마커 인터페이스입니다. 구현할 것이 없고, 대신 기본 구현으로 `asTools()`를 얻습니다. 이 함수가 리플렉션으로 클래스를 훑어 `@Tool`이 붙은 함수를 모읍니다. 리플렉션으로 도는 만큼 `kotlin-reflect`가 런타임에 있어야 합니다.

`@Tool`은 표시만 하는 애노테이션입니다. 도구 이름은 함수 이름이 그대로 쓰이고, 바꾸려면 `@Tool(customName = "...")`입니다. 앞 편의 `@Tool`이 설명까지 같이 받던 것과 다릅니다.

`@LLMDescription`이 설명을 맡습니다. 클래스·함수·파라미터 세 자리에 붙고, 붙은 자리에 따라 무엇의 설명인지가 정해집니다. 모델이 읽는 문서가 이것뿐이라 여기 쓴 문장이 도구를 부를지 말지를 가릅니다.

### 레지스트리에 담아 에이전트에 넘깁니다

```kotlin
val tools = ExchangeToolSet()
val agent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = KoogModels.QWEN3_4B_INSTRUCT,
    systemPrompt = "환율 질문에는 반드시 exchangeRate 도구를 호출한다. " +
        "도구가 돌려준 숫자를 그대로 답에 싣고, 직접 계산하거나 추측하지 않는다.",
    temperature = 0.0,
    toolRegistry = ToolRegistry { tools(tools) },
)
val result = agent.run("1달러는 몇 원이야?")
```

`"1달러는 1385원입니다."`가 나오고, `tools.calls`에는 `[USD]`가 남습니다. 모델이 지어낸 숫자가 아니라 Kotlin 함수가 이 JVM에서 돌아 돌려준 값이라는 증거입니다.

`ToolRegistry { }`는 람다를 받는 생성자입니다. 람다 안이 `ToolRegistryBuilder`이고, `tools(ToolSet)` 말고도 도구 객체 하나를 넣는 `tool(ToolBase)`, 함수 참조 하나만 도구로 만드는 `tool(KFunction)`이 있습니다. 클래스로 묶기 애매한 한 개짜리는 마지막 것으로 끝냅니다.

에이전트 쪽에서는 `toolRegistry`가 생성자 인자 하나로 끝입니다. 3장에서 `systemPrompt`와 `temperature`가 그랬듯 도구도 **에이전트를 만들 때 박히는 설정**입니다. 앞 편에서 `.tools(...)`가 호출할 때마다 체인에 끼는 값이던 것과 갈리는 자리입니다.

### 그 스키마가 무엇인지

Spring AI가 받아 가는 "스키마"의 실체는 `ToolDescriptor`입니다. 모델을 부르지 않고 꺼내 볼 수 있습니다.

```kotlin
val descriptor = ExchangeToolSet().asTools().single().descriptor

assertEquals("exchangeRate", descriptor.name)
assertEquals("통화 코드의 원화 환율을 돌려준다", descriptor.description)
assertEquals(listOf("code"), descriptor.requiredParameters.map { it.name })
assertEquals("통화 코드, 예: USD", descriptor.requiredParameters.single().description)
```

이름 하나, 설명 하나, 파라미터마다 이름과 설명. 애노테이션에 적은 문자열이 그대로 옮겨져 있습니다. 모델에게 도구란 이 몇 줄이 전부이고, 함수 본문은 모델 쪽으로 넘어가지 않습니다. `@LLMDescription`을 대충 쓰면 도구를 안 부르는 이유가 여기 있습니다.

브리지가 이 서술자를 Spring AI의 도구 표현으로 옮겨 Ollama 요청에 싣습니다. 거기까지가 Spring AI 몫입니다. 모델이 "이 도구를 이런 인자로 부르겠다"고 답한 다음부터 — 호출을 받아 함수를 돌리고, 결과를 대화 이력에 넣고, 모델을 다시 부르는 왕복 — 은 전부 Koog입니다. 앞 편에서 `ChatClient`가 그 왕복까지 대신 돌려주던 것과 다릅니다.

### 왕복을 그래프로 직접 그리면

위 코드에는 전략이 없으니 3장의 기본 그래프가 그 왕복을 돌린 것입니다. 4장에서 미뤄 둔 `onToolCalls`로 같은 왕복을 펼쳐 놓으면 이렇게 됩니다.

```kotlin
val toolLoop = strategy<String, String>("tool-loop") {
    val request by nodeLLMRequest()
    val execute by nodeExecuteTools()
    val report by nodeLLMSendToolResults()

    edge(nodeStart forwardTo request)
    edge(request forwardTo execute onToolCalls { true })
    edge(request forwardTo nodeFinish onTextMessage { true })
    edge(execute forwardTo report)
    edge(report forwardTo nodeFinish onTextMessage { true })
}
```

`"1엔은 몇 원이야?"`를 넣으면 `calls`에 `[JPY]`가 남습니다.

노드 두 개가 새로 나왔습니다. `nodeExecuteTools()`는 `ToolCalls`를 받아 `ReceivedToolResults`를 내놓습니다 — 도구를 실제로 돌리는 자리입니다. `nodeLLMSendToolResults()`는 그 결과를 받아 대화에 넣고 모델을 다시 불러 `Message.Assistant`를 돌려줍니다.

`onToolCalls { }`는 4장의 `onTextMessage { }`와 같은 자리에 있습니다. 응답을 도구 호출인 경우로 좁히고, 통과한 값을 `ToolCalls`로 바꿔 넘깁니다. 그래서 `request` 노드에서 엣지가 둘 나갑니다. 모델이 도구를 부르겠다고 하면 `execute`로, 그냥 텍스트로 답하면 곧장 끝으로 갑니다. 도구를 붙였다고 매번 부르는 것은 아니라는 사실이 엣지 두 개로 드러나 있습니다.

펼쳐 두면 노드 사이에 다른 것을 끼울 수 있습니다. 앞 편 13장에서 취소 도구를 실행하지 않고 사람 승인을 기다리게 했던 것이 여기서는 `execute` 앞에 승인 노드 하나가 됩니다. 손조립에서 도구 함수 안에 심어야 했던 조건이 그래프에서는 노드 사이 간격으로 옮겨 갑니다.

### 같은 이름, 다른 타입

여기까지 나온 `@Tool`은 앞 편의 `@Tool`과 이름만 같고 다른 애노테이션입니다. 둘 다 클래스패스에 올라와 있으니 대조해 둡니다.

|          | 앞 편 (Spring AI)                                 | 이 편 (Koog)                                     |
| -------- | ---------------------------------------------- | ---------------------------------------------- |
| 도구 표시    | `org.springframework.ai.tool.annotation.Tool`  | `ai.koog.agents.core.tools.annotations.Tool`   |
| 도구 설명    | `@Tool(description = "…")`                     | 함수에 붙인 `@LLMDescription("…")`                  |
| 파라미터 설명  | `@ToolParam(description = "…")`                | 파라미터에 붙인 `@LLMDescription("…")`                |
| 묶는 자리    | 아무 클래스나 가능                                     | `ToolSet` 구현 클래스                               |
| 붙이는 자리   | 호출할 때 `.tools(SupportTools(orderService))`      | 만들 때 `toolRegistry = ToolRegistry { tools(…) }` |

`@LLMDescription`도 Koog 전용이라 `ai.koog.agents.core.tools.annotations.LLMDescription`을 임포트합니다. Koog 쪽 애노테이션 둘은 같은 패키지에 있습니다.

임포트를 잘못 골라도 컴파일은 통과합니다. Spring AI의 `@Tool`은 메서드에 붙일 수 있고 Kotlin 함수도 메서드니까요. 대신 실행하면 이렇게 끊깁니다.

```
java.lang.IllegalArgumentException: No tools found in class com.example.phase3.WrongToolSet
	at ai.koog.agents.core.tools.reflect.ToolFromCallableExtensionsKt.asTools(ToolFromCallableExtensions.kt:71)
	at ai.koog.agents.core.tools.ToolRegistryBuilder.tools(ToolRegistryBuilder.kt:42)
	at ai.koog.agents.core.tools.ToolRegistry.<init>(ToolRegistry.kt:39)
```

리플렉션이 Koog의 `@Tool`만 찾으므로 도구가 하나도 안 잡힙니다. 다행인 것은 터지는 자리입니다. 스택 맨 아래가 `ToolRegistry.<init>`이니 레지스트리를 만드는 순간, 곧 에이전트를 조립하는 순간 끊깁니다. 모델을 부르다가 조용히 도구 없이 답하는 쪽이 아닙니다.

### 작은 모델이 도구를 부르게 하려면

qwen3:4b는 도구 호출을 건너뛰기 쉬운 크기입니다. 위 예제들은 다섯 번씩 돌려 모두 같은 결과가 나왔지만, 그렇게 되기까지 붙인 조건이 있습니다.

시스템 프롬프트에 도구 이름을 그대로 박았습니다. `"환율 질문에는 반드시 exchangeRate 도구를 호출한다"`처럼 무엇을 부를지까지 적습니다. 온도는 `0.0`으로 내렸습니다. 그리고 도구가 하나뿐이고 질문이 그 도구와 정확히 맞아떨어집니다.

도구를 여럿 붙이면 "부를지 말지"에 "무엇을 부를지"가 더해집니다. 앞 편에서 작은 모델의 판정이 흔들리던 것과 같은 자리이고, 프레임워크를 갈아도 모델 크기에서 오는 문제는 그대로 남습니다. Koog가 가져간 것은 왕복을 도는 코드이지 부를지 말지의 판단이 아닙니다.

도구까지 붙었으니 대화가 길어집니다. 다음 장은 그 이력을 어디에 두고 어떻게 접는지입니다.

## 6장 메모리 — `ChatMemoryRepository` 어댑터와 제약, 히스토리 압축

4장에서 노드들이 같은 대화 이력을 공유한다고 했습니다. 그 이력의 수명은 `run()` 한 번입니다. 호출이 끝나면 사라지고, 다음 `run()`은 빈 이력에서 시작합니다. 이 장은 그 사이를 잇습니다.

Koog에서 이력을 밖에 내놓는 자리는 `ChatHistoryProvider`입니다. 인터페이스에 함수가 둘뿐입니다.

```kotlin
suspend fun store(conversationId: String, messages: List<Message>)
suspend fun load(conversationId: String): List<Message>
```

Spring AI에서 그 자리에 있던 것이 `ChatMemoryRepository`입니다. 브리지가 하는 일은 2장과 같은 모양입니다.

```
Spring AI ChatMemoryRepository 빈  →  SpringAiChatHistoryProvider  =  ChatHistoryProvider 빈
```

2장의 그림보다 칸이 하나 적습니다. 실행기 쪽은 `SpringAiLLMClient`를 다시 `PromptExecutor`로 감쌌지만, 여기서는 어댑터 자체가 곧 인터페이스 구현이라 그대로 빈이 됩니다.

### 의존성 하나가 더 붙습니다

```kotlin
// Spring AI의 ChatMemoryRepository를 Koog의 ChatHistoryProvider로 감싸는 스타터
implementation(libs.koog.spring.ai.starter.chat.memory)
```

좌표는 `ai.koog:koog-spring-ai-starter-chat-memory`이고 버전은 2장의 브리지와 같은 `1.2.0-beta`입니다. 저장소 쪽은 따로 넣을 것이 없습니다. Spring AI의 Ollama 스타터가 `spring-ai-autoconfigure-model-chat-memory`를 이미 끌고 오고, 거기서 `InMemoryChatMemoryRepository`가 `chatMemoryRepository`라는 이름으로 올라옵니다.

### 그런데 빈이 만들어지지 않습니다

의존성만 넣고 `ChatHistoryProvider`를 주입받으면 컨텍스트가 뜨지 않습니다.

```
No qualifying bean of type 'ai.koog.agents.chatMemory.feature.ChatHistoryProvider' available:
expected at least 1 bean which qualifies as autowire candidate
```

저장소 빈이 없어서가 아닙니다. `chatMemoryRepository`는 컨텍스트에 멀쩡히 올라와 있습니다. 조건 평가 보고서를 열어 보면 어댑터 쪽 조건만 어긋나 있습니다.

```
SpringAiChatMemoryAutoConfiguration$SingleChatMemoryRepositoryConfiguration
   @ConditionalOnSingleCandidate (types: org.springframework.ai.chat.memory.ChatMemoryRepository;
   SearchStrategy: all) did not find any beans
```

순서 문제입니다. Koog의 오토컨피규레이션은 `@AutoConfiguration(afterName = …)`에 Cassandra · CosmosDB · JDBC · Neo4j · Mongo 저장소 오토컨피규레이션을 적어 두고 그 뒤에 서겠다고 선언합니다. 정작 기본 인메모리 저장소를 만드는 Spring AI의 `ChatMemoryAutoConfiguration`은 그 목록에 없습니다. 그래서 `@ConditionalOnSingleCandidate`가 저장소 빈이 등록되기 전에 평가되고, 후보를 못 찾은 채 어댑터를 건너뜁니다. 별도 저장소를 붙인 사람은 겪지 않고 기본값으로 시작한 사람만 겪습니다.

빠져나갈 문은 스타터가 들고 있습니다. 저장소를 타입이 아니라 **이름으로** 찾는 설정이 따로 있습니다.

```properties
koog.spring.ai.chat-memory.chat-memory-repository-bean-name=chatMemoryRepository
```

이 값이 비어 있지 않으면 `NamedChatMemoryRepositoryConfiguration` 쪽이 성립합니다. 이쪽은 조건을 평가할 때 빈을 찾지 않고, 어댑터를 만드는 시점에 `BeanFactory`에서 이름으로 꺼냅니다. 그때는 저장소 정의가 이미 등록돼 있으니 걸리지 않습니다. 프로퍼티 한 줄로 끝나지만, 프레임워크가 알아서 해 주기로 한 자리에 Spring AI 내부 빈 이름을 우리가 적어 넣은 셈입니다.

### 붙이는 자리는 `install`

3장부터 쓰던 `AIAgent(...)` 뒤에 블록이 하나 더 붙습니다. 기능을 설치하는 자리입니다.

```kotlin
@Autowired
lateinit var historyProvider: ChatHistoryProvider

val agent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = KoogModels.QWEN3_4B_INSTRUCT,
    systemPrompt = "한 문장으로만 답한다.",
    temperature = 0.0,
) {
    install(ChatMemory) {
        chatHistoryProvider = historyProvider
    }
}
val sessionId = "ch06-이름"

agent.run("내 이름은 준이야", sessionId)
val result = agent.run("내 이름이 뭐라고 했지?", sessionId)
```

`"당신의 이름은 준이에요."`가 나옵니다. 두 번의 `run()`이 이어졌다는 뜻입니다.

3장에서 생략 가능하다고만 적어 둔 `run()`의 둘째 인자가 여기서 쓰입니다. `sessionId`가 그대로 저장소의 `conversationId`가 되고, 같은 값을 넣으면 같은 대화, 다른 값을 넣으면 다른 대화입니다. 이름이 경계에서 한 번 바뀔 뿐 값은 같은 것입니다.

`install(ChatMemory)`는 전략이 시작할 때 `load`를, 끝날 때 `store`를 겁니다. 그 사이의 이력 관리는 4장에서 보던 그대로입니다. 즉 메모리는 그래프를 바꾸지 않고 그래프의 앞뒤에 붙습니다.

### 저장되는 것은 평문뿐입니다

여기가 이 장의 핵심입니다. 어댑터가 옮기는 것은 두 쪽의 교집합이고, 대화 이력에서 그 교집합은 **텍스트**입니다.

5장의 환율 도구를 그대로 붙이고, 메모리까지 켜서 한 번 돌린 다음 저장소를 직접 열어 봤습니다.

```kotlin
agent.run("1달러는 몇 원이야?", sessionId)

val stored = chatMemoryRepository.findByConversationId(sessionId)

assertEquals(listOf("USD"), tools.calls)
assertEquals(
    listOf(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT),
    stored.map { it.messageType },
)
assertTrue("1385" in stored.last().text)
```

남은 행은 셋입니다.

```
SYSTEM    | 환율 질문에는 반드시 exchangeRate 도구를 호출한다. …
USER      | 1달러는 몇 원이야?
ASSISTANT | 1달러는 1385원입니다.
```

`tools.calls`에 `[USD]`가 있으니 도구는 분명히 돌았습니다. Koog 쪽 이력에는 "이 도구를 이 인자로 부르겠다"는 어시스턴트 메시지와 "결과는 1385"라는 도구 결과 메시지가 있었습니다. 저장소에는 그 둘이 없습니다. 예외도 경고도 없이 사라집니다.

이유는 두 메시지 모델이 다른 층위에서 갈리기 때문입니다. Koog의 `Message`는 `System` · `User` · `Assistant` 셋뿐이고, 도구 호출도 도구 결과도 추론도 그 안에 담기는 `MessagePart`입니다.

```
Message      = System | User | Assistant
MessagePart  = Text | Reasoning | Attachment | Tool.Call | Tool.Result | …
```

어댑터는 각 메시지에서 `MessagePart.Text`만 골라 Spring AI 메시지로 옮깁니다. 나머지 파트는 옮길 자리가 없어 버려집니다. 텍스트 파트가 하나도 없는 메시지 — 도구를 부르겠다는 응답이 딱 이것입니다 — 는 통째로 사라집니다. 첨부가 붙은 메시지는 아예 걸러 내고 디버그 로그 한 줄을 남깁니다.

```
Dropping Koog message with attachments (type={}); not persistable via Spring AI ChatMemoryRepository
Skipping Spring AI TOOL message on load; not mappable to a Koog message type
```

반대 방향도 대칭입니다. 저장소에 다른 경로로 들어온 `TOOL` 타입 행은 읽어 올 때 건너뜁니다. 타임스탬프 · 토큰 수 · 종료 이유 같은 메타데이터도 마찬가지로 남지 않습니다.

**1장에서 예고한 어댑터 제약의 두 번째 사례입니다.** 2장은 버전이 맞아야 한다는 조건이었고, 이번 것은 기능의 일부만 건너간다는 쪽입니다. 저장된 대화를 다시 불러오면 무엇을 물었고 무엇을 답했는지는 남지만, 그 답을 **어떻게 알아냈는지는 없던 일이 됩니다.** 도구를 붙인 에이전트를 재시작 너머로 이어 붙이려면 이 손실을 감수하거나, `ChatHistoryProvider`를 직접 구현해야 합니다. 인터페이스가 함수 두 개뿐이라는 것이 여기서는 다행입니다.

세션 안에서만 도는 동안에는 이 문제가 없습니다. 도구 왕복은 그 `run()`의 이력에 온전히 있고, 잘리는 자리는 저장소로 나갔다 들어오는 경계입니다.

### 히스토리 압축은 노드 하나입니다

4장에서 이력이 계속 자란다는 이야기를 하고 6장으로 미뤄 뒀습니다. 접는 자리는 그래프 안의 노드입니다.

```kotlin
val compressing = strategy<String, String>("compress") {
    val ask by nodeLLMRequest()
    val compress by nodeLLMCompressHistory<Message.Assistant>()
    val recall by nodeLLMRequest()

    edge(nodeStart forwardTo ask)
    edge(ask forwardTo compress)
    edge(compress forwardTo recall transformed { "내 이름이 뭐라고 했지?" })
    edge(recall forwardTo nodeFinish transformed { it.textContent() })
}
```

`"내 이름은 준이야"`를 넣으면 `"당신의 이름은 준이야."`가 나옵니다. 가운데서 이력이 한 번 접혔는데도 이름이 살아남았습니다.

`nodeLLMCompressHistory<T>()`의 타입 인자가 하나뿐인 것이 이 노드의 성격입니다. 입력을 그대로 출력으로 흘려보내고, 손대는 것은 값이 아니라 옆에 있는 대화 이력입니다. 그래서 엣지에 `transformed`가 필요 없습니다.

접는 방식은 인자로 고릅니다. 노드 선언만 바꾸면 됩니다.

```kotlin
val compress by nodeLLMCompressHistory<Message.Assistant>(
    strategy = HistoryCompressionStrategy.FromLastNMessages(3),
)
```

기본값은 `WholeHistory`입니다. 이력 전체를 모델에게 요약시켜 그 요약문으로 갈아 끼웁니다. `FromLastNMessages(n)`은 최근 n개만 두고, `Chunked(n)`은 덩어리로 나눠 각각 요약하고, `FromTimestamp(t)`는 그 시각 이후만 남기고, `FactRetrieval(concepts)`는 미리 적어 둔 개념에 해당하는 사실만 뽑아냅니다. 시스템 메시지가 여러 개인 이력을 위한 `WholeHistoryMultipleSystemMessages`도 따로 있습니다.

이것이 1장에서 "프레임워크가 이미 만들어 둔 물건"이라고 적은 쪽의 표본입니다. 손으로 짜면 요약 프롬프트를 쓰고, 언제 접을지 정하고, 접은 결과로 이력을 갈아 끼우고, 시스템 메시지는 남기는 일을 전부 우리 코드에 둬야 합니다. 여기서는 노드 이름 하나입니다. 대신 요약 품질은 모델 몫이라 5장 끝과 같은 자리로 돌아옵니다 — 프레임워크가 가져간 것은 접는 절차이지 무엇을 남길지의 판단이 아닙니다.

### 이 장에서 갈린 것

| 축         | Spring AI 쪽                     | Koog 쪽                       | 어댑터가 옮기는 것        |
| --------- | ------------------------------- | ---------------------------- | ----------------- |
| 저장소       | `ChatMemoryRepository`          | `ChatHistoryProvider`        | 전부                |
| 대화 키      | `conversationId`                | `run()`의 `sessionId`         | 전부                |
| 메시지       | `SYSTEM` · `USER` · `ASSISTANT` | `System` · `User` · `Assistant` | 텍스트만              |
| 도구 호출·결과  | `TOOL` 행                        | `MessagePart.Tool.*`         | 없음                |
| 추론 · 첨부   | 담을 행이 없음                        | `MessagePart.Reasoning` 등    | 없음                |

이력은 이제 `run()` 밖에서도 남고 길어져도 접힙니다. 다음 장은 대화가 아니라 **문서**를 밖에 두는 쪽입니다.
