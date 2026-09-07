# Spring AI 2.0 데모

스터디 문서([`docs/phase1`](../docs/phase1))의 예제를 실제로 동작시키는 Spring Boot 앱.
**Spring Boot 4 + Spring AI 2.0.1 + Anthropic(claude-sonnet-4-5)**. 흰/검 단일 페이지 UI에서 각 기능을 눌러 확인한다.

## 요구사항

- JDK 21
- `ANTHROPIC_API_KEY` 환경변수 (API 키는 코드/설정에 넣지 않고 환경변수로만 주입)

## 실행

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew bootRun
```

브라우저에서 http://localhost:8080 접속.

> 최초 실행 시 RAG용 임베딩 모델(all-MiniLM-L6-v2, ONNX)을 내려받는다(수십 MB, 로컬 실행이라 Anthropic 외 추가 키 불필요). 한 번 받으면 로컬에 캐시되므로 이후 기동은 오프라인으로 동작한다.
>
> **외부망이 막힌 사내망이면 이 다운로드가 실패해 임베딩 초기화가 깨진다.** 기본 URI가 githubusercontent라서다. 캐시를 미리 채워 두거나, 아래 프로퍼티를 사내 미러로 돌린다.
>
> ```properties
> spring.ai.embedding.transformer.onnx.model-uri=...     # ONNX 모델 (미러)
> spring.ai.embedding.transformer.tokenizer.uri=...      # 토크나이저 (미러)
> spring.ai.embedding.transformer.cache.directory=...    # 캐시 위치
> ```

## 코드 구조

기능 하나가 패키지 하나다. 앞의 `chNN`은 학습 자료의 장 번호라, 문서에서 궁금한 기능을 번호로 바로 찾을 수 있다.

```
com.example.demo
├── DemoApplication.kt
├── support/            ChatRequest, ConversationRequest, ChatReply (공용 DTO)
├── ch01_basic/         ChatClient 동기 호출 · 스트리밍 · 페르소나
├── ch02_structured/    .entity() + Movie 타입
├── ch03_memory/        MessageChatMemoryAdvisor + ChatMemory 빈
├── ch04_tool/          @Tool (WeatherTools)
├── ch05_rag/           QuestionAnswerAdvisor + VectorStore 시드
├── ch06_toolsearch/    ToolSearchToolCallingAdvisor (ManyTools)
├── ch07_observability/ 토큰 사용량 메타데이터
├── ch08_advisor/       커스텀 Advisor (BaseAdvisor) — 카드번호 마스킹 가드레일
├── ch09_mcp/           @McpTool — 이 앱을 MCP 서버로 노출
└── ch10_agent/         supportAgent — RAG+메모리+Tool 결합 (직접 만들어 보기)
```

## 데모 ↔ 문서 예제 매핑

| UI 섹션 | 엔드포인트 | 문서 예제 |
| --- | --- | --- |
| 1. 기본 채팅 | `POST /api/basic/chat` | ChatClient 동기 호출 |
| 2. 스트리밍 | `GET /api/basic/stream` (SSE) | ChatClient `.stream()` |
| 3. 페르소나 | `POST /api/basic/persona` | 시스템 프롬프트 `.system()` |
| 4. Structured Output | `POST /api/structured/movie` | `.entity(Movie)` |
| 5. 대화 메모리 | `POST /api/memory/chat` | `MessageChatMemoryAdvisor` |
| 6. Tool Calling | `POST /api/tool/chat` | `@Tool` |
| 7. RAG | `POST /api/rag/chat` | `QuestionAnswerAdvisor` + `VectorStore` |
| 8. ToolSearch | `POST /api/toolsearch/chat` | `ToolSearchToolCallingAdvisor` |
| 9. 관측성 | `POST /api/observability/chat` | 토큰 사용량 메타데이터 |
| 10. 커스텀 Advisor | `POST /api/advisor/chat` | `BaseAdvisor` (before/after) |
| 11. 사내 문서 Q&A 에이전트 | `POST /api/agent/chat` | `supportAgent` (RAG+메모리+Tool 결합) |

## 구현 메모

- **API 키** — `application.properties`에서 `${ANTHROPIC_API_KEY}`로 주입. 하드코딩 없음.
- **RAG 임베딩** — 로컬 ONNX(`spring-ai-starter-model-transformers`). 사내 문서 3건(환불 정책·영업시간)을 인메모리 `SimpleVectorStore`에 시드.
- **대화 메모리 영속화** — `spring-ai-starter-model-chat-memory-repository-jdbc` + H2 **파일** 모드(`./data/chatmemory`). 도커 없이 실행하려고 H2를 썼고, 운영이라면 같은 코드에 Postgres 설정만 바꾸면 된다. 스키마는 starter 내장 `schema-h2.sql`을 `initialize-schema=always`로 생성한다. `data/`는 gitignore 대상.
- **에이전트(캡스톤)** — `ch10_agent`는 앞 장의 빈(`vectorStore` §5, `chatMemory` §3)을 그대로 주입받아 하나의 `supportAgent` ChatClient로 결합한다. 새로 만든 것은 `OrderTools`와 빈·컨트롤러뿐이다. 이 앱의 유일한 `ChatClient` 빈이라 컨트롤러가 이름 없이 주입받는다(다른 컨트롤러는 `ChatClient.Builder`에서 각자 build). 메모리 Advisor를 얹었으니 대화 ID가 필수라, UI는 2턴 시나리오로 메모리 기여를 눈에 보이게 한다.
- **MCP** — 화면 섹션은 이번 스터디에서 제외했다. 다만 서버 코드(`mcp/McpTools.kt`)와 의존성은 남아 있어, 앱은 여전히 `/mcp`로 `add`·`echo`를 노출한다(`@McpTool`). 필요해지면 화면 섹션만 되살리면 된다. 기동 로그의 `McpServerAnnotationScanner...` WARN은 무해한 BeanPostProcessor 경고.

## 검증 상태

- `./gradlew build` 성공.
- 앱 기동 성공 — 전체 빈 wiring(8개 컨트롤러 + MCP 서버 + ToolSearch + RAG 임베딩) 확인.
- HTML 서빙·엔드포인트 라우팅 확인.
- **실제 LLM 응답은 유효한 `ANTHROPIC_API_KEY`로 실행해 확인한다** (더미 키로는 Anthropic 인증 단계에서 실패).
