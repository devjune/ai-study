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

## 2026-10-19 onTextMessage 조건이 1.2.0에서 평가되지 않음

조건 엣지 라우팅에서 `onTextMessage { 조건 }`의 람다가 무시되고 먼저 선언된
텍스트 엣지가 항상 선택된다 (프로브 4회 재현: 조건이 거짓인 입력도 전부 첫
엣지로). `onCondition { ... it.textContent() }`는 정상 동작한다. 덱과 테스트의
라우팅 예제를 onCondition 기반으로 바꿨다. 과거 "조건 동작" 관찰들은 모두
첫-엣지 선택과 결과가 우연히 일치했던 것이다. 1.3.0에서의 재현 여부는 확인
하지 않았다.

## 2026-10-19 5단계를 시각화에서 챗 메모리로

덱 전체 기준으로 5단계 "시각화"는 세 가지가 어긋났다. (1) 하네스에 아무것도
더하지 않는다. `asMermaidDiagram()` 호출은 테스트 코드에만 있다. (2) 7~10장과
12장은 터미널에서 확인하는데 이 장만 정적 그림이다. 공식 IntelliJ 라이브
시각화 도구가 없다는 조사 결과(AI Agents Debugger는 IDE 2026.2에서 로드 실패,
Koog Strategy Graph는 서드파티)로 라이브 데모 가능성도 사라졌다. (3) 5장에서
칠한 칩 여섯 개 중 챗 메모리만 단계가 없었고, 12장 스크립트가 "슬라이드엔
없었지만"으로 메우고 있었다.

5단계를 챗 메모리(`install(ChatMemory)` + `run`의 세션 ID)로 바꿨다. 하네스
코드에 이미 있던 것이라 슬라이드 = 하네스 코드가 유지된다. 시각화는 10장의
dispatch 그림 하나와 예상 질문으로 내렸고, 내장 전략(singleRunStrategy) 그림과
테스트는 뺐다.

프로브(qwen3:4b-instruct, temperature 0): 같은 세션의 새 에이전트가 "아까 말한
첫 번째 일정이 뭐였지?"에 "10:00 팀 싱크"를 도구 호출 없이 답한다(calls =
[내일], 첫 턴 한 번뿐). 기억이 없으면 "알 수 없습니다"라고 답하고 도구도 부르지
않는다. ChatMemoryTest 2건으로 고정했다.

## 2026-10-19 하네스 이벤트 트레이스

리허설에서 "그냥 채팅하는 느낌, 하네스를 적용했다는 임팩트가 약하다"는 피드백.
터미널에 입력과 답만 보이고 분기, 도구 호출, 스킬 파일 읽기, 이력 적재가 전부
숨어 있었다. Koog EventHandler(공식 feature, 5장 칩 "이벤트 핸들러")를 세
에이전트에 install하고 Trace.kt가 흐린 줄을 찍는다. 라우터는 노드 진입
(`dispatch → skill|assistant`), 담당 에이전트는 LLM 호출(메시지 수)과 도구
호출(이름, 인자, 결과)만. 내장 전략 노드(nodeCallLLM 등)는 소음이라 찍지 않는다.

실측에서 각 장의 "확인"이 화면에 그대로 나온다. 8장은 모델 호출 사이의
`schedules(day=내일)`, 9장은 `__read_file__(...SKILL.md) → 2,047자`, 10장은
dispatch 줄, 11장은 메시지 4개짜리 LLM 호출 한 번과 도구 줄 없음. 비서 세션의
메시지 수가 턴마다 2개씩 느는 것으로 챗 메모리가 보인다. step1(7장)은 트레이스
없이 두어 대비로 쓴다.

## 2026-10-20 Koog 1.3.0으로 이동

코어 `koog-agents-jvm` 1.3.0, 브리지·skills·ext·mcp는 1.3.0-beta(Maven Central 최신).
1.3.0-beta 브리지의 POM도 Spring Boot 3.5.14 / Spring AI 1.1.6을 고정하므로 스택은 그대로다.
1.3.0 릴리스 노트는 모델 정의 추가와 Google 캐시 토큰·Langfuse 트레이스 버그 수정뿐.
전체 테스트 14건 통과. onTextMessage 조건 무시 현상의 1.3.0 재현 여부는 확인하지 않았다
(덱과 테스트는 onCondition만 쓴다).

## 2026-10-20 Persistence: 저장 키는 run의 둘째 인자

공식 문서의 "체크포인트는 특정 에이전트에 묶인다"는 말과 달리, 1.2.0·1.3.0의 Persistence
기능은 `AIAgentContext.runId`로 저장하고 찾는다. `run(input, "demo")`의 둘째 인자가 그
runId이고, 챗 메모리의 세션 ID와 같은 값이다. 에이전트 `id`를 주고 둘째 인자를 비우면
실행마다 새 UUID가 되어 복구가 일어나지 않는다(첫 실험 실패 원인).

