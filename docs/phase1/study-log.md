# Spring AI 학습 로그

본문 문서보다 날것의 학습 메모. 주제별 핵심을 정리한다.
기준 버전: Spring AI 2.0.0 GA (2026-06-12 릴리스).
본문(`Spring AI 2.0 — 개념부터 에이전트까지.md`)은 처음 접하는 사람용 학습 자료(시작하기 → 핵심 기능 → 직접 만들어 보기)로 재구성됨. 아래 메모는 주제별 상세 배경 (섹션 라벨 "1장/2장/3장"은 옛 구조 잔재, 내용은 유효).

---

## 0. 한 줄 정의

Spring AI = **Java/Spring 진영의 LangChain**. 1.0은 "LLM 호출 라이브러리"였고, **2.0은 "에이전트 플랫폼"으로 이동**했다. 핵심 철학은 여전히 **이식성 + 추상화** — OpenAI/Anthropic/로컬 Ollama를 코드 변경 없이 교체. 2.0에서 이식성은 **도구·에이전트 상호운용(MCP)** 으로까지 확장.

## 1장. 그때 본 기본기 (recap — 2.0에서도 그대로 중심)

- **ChatClient**: fluent 체인 `prompt().user(message).call().content()`. `.call()`=동기, `.stream()`=SSE. `.content()`=텍스트, `.entity(Class)`=타입 객체. `ChatClient.Builder` 자동 빈 등록(WebClient.Builder 패턴).
- **Structured Output**: `.entity()` 한 줄. 내부 = 스키마 자동 생성 → 프롬프트 주입 → `BeanOutputConverter` 역직렬화. **`.entity()` 자체는 재시도 안 함** — `StructuredOutputValidationAdvisor` 별도(`maxRepeatAttempts`). 프로바이더 네이티브 강제 출력(`useProviderStructuredOutput()`)은 스트리밍과 병행 불가. 비즈니스 검증은 우리 책임.
- **RAG**: `QuestionAnswerAdvisor` 하나로 Retrieve→Augment→Generate 자동. "LLM에게 오픈북 시험." (발표 문서에선 라이트하게만 다룸 — RAG는 다들 아는 recap. 아래는 그 심화 노트.)
  - **`QuestionAnswerAdvisor` 동작(요청마다)**: ① 질문 가로챔 → ② `vectorStore.similaritySearch(질문)`로 관련 문서 top-N → ③ 내장 프롬프트 템플릿에 문서를 컨텍스트로 끼움 → ④ 증강 프롬프트를 LLM에 전송, 응답 반환. "마법"이 아니라 **검색 + 프롬프트 조립을 대신 하는 인터셉터**.
  - **증강(Augment)의 실체** = 검색한 문서를 프롬프트 텍스트에 붙이는 것. 예) 입력 "우리 환불 정책?" → LLM이 실제로 받는 것: `아래 컨텍스트를 근거로 답하라. [컨텍스트: 환불 규정 단락들] 질문: 우리 환불 정책?`. 모델은 안 건드리고 프롬프트만 조립 → 그래서 재학습 불필요.
  - **VectorStore**: 문서를 임베딩(의미 벡터)으로 저장하고 유사도로 검색하는 공통 인터페이스. 구현체(pgvector/Redis/Chroma 등) 교체 가능 → 앱 코드 동일(이식성). 핵심 연산 `add(documents)` / `similaritySearch(query)`.
- 태도: **LLM 출력을 신뢰하지 말고 외부 입력처럼 다뤄라.** (2.0에서도 유효)

## 2장. 무엇이 달라졌나 (1.x → 2.0)

### 기반 업그레이드 (breaking, 마이그레이션 필요)
- **Spring Boot 4.0/4.1 + Framework 7** 베이스라인.
- Jackson 3 업그레이드.
- JSpecify 널 안정성 전면 적용.
- 프로바이더 변형 구현 정리(OpenAI 3종→1종 등).

### Tool Calling 표준화 — 진화의 핵심
- 2.0은 **`ToolCallback`/`FunctionToolCallback`으로 일원화**.
  - ~~`FunctionCallback`이 1.x에 있다가 2.0에서 제거~~ → **사실 아님**. `spring-ai-core:1.0.0-M6`에 16개, `spring-ai-model:1.0.0`(GA)부터 0개. 1.0 GA 전에 정리된 API.
- `internalToolExecutionEnabled` 옵션 제거 → **`ToolCallingAdvisor`가 1급 컴포넌트 + 자동 등록**. `.tools()`만 주면 호출 루프 자동 처리.
- 의미: 툴 = 에이전트의 손발. 2.0이 이를 프레임워크 **정식 파이프라인으로 승격**. 철학은 그대로 제어의 역전(IoC).
- 마이그레이션 메모: `.functions()`→`.tools()`, bare Function 빈 → `ToolCallback` 빈 + `FunctionToolCallback.builder()`.

## 3장. 트렌드 (2.0가 겨냥하는 것)

