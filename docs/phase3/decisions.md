# phase3 방향 & 결정 로그

phase3를 진행하며 내린 결정과 근거를 남긴다.
본문에는 들어가지 않는 "왜 이렇게 했나"의 기록.

## 스파이크 판정 — AIAgent 실행 (2026-09-10)

### 목표

Task 1은 Koog가 Spring AI의 `ChatModel`을 `PromptExecutor`로 감싸는 데까지만 확인했다(스프링 컨텍스트 로드, 빈 등록). Task 2는 그 위에서 `AIAgent`를 실제로 돌려 Ollama 응답을 받는 것까지 확인하는 2차 스파이크다.

### Kotlin 버전 — 2.2.21 그대로, 추가 확인 불필요

Task 1이 이미 컴파일·테스트로 확정한 판정을 Task 2에서도 재검증했다. `AIAgent`가 속한 `ai.koog:agents-core-jvm:1.2.0`을 새로 추가한 뒤(아래 참조) `compileTestKotlin`을 돌렸을 때 Kotlin 메타데이터 오류는 전혀 없었다. **Kotlin 2.3.x로 올릴 이유가 없다.**

### `LLModel` 생성자 — `contextLength` 필수 아님

`prompt-llm-jvm-1.2.0.jar`의 `ai/koog/prompt/llm/LLModel.class`를 `javap`로 직접 뜯어본 결과:

```
public ai.koog.prompt.llm.LLModel(LLMProvider, String, List<? extends LLMCapability>, Long, Long);
public ai.koog.prompt.llm.LLModel(LLMProvider, String, List<? extends LLMCapability>, Long);
public ai.koog.prompt.llm.LLModel(LLMProvider, String, List<? extends LLMCapability>);
public ai.koog.prompt.llm.LLModel(LLMProvider, String);
```

Kotlin 기본 인자가 오버로드로 컴파일돼 있어 `contextLength`·`maxOutputTokens` 없이 `(provider, id, capabilities)` 3개만 넘겨도 된다. 브리핑의 Step 1 예시 코드가 그대로 컴파일됐다 — **수정 불필요**.

### 빠진 의존성 — `AIAgent`는 브리지 스타터에 없다

`koog-spring-ai-starter-model-chat`의 POM은 `koog-spring-ai-common` · `prompt-executor-clients-jvm` · `prompt-executor-model-jvm`만 끌어온다. `AIAgent`는 `ai.koog.agents.core.agent` 패키지, 즉 `ai.koog:agents-core-jvm`에 있고 이 모듈은 브리지 스타터의 전이 의존성에 없다. `ai.koog:koog-agents-jvm:1.2.0`(브리지가 끌어오는 `prompt-executor-*-jvm`과 같은 비-beta 1.2.0 계열)을 `demo-phase3/build.gradle.kts`에 직접 추가해서 해결했다. 이 버전은 Maven Central에 정식 릴리스로 존재한다(`koog-agents-jvm/maven-metadata.xml`의 `<release>1.2.0</release>`).

`AIAgent(...)`는 클래스 생성자가 아니라 톱레벨 팩토리 함수다(`AIAgentFactoryKt.AIAgent`, 여러 오버로드). 브리핑의 `AIAgent(promptExecutor = ..., llmModel = ..., systemPrompt = ...)` 명명 인자 호출이 실제 시그니처와 그대로 맞아떨어졌다 — `toolRegistry`·`temperature`·`maxIterations`·`responseProcessor`·`clock`·`installFeatures`에 전부 기본값이 있다.

### 런타임 실패 — Koog 1.2.0-beta ↔ Spring AI 2.0.1 비호환 (ClassCastException)

컴파일은 전부 성공했지만 `./gradlew test --tests Ch03SingleAgentTest` 실행 시 실제 Ollama 호출 단계에서 실패했다:

```
ai.koog.prompt.executor.clients.LLMClientException: Error from client: SpringAiLLMClient
ChatModel.call() failed: class org.springframework.ai.chat.prompt.DefaultChatOptions cannot be cast to class org.springframework.ai.ollama.api.OllamaChatOptions
  (org.springframework.ai.chat.prompt.DefaultChatOptions and org.springframework.ai.ollama.api.OllamaChatOptions are in unnamed module of loader 'app')

Caused by: java.lang.ClassCastException
	at org.springframework.ai.ollama.OllamaChatModel.ollamaChatRequest(OllamaChatModel.java:415)
	at org.springframework.ai.ollama.OllamaChatModel.internalCall(OllamaChatModel.java:224)
	at org.springframework.ai.ollama.OllamaChatModel.call(OllamaChatModel.java:219)
	at ai.koog.spring.ai.chat.SpringAiLLMClient$execute$2.invokeSuspend(SpringAiLLMClient.kt:160)
```

Koog의 `SpringAiLLMClient`가 벤더 중립 `ChatOptions`(`DefaultChatOptions`)를 만들어 `chatModel.call(prompt)`에 그대로 넘기는데, Spring AI 2.0.1의 `OllamaChatModel.ollamaChatRequest`는 `prompt.getOptions()`를 곧바로 `OllamaChatOptions`로 캐스트한다. 예전에는(Spring AI 1.1.x) 이 경계에서 포터블 옵션을 벤더 옵션으로 자동 변환/병합했던 것으로 보이는데, 2.0 계열에서 이 관용이 깨졌다.

**근거**: `koog-spring-ai-common-1.2.0-beta.pom`의 `dependencyManagement`가 명시적으로 `spring-ai-bom:1.1.6` · `spring-boot-dependencies:3.5.14`를 임포트한다. 즉 Koog 1.2.0-beta의 Spring AI 브리지는 애초에 **Spring AI 1.1.6 / Spring Boot 3.5 기준으로 빌드·테스트됐고, 2.0.1을 겨냥한 적이 없다.**

### 리스크 절차 2단계 — Koog 다운그레이드도 막힘

1. **Kotlin 2.3.x 상향** — 대상 아님. 실패가 Kotlin 메타데이터 문제가 아니라 Spring AI 런타임 캐스팅 문제라 애초에 손댈 지점이 아니다.
2. **Spring AI를 Koog가 겨냥한 버전(1.1.6)으로 낮춰본다** — 시도함. `springAi = "1.1.6"`로 바꾸고 재실행하니 이번엔 컨텍스트 로딩 단계에서 죽었다:
   ```
   java.lang.ClassNotFoundException: org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration
   ```
   Spring AI 1.1.6은 Spring Boot 3.x 패키지 구조를 전제하는데 demo-phase3는 Spring Boot 4.0.0이라 오토컨피규레이션 클래스 경로 자체가 없다. 즉 Spring AI만 낮춰서는 안 되고 Spring Boot까지 3.5.x로 같이 낮춰야 하는데, 이건 phase2와 같은 스택(Spring Boot 4.0.0 / Spring AI 2.0.1)을 쓰겠다는 repo 컨벤션을 정면으로 벗어난다.
   `springAi`는 즉시 `2.0.1`로 원복.
3. **Koog 자체를 다운그레이드해본다** — Maven Central에 남은 `koog-spring-ai-starter-model-chat` 전체 릴리스 이력(`0.7.0` ~ `1.2.0-beta`, 총 17개)에서 최근 두 개의 POM을 직접 대조했다:

   | 버전 | `spring-ai-bom` | `spring-boot-dependencies` |
   | --- | --- | --- |
   | `1.2.0-beta` | 1.1.6 | 3.5.14 |
   | `1.1.1-beta` | 1.1.6 | 3.5.14 |

   두 버전이 동일한 Spring AI/Boot 타깃을 선언한다. POM 증거만으로 끝내지 않고 실제로 `koogSpringAi = "1.1.1-beta"`로 낮춰 `Ch03SingleAgentTest`를 재실행했다 — **같은 `ClassCastException`이 `OllamaChatModel.java:415`에서 똑같이 재현됐다.** 버전을 낮춰도 문제가 사라지지 않는다는 예상이 실증으로 확인됐다. 이후 `1.2.0-beta`(Task 1이 확정한 최신)로 되돌렸다.

### 중간 판정 — 이 시점에서는 BLOCKED, 3개 선택지로 팀 리드에 보고