동작: 노드가 끝날 때마다 체크포인트(nodeCallLLM, nodeExecuteTool, nodeSendToolResult),
정상 완료 때 tombstone. 다음 `run(…, 같은 세션)`은 tombstone 없는 마지막 체크포인트가
있으면 거기서 이어간다. 입력은 무시된다("다시 시작"으로 재개해도 원래 질문의 답이 나옴).
PersistenceTest 2건: 중단된 세션 재개는 모델 호출 1회(메시지 4개), 도구 호출 1회.

하네스: 비서 에이전트에 `install(Persistence)`(파일 저장소 /tmp/koog-checkpoints), 기동 때
세션 "demo"에 tombstone 없는 체크포인트가 있으면 트레이스 줄을 찍고 `run("", "demo")`로
마저 돌린다. 체크포인트 폴더는 재시작 뒤에도 남아야 하므로 지우지 않는다(리허설 뒤 수동 삭제).

## 2026-10-20 덱을 공식 주장 기준으로 재구성 (14장)

"Spring AI로도 다 되는 것 아닌가"라는 질문에 답이 없었다. 주제를 Koog의 공식 주장
(Why Koog: 그래프 워크플로, 내구 실행, 재시도·압축·LLM 전환, JetBrains 검증, 통합)을
우리 Spring AI 스택에서 확인하는 것으로 잡았다. Spring AI 위에 얹는 구도는 Why Koog의
통합 항목 중 하나이지 Koog의 전제가 아니므로 6장 "우리 환경에서의 배치"로만 둔다.
4장 공식 주장, 6장 두 층 배치와 여섯 단계, 10장 오케스트레이션, 12장 내구 실행(신설),
13장 Spring AI와 갈리는 지점(신설, 오늘 본 것과 출처만인 것 구분), 14장 Demo.

## 2026-10-20 덱 교차 검증 (서브 에이전트 3)

범위를 셋으로 나눠 출처 대조. Koog 자체 주장 66건(확인 53, 부분 13, 반박 0), Spring AI
비교 13건(확인 8, 부분 4, 반박 1), 연혁·외부 참조 19건(전부 확인, 링크 18개 전부 응답).

반박 1건: "모델 설정은 application.properties 그대로"와 예상 질문 "spring.ai 설정만
바꾸면 된다". 브리지 SpringAiLLMClient.buildChatOptions가 에이전트 LLModel의 id를 매
호출 ChatOptions.model로 넣어 프로퍼티의 모델 이름을 덮는다(1.3.0-beta 바이트코드로
재확인). 접속(base-url)은 프로퍼티, 모델 이름은 코드로 고쳐 적었다.

부분 일치에서 고친 것: 4장 인용문은 GitHub 저장소 소개문(About)이라 출처 추가와 말줄임,
"1년 호환 보장"→"최소 1년", 3장 출처에 Koog Comes to Java·Releases 추가, 6장 브리지는
스타터 둘 이름과 LLMClient 단계, 9장 카탈로그는 name·description, 13장 스킬 셀은
커뮤니티 spring-ai-agent-utils SkillsTool 명시, 5장 툴팁 넷(노드 간 데이터 전달, 커스텀
스키마, 스트리밍, 직렬화)을 문서 설명대로, 12장 체크포인트 키는 "문서는 에이전트에
묶인다고 쓰지만 1.3.0 코드는 runId"로, 완료 표시·파일 저장소는 소스 출처로.

## 2026-10-20 스킬 요청에 직전 비서 답변을 붙임

"오늘 일정이 있나?" 뒤에 "판교 사투리로 일정 공지 만들어줘"가 끊겼다. 스킬 에이전트는
이력이 없어 비서가 말한 일정을 모른다. 스킬 에이전트에 같은 세션의 ChatMemory를
붙이는 실험(filterMessages로 시스템 메시지 제외)은 실패: 4B가 SKILL.md를 읽지 않고
앞 대화 말투를 따라갔고, 그 수다스러운 답이 세션 이력에 저장돼 비서 답변까지 물들었다.
대신 라우터가 스킬 요청에 historyProvider의 마지막 비서 답변을 "[판교 사투리로 바꿀
원문]"으로 붙여 넘긴다(Harness12.withLastAnswer). 라벨이 "직전 비서 답변"이면 참고
자료로 읽혀 11에서 없는 사실(순연, 야근)을 보탰고, "바꿀 원문"이면 사실을 지켰다.
강도를 안 적으면 원문을 그대로 돌려주므로(SKILL.md의 기본 11 규칙을 4B가 안 따름)
데모에서는 강도를 적는다.

## 2026-10-20 비서에 스킬 카탈로그 넣기 실험, 철회

"에이전트가 자기 스킬을 알아야 하지 않나"는 지적에 비서 프롬프트에 카탈로그를 넣고
안내만 하게 했다. 안내는 됐지만 4B의 말투가 실행마다 흔들렸다("예요!"체, "~입니다 체"
지시를 넣자 반말). 슬라이드 실측 출력과 어긋나서 뺐다. 비서가 스킬 목록을 모르는 건
담당을 나눈 대가로 두고, 스킬 이름이 들어간 질문("판교사투리란?")은 띄어쓰기와
무관하게 스킬 에이전트가 파일을 읽고 답하게 했다. 스킬 요청의 콜론 규칙: 콜론이
있으면 그 뒤 문장을, 없으면 직전 비서 답변을 바꾼다.