### ToolSearch — 점진적 도구 노출
- 문제: 도구 수백 개를 프롬프트에 다 넣으면 토큰 폭발 + LLM 혼란.
- 답: **`ToolSearchToolCallingAdvisor`** — 질문에 맞는 도구만 검색해 노출.
- 설정: `spring.ai.chat.client.tool-search-advisor.enabled=true`, 인덱스 `tool-index-type=regex(기본)/lucene/vector`.
- 발상: RAG(문서 검색 주입)의 도구판. "위임 vs 통제" — 수백 개 쥐여주되 노출은 통제.

### MCP — 도구의 USB-C (최대 트렌드)
- **MCP(Model Context Protocol)** = 도구·리소스·프롬프트를 노출하는 표준 프로토콜. AI 도구계의 USB-C.
- Spring AI 2.0이 1급으로 품음:
  - 서버 애노테이션: `@McpTool`/`@McpResource`/`@McpPrompt`/`@McpComplete`(+ `@McpToolParam`). 선언만 하면 MCP 서버로 노출, JSON 스키마 자동.
  - 클라이언트 애노테이션: `@McpSampling`/`@McpElicitation`/`@McpLogging`/`@McpProgress` 등. 남의 MCP 서버를 내 도구로.
  - **전송 편입**: `mcp-spring-webflux`/`mcp-spring-webmvc`가 MCP Java SDK → Spring AI로 이동(그룹 id `io.modelcontextprotocol.sdk`→`org.springframework.ai`, breaking). 기본 전송 Streamable HTTP.
- 의미: 이식성의 최종 확장. 1.0 "벤더 교체" → 2.0 "도구·에이전트가 벤더·언어 넘어 상호운용". 내 서비스가 남 에이전트의 도구가 되고, 남 도구가 내 에이전트의 손발이 됨.

## 4. 그래서 백엔드 개발자의 역할

무게중심이 **"흐름을 직접 제어"에서 "위임하되 통제"로** 이동(2.0에서 더 강해짐).

- 검증된 도구를 만든다 — `@Tool`을 넘어 `@McpTool`로 표준 노출까지.
- 지식을 공급한다 — RAG.
- 가드레일을 친다 — 위험 작업 차단 / 도구 노출 통제(ToolSearch) / 사람 승인.
- 출력을 검증한다 — LLM 출력 = 외부 입력.

LLM은 오케스트레이터, 우리는 그것이 안전하게 놀 **운동장과 규칙**을 만든다. 2.0은 그 운동장을 넓히고, **다른 운동장과 연결(MCP)** 했다.

## 검증 메모 (출처 대조)

- 2.0 GA 사실 + 기능: `docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/`(getting-started, upgrade-notes, mcp-overview, mcp-server-boot-starter, mcp-annotations-overview)로 대조 완료.
- 2.0 GA 릴리스: 2026-06-12(공식 블로그). 학습 컷오프 이후라 블로그 요약은 2.0-SNAPSHOT 레퍼런스 문서로 교차검증함.
- 주의: 1.x 시절 자료엔 "2.0은 아직 마일스톤(2.0.0-M7)"으로 적힌 게 있었음 — 2.0 GA 출시로 폐기된 서술.

## 정정 이력 — "무게중심이 에이전트로 이동"

본문 서론에 오래 이렇게 적혀 있었다. 릴리스 서사를 그대로 받아쓴 서술이라 실물과 대조해 보니 과장이었다.

**대조 결과** — `spring-ai-bom-2.0.0.pom`의 아티팩트 **164개 중 이름에 `agent`가 들어간 것은 0개**다.

```bash
grep -o '<artifactId>[^<]*</artifactId>' spring-ai-bom-2.0.0.pom | grep -iE 'agent|session'   # 결과 없음
```

GA 발표문이 소개하는 에이전트 물건들(`spring-ai-agent-utils`의 Agent Skills, 이벤트 소싱 메모리 `spring-ai-session`)은 전부 **코어 밖 커뮤니티 프로젝트**라 BOM에 없다. 발표문 자체도 분량으로는 `Improved foundations`(Boot 4, Jackson 3, JSpecify, options 리팩터링, 벤더 정리)가 최대 항목이다.

**무엇이 문제였나** — "무게중심이 에이전트로 갔다"고 하면 듣는 사람은 `Agent` 같은 클래스를 기대하는데, 열어보면 없다. 발표 자리에서 "그래서 에이전트 뭐 쓰면 되나요"에 답이 막힌다.

**어떻게 고쳤나** — 2.0이 한 일은 에이전트를 **가져온** 것이 아니라 에이전트를 **얹을 자리**를 연 것이다(도구 실행 루프를 모델 구현 밖 Advisor 체인으로 꺼내고, 체인이 재진입을 지원). GA 발표문 인용이 정확히 이 말이다 — build **on top of** tool calling.