- **컴파일은 Kotlin 2.2.21 · Koog 1.2.0-beta(+ `koog-agents-jvm:1.2.0`) · Spring AI 2.0.1 조합에서 문제없다.**
- **런타임은 막힌다.** `AIAgent.run()`이 Ollama를 호출하는 순간 Koog 1.2.0-beta의 Spring AI 브리지가 Spring AI 2.0.1의 `OllamaChatModel` 캐스팅 규약과 맞지 않아 `ClassCastException`으로 죽는다.
- 이 자리에서 고칠 수 있는 문제가 아니라 세 선택지를 정리해 보고했다.
  1. phase3만 Spring Boot 3.5.x / Spring AI 1.1.6로 낮춰 Koog가 실제로 검증한 스택에 맞춘다 — phase1·phase2와 다른 스택을 쓰게 된다.
  2. Koog가 Spring AI 2.x를 겨냥한 릴리스를 내길 기다린다.
  3. `AIAgent` 실행 자체를 이 장의 주제에서 빼고, Task 1까지 확인된 "`PromptExecutor` 브리지가 빈으로 뜬다"는 선에서 멈춘다.

### 최종 판정 — **선택지 1 채택**: demo-phase3만 Spring Boot 3.5.14 · Spring AI 1.1.6로 고정

phase1·phase2와 다른 스택을 쓰는 비용보다, **"Koog의 Spring AI 브리지가 아직 2.x를 지원하지 않는다"는 발견 자체가 이 스터디의 주제(프레임워크 추상화의 경계)에 정확히 들어맞는다**는 점이 더 컸다. phase2가 "손조립의 경계"를 봤다면, phase3는 이 발견으로 "프레임워크 추상화의 경계"를 처음부터 실물로 보여준다. 스펙의 각도(기존 Spring AI 빈을 Koog에 연결하는 구도)는 버전을 내려도 그대로 성립한다.

**적용한 조합**: Kotlin 2.2.21(변경 없음) · Spring Boot **3.5.14** · Spring AI BOM **1.1.6** · Koog **1.2.0-beta** + `koog-agents-jvm:1.2.0`. 셋 다 `koog-spring-ai-common-1.2.0-beta.pom`이 고정한 값 그대로다.

- **Kotlin**: 2.2.21로 그대로 두고 재시도 — 컴파일·테스트 모두 문제없었다. Boot 3.5.14 쪽에서도 추가 조정이 필요 없었다.
- **Ollama 프로퍼티 키**: `spring-ai-autoconfigure-model-ollama-1.1.6.jar`의 `spring-configuration-metadata.json`을 직접 열어 확인 — `spring.ai.ollama.base-url`과 `spring.ai.ollama.chat.model` 모두 1.1.6에도 그대로 있다. `application.properties`는 **수정 불필요**.
- **재실행 결과**: `./gradlew test` — **BUILD SUCCESSFUL**, 2건 모두 PASS.
  ```
  ContextLoadsTest: tests="1" failures="0" errors="0"
  Ch03SingleAgentTest: tests="1" failures="0" errors="0"
  ```
  `Ch03SingleAgentTest`의 실제 Ollama 응답: `Spring AI는 스프링 프레임워크를 기반으로 한 인공지능 개발을 위한 도구와 라이브러리이다.`

### 요약 — 이번 스파이크가 확정한 전체 조합

| 축 | 값 | 근거 |
| --- | --- | --- |
| Kotlin | 2.2.21 (변경 없음) | 컴파일·테스트 모두 무경고 통과 (Task 1·2 공통) |
| Spring Boot | 3.5.14 | Koog 1.2.0-beta가 겨냥한 버전. 4.0.0은 런타임 ClassCastException |
| Spring AI | 1.1.6 | 동일 — `koog-spring-ai-common` POM이 고정한 값 |
| Koog | 1.2.0-beta (+ `koog-agents-jvm:1.2.0`) | Maven Central 최신. 1.1.1-beta로 낮춰도 같은 오류 재현 — 버전을 낮춰서 해결되는 문제가 아님을 실증 |

phase1·phase2는 Spring Boot 4.0.0 · Spring AI 2.0.1을 그대로 쓴다 — phase3만 다른 스택을 쓴다는 사실 자체를 문서 2장에 1급 관찰로 남긴다.
