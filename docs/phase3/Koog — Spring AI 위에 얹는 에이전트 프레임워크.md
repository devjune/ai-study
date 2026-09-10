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