**배치도 함께 고쳤다** — 처음엔 근거를 서론에 붙였는데, 이 주장은 Tool Calling(§4)과 Advisor(§8)를 알아야 이해되는 **결론**이라 서론에서는 스캐폴딩이 계속 늘어났다. 문장의 문제가 아니라 배치의 문제였다. 지금은 문서 끝 `1.x → 2.0 한눈에` 아래 "그래서 2.0은 에이전트 프레임워크인가" 절에 있다. 독자가 §4·§6·§8을 다 보고 직전 절에서 에이전트를 직접 조립해 본 뒤라, 같은 내용이 변명이 아니라 회수로 읽힌다.

> 교훈: 릴리스 노트의 **절 제목**은 근거가 아니다. 벤더가 무엇을 강조하고 싶은지를 보여줄 뿐이고, 실제로 무엇이 들어왔는지는 BOM·jar로 세어야 한다.

## ToolSearch 자동 구성(property 방식)의 함정 — 2.0.0 기준

본문에서는 "제약이 있어 수동 구성을 권한다" 한 문장으로 줄였고, 그 제약의 상세를 여기 남긴다. `spring.ai.chat.client.tool-search-advisor.enabled=true`로 자동 구성할 때:

- **적용 범위가 전역이다.** 이 프로퍼티는 기본 `ToolCallingAdvisor`를 앱 전체에서 교체한다. 켜는 순간 도구를 쓰지 않는 호출까지 세션 ID를 요구하므로(`chat_memory_conversation_id`), 앱 전체가 ToolSearch를 쓸 때만 적합하다.
- **starter가 별도이고 BOM에 없다.** autoconfigure는 `spring-ai-starter-tool-search-advisor`에 들어 있는데(라이브러리인 `spring-ai-tool-search-advisor`에는 없다), `spring-ai-bom:2.0.0`이 이 starter를 관리하지 않아 버전을 직접 지정해야 한다.
- **조용히 건너뛸 수 있다.** Advisor 빈은 `@ConditionalOnBean(ToolCallingManager, ToolIndex)` 조건인데, 이 autoconfigure가 `ToolCallingAutoConfiguration`보다 먼저 평가되어 `ToolCallingManager`를 아직 못 찾는 경우가 있다. 프로퍼티는 켜졌는데 Advisor는 등록되지 않고 오류도 나지 않는다. `ToolCallingManager`를 직접 빈으로 선언하면(사용자 빈이 먼저 등록된다) 해결된다.

## 데모 만들며 밟은 것 (Spring AI 아님, Boot 4 이야기)

### 자동설정이 모듈로 쪼개졌다

`spring.h2.console.enabled=true`만 켜고 재시작했는데 아무 일도 안 일어났다. `spring-boot-autoconfigure` 안을 뒤져도 `H2Console*` 클래스가 없다. Boot 4에서 `spring-boot-h2console`이라는 **별도 아티팩트**로 빠졌기 때문이다.

```kotlin
runtimeOnly("org.springframework.boot:spring-boot-h2console")
```

3.x에서 올라오면 반드시 밟는다. 속성이 무시되는 게 아니라 자동설정 자체가 클래스패스에 없는 것이라, 로그에도 단서가 안 남는다.

### `file:` 모드 H2는 Boot이 embedded로 안 본다

콘솔에서 `sa`로 붙으니 `Wrong user name or password [28000]`. 원인은 `EmbeddedDatabaseConnection`의 판정 조건이다.

```java
case H2 -> url.contains(":h2:mem");
```

`jdbc:h2:file:...`은 여기 안 걸린다. 그래서 `DataSourceProperties.determineUsername()`이 `"sa"`가 아니라 **`null`** 을 반환하고, DB도 사용자명 없이 만들어진다. 콘솔에서는 **User Name을 비워야** 붙는다.

`spring.datasource.username=sa`를 명시하면 정리되지만, 이미 만들어진 파일에는 소급되지 않는다(`data/` 지우고 다시 생성해야 함).

### H2 콘솔 접속 정보

`data/`가 gitignore라 각자 만들어진다. 콘솔에서 한 번 붙어두면 `~/.h2.server.properties`에 저장돼 다음부터는 클릭만 하면 된다.

| 항목 | 값 |
| --- | --- |
| JDBC URL | `jdbc:h2:file:./data/chatmemory` |
| User Name | (비움) |
| Password | (비움) |

```sql
SELECT * FROM SPRING_AI_CHAT_MEMORY ORDER BY conversation_id, sequence_id;
```

`conversation_id`가 다르면 대화가 격리된다는 걸 데이터로 보여줄 수 있다 — 5번 데모에서 `session-1`은 이름을 기억하고 `session-2`는 "처음 만났다"고 답하는 게 한 화면에 나온다.

### 파일 잠금

H2 파일 모드는 프로세스 하나만 붙는다. 앱이 도는 동안 IntelliJ나 CLI로 붙으면 `Database may be already in use`. 웹 콘솔은 같은 JVM 안이라 엔진 인스턴스를 공유해서 괜찮다. 밖에서 붙어야 하면 URL에 `AUTO_SERVER=TRUE`를 붙인다.

## 그 외

- Observability: Micrometer 연동(토큰 사용량·레이턴시).
- 멀티 벤더/VectorStore 다수 지원(2.0에서 구현 정리).
