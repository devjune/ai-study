# 에이전틱 패턴 21개 — Spring AI 구현

*Agentic Design Patterns*(Antonio Gulli, Springer 2025)가 정리한 **21개 패턴을 원서 목차 순서 그대로** Spring AI로 구현한 예제입니다.

**Spring Boot 4 + Spring AI 2.0.1 + 로컬 Ollama. API 키가 필요 없습니다.**

문서는 [Spring AI로 구현하는 에이전틱 패턴](../docs/phase2/Spring%20AI로%20구현하는%20에이전틱%20패턴.md), 사내망에서 돌리려면 [offline-kit.md](../docs/phase2/offline-kit.md).

## 준비

```bash
brew install ollama
brew services start ollama

ollama pull qwen3:4b-instruct   # 2.5 GB — 기본 모델
ollama pull granite4:micro      # 2.1 GB — 8·12·18·19장과 18장 문지기
ollama pull bge-m3              # 1.2 GB — 임베딩 (9·14장)
ollama pull qwen3:1.7b          # 1.4 GB — 16장 자원 최적화 비교용

./gradlew bootRun
```

http://localhost:8080

## 무엇을 보여주는가

**패턴마다 모델을 몇 번, 어떤 순서로 부르는지가 다릅니다.** 화면은 그것을 보여줍니다.

- 왼쪽에서 21개 패턴 중 하나를 고릅니다
- 그 패턴이 쓰는 Spring AI 타입과 코드가 문서와 같은 내용으로 뜹니다
- 실행하면 **모델 호출 타임라인**이 남습니다. 막대 하나가 호출 한 번이고, 펼치면 그 호출에 나간 프롬프트 전문이 보입니다

`@Tool` 하나 붙였을 뿐인데 호출이 두 번인 것, 자기 검토가 검토 때문에 늘어나는 것이 타임라인에서 갈립니다.

## 코드 구조

```
com.example.demo
├── pattern/
│   ├── Model.kt              PatternRunner · PatternSpec · RunResult · ModelCall
│   ├── RunContext.kt         TraceAdvisor — 호출마다 프롬프트를 붙잡는다
│   ├── Docs.kt               사내 규정 문서 — 9·14장이 검색한다
│   ├── Mcp.kt                @McpTool 선언과 자기 /mcp 호출
│   ├── SupportTools.kt       @Tool — 조회는 실행, 취소는 승인 요청만
│   ├── PatternController.kt  실행 · 승인
│   ├── ch01/PromptChaining.kt
│   ├── ch02/Routing.kt
│   ├──   …                   장 하나에 패키지 하나, 파일 하나
│   └── ch21/Exploration.kt
└── support/OrderService.kt   주문 저장소 — 13장에서 실제로 상태가 바뀐다
```

장을 찾으려면 `ch05/` 로 갑니다. 그 장이 쓰는 그릇(`Outline` · `Plan` · `Task` …)도 러너 바로 아래에 함께 있습니다.

## 구현 메모

- **타임라인의 막대 = 모델이 실제로 불린 횟수.** 도구 호출처럼 `.call()` 한 번이 안에서 두 번 오가면 막대도 두 개입니다. 그래야 패턴 간 비교가 정직합니다.

- **프롬프트를 잡는 것은 `TraceAdvisor`** 입니다. `order` 를 `1000` 으로 주어 도구 루프(`ToolCallingAdvisor`, `MIN_VALUE + 300`) **안쪽**에 세웁니다. 그래야 왕복마다 다시 불려 전부 잡힙니다.

- **도구 메시지는 `text` 가 비어 있습니다.** 호출 요청은 `AssistantMessage.getToolCalls()`, 실행 결과는 `ToolResponseMessage.getResponses()` 에 있습니다. `text` 만 찍으면 왕복에서 가장 중요한 두 줄이 빈칸으로 나옵니다.

- **화면의 코드에는 추적 래퍼가 없습니다.** 실제 러너는 `ctx.step("라벨") { … }` 으로 감싸 타임라인에 라벨을 남기지만, 화면에는 패턴의 알맹이만 싣습니다.

- **10장은 이 앱 자신이 MCP 서버입니다.** `spring.ai.mcp.server.protocol=STATELESS` 로 두면 세션 없이 `/mcp` 에 POST 한 번으로 `tools/list` 가 옵니다. `@McpTool` 선언에서 JSON 스키마가 자동 생성되는 것을 실물로 봅니다.

- **15장은 최소 A2A 서버까지 자기 앱에 얹었습니다.** `/.well-known/agent.json` 과 `/a2a` 두 엔드포인트가 전부고, 원격 에이전트 역도 자기가 맡습니다. Spring AI 2.0.1 BOM 에 A2A 가 없어서입니다.

- **13장은 상태가 실제로 바뀝니다.** 승인 버튼으로 취소된 주문은 다음 실행 때 스스로 되돌립니다.

- **재시도 끔** (`spring.ai.retry.max-attempts=1`) — 12장이 일부러 없는 모델을 불러 실패시키는데, 기본값 10회면 몇 분을 기다립니다.

- **온도 0.1 고정** — 실행마다 답이 흔들리면 무엇이 패턴 차이고 무엇이 샘플링 노이즈인지 구분할 수 없습니다. 21장만 예외로 0.9 를 씁니다(후보를 서로 다르게 만들어야 하므로).
