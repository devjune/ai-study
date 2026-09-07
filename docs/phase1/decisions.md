# 스터디 문서 방향 & 결정 로그

[`Spring AI 2.0 — 개념부터 에이전트까지.md`](./Spring%20AI%202.0%20—%20개념부터%20에이전트까지.md)(학습 자료 본문)를 만들며 내린 결정과 그 근거를 남긴다.
본문에는 들어가지 않는 "왜 이렇게 썼나"의 기록.

---

## 목표

- 팀 내 AI 스터디 **학습 자료** (발표 겸 위키 포팅용).
- 대상: Java/Spring 백엔드 개발자 — **Spring AI를 처음 접하는 사람 포함**.
- 주제: Spring AI **2.0**.
- 목표 수준: 읽고 나면 (1) "Spring AI로 무엇을 할 수 있나"에 정확히 답하고, (2) 직접 간단한 AI 기능을 붙일 수 있을 것.
- 최종 산출물은 위키로 포팅.

## 컨셉

Spring AI 2.0을 기준으로 **처음부터 제대로 가르치는 학습 문서**. 1.x는 초반에 간단히 소개하고, 본문은 2.0 기준으로 상세히 설명한다. 처음 접하는 사람도 온보딩(시작하기)부터 따라와 직접 만들 수 있게 하는 것이 핵심. 관통 축은 **"라이브러리에서 에이전트 플랫폼으로의 진화"**.

> 방향 전환 이력: 초기엔 "예전에 1.0 봤고 2.0으로 다시 본다"는 발표 서사(3장: recap→진화→트렌드)였으나, **처음 접하는 사람까지 포함하는 학습 자료**로 전환하며 폐기. 1인칭 회고 톤도 제거.

## 결정 사항

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 구성 | 온보딩 → 상세 → 실습 (들어가며 · 무엇을·누가 · 시작하기 · 핵심 기능 9개 · 직접 만들어 보기 · 1.x→2.0 · 정리) | 처음 접하는 사람도 따라오도록 시작 설정부터. 각 기능은 개념→예제→(1.x 대비 변화). |
| 범위 | ChatClient·Structured Output·ChatMemory·Tool Calling·RAG·ToolSearch·MCP·관측성 + 조합 예제(사내 문서 Q&A 에이전트) | 처음 접하는 사람이 "만들 수 있게" 필요한 기능을 폭넓게. 얕더라도 빠짐없이. |
| MCP 비중 | **크게** (2.0 핵심) | 2.0의 가장 큰 방향 전환. 애노테이션 예제 + 조합 예제에 포함. |
| 사실성 | 팩트 기반만. 추측/추론 배제 | 모든 기술 서술을 공식 2.0 레퍼런스·블로그와 대조. 해석적 프레이밍(LangChain 비유, IoC, 위임 vs 통제)은 "관점"으로만 사용하고 사실과 구분. |
| 톤 | 존댓말 "~합니다"체 + **위키 문체(중립·서술)** | 논설·구어 연결어("여기서부터 본론"…)와 1인칭("우리/내") 제거. 볼드 TL;DR + before/after 표는 유지. (평서문→존댓말→위키 중립화 순으로 전환) |
| 형식 | 마크다운 + mermaid 다이어그램 | 위키 포팅 고려. 표준 마크다운·mermaid만(외부 이미지 X). |
| 실습 | 본문에 코드 예제 충실히 + 끝에 "직접 만들어 보기" 조합 예제 | 학습 자료로 전환하며 "데모 몰아서" 규율은 완화. 코드가 곧 실습 자료. |
| 기준 버전 | **Spring AI 2.0.0 GA (2026-06-12)** | 2.0 기준 학습 자료. |
| 버전 진화 | 1.x→2.0 변화는 각 기능 옆 + 별도 요약 표 | 학습 자료라 "진화 서사"보다 "각 기능이 뭐고 1.x 대비 뭐가 달라졌나"가 중심. 끝에 "1.x→2.0 한눈에" 표. |

## 관통하는 두 테마

발표 전체를 꿰는 축. 개별 기능 설명이 여기로 수렴하도록 배치. 2.0의 새 기능(ToolSearch·MCP 등)도 결국 이 두 테마로 수렴한다.

- **이식성(Portability)** — 벤더/DB 교체를 넘어, 2.0에서는 **도구·에이전트 상호운용(MCP)** 으로 확장.
- **위임 vs 통제** — LLM에 흐름을 얼마나 맡기고, 어디에 가드레일을 칠 것인가. 에이전트가 커질수록(ToolSearch·MCP) 더 중요해짐.

## 검증된 사실 (공식 출처 대조 완료)

- **Spring AI 2.0.0 GA: 2026-06-12 릴리스**(공식 블로그, 작성자 Christian Tzolov). 기능은 `docs.spring.io/spring-ai/reference/2.0/`로 교차검증.
- 에이전트 이동 배경(블로그 명시): ① 1.x는 tool-calling 루프가 모델 구현 내부에 숨어 hook/wrap/교체 불가 → advisor 체인의 1급 컴포넌트로 승격, ② 유입 PR의 다수가 coding agent 관련, ③ "MCP가 AI 통합의 공통 프로토콜로 부상". (배경: MCP는 Anthropic이 2024 공개 후 업계 표준화 — 블로그 밖 일반 사실, 컷오프 2026-01)
- 2.0 기반: Spring Boot 4.0/4.1 + Framework 7, Jackson 3, JSpecify 널 안정성.
- Tool Calling: `ToolCallback`/`FunctionToolCallback` 일원화. `ToolCallingAdvisor` 1급 컴포넌트 + 자동 등록. `internalToolExecutionEnabled` 제거(1.1.8 존재 → 2.0.0 없음, jar 확인).
  - 정정: `FunctionCallback`을 "2.0에서 제거"로 적었으나 **틀림**. jar 대조 결과 `spring-ai-core:1.0.0-M6`에 16개 존재 → `spring-ai-model:1.0.0`(GA)에 이미 0개. 1.0 GA 이전에 사라진 API라 1.x↔2.0 대비 항목이 아니다.
- `ToolSearchToolCallingAdvisor`: `spring.ai.chat.client.tool-search-advisor.enabled`, 인덱스 regex(기본)/lucene/vector.
- MCP: 서버 애노테이션 `@McpTool`/`@McpResource`/`@McpPrompt`/`@McpComplete`, 클라이언트 `@McpSampling`/`@McpElicitation` 등. 전송(webflux/webmvc)이 MCP Java SDK→Spring AI로 편입(그룹 id 변경, breaking). 기본 전송 Streamable HTTP.
- 기본기(ChatClient/`.entity()`/`StructuredOutputValidationAdvisor`/`QuestionAnswerAdvisor`/VectorStore)는 2.0에서도 유지.
- 폐기된 서술: 1.x 시절 자료의 "2.0은 아직 마일스톤(2.0.0-M7), GA 아님" — 2.0 GA 출시로 무효.
- Anthropic 스타터: `spring-ai-starter-model-anthropic`. 이 자료의 예제는 `spring.ai.anthropic.chat.model`에 `claude-sonnet-4-5`를 명시 지정(스타터 기본값과 무관하게 동작 보장).

## 발표·실습 운영

학습 자료로 전환하며 기존 규율(20분 엄수, "데모 몰아서")은 완화했다. 발표 시에는 이 문서에서 필요한 섹션을 발췌해 쓴다.

- 발표용 발췌 추천: 들어가며 → 무엇을·누가 → 시작하기(라이브) → Tool Calling → MCP → 직접 만들어 보기.
- "직접 만들어 보기"(사내 문서 Q&A 에이전트)를 라이브 코딩 클라이맥스로 활용 가능. 셋업 리스크가 크니 사전 리허설 권장.
- 데모는 실제 Spring Boot 4 + Spring AI 2.0(Anthropic) 프로젝트로 준비(별도 진행, ANTHROPIC_API_KEY 필요).

## 참고

- Spring AI Reference (2.0): https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/
- 2.0 GA 블로그: https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now
- 2.0 업그레이드 노트: https://docs.spring.io/spring-ai/reference/upgrade-notes.html#upgrading-to-2-0-0
- 1.0 GA 블로그: https://spring.io/blog/2025/05/20/spring-ai-1-0-GA-released/
- 1.1 GA 블로그: https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/

## 기준 버전 갱신 — 2.0.0 → 2.0.1 (2026-09-07)

위 표의 "기준 버전" 행은 작성 시점 기록이라 그대로 두고, 여기에 갱신분을 덧붙인다.

**Spring AI 2.0.1 (2026-08-21 릴리스)** 로 올렸다. 패치 릴리스지만 학습 자료의 서술을 두 곳 바꾼다.

- **도구 호출 상한이 새로 들어왔다.** `spring.ai.tools.limits.*` — 도구당 40, 전체 150, 초과 시 `ToolCallLimitExceededException`. 설정하지 않아도 켜져 있다. §4의 제어의 역전(IoC) 서술이 "위임한다"에서 "위임하되 끝은 코드가 지킨다"로 한 겹 정확해졌다. 기본값 40·150 은 `spring-ai-autoconfigure-model-tool-2.0.1.jar` 의 `ToolCallingProperties$Limits` 바이트코드에서 확인했다(문서 사이트가 아니라 아티팩트 기준).
- **ToolSearch 자동 구성의 제약이 절반 풀렸다.** BOM 아티팩트가 164 → 169 개로 늘면서 `spring-ai-starter-tool-search-advisor` 와 `spring-ai-autoconfigure-tool-search-advisor` 가 편입됐다. "starter 가 BOM 에 없다"는 서술은 폐기. 다만 **적용 범위가 전역이라는 지적은 유효하다** — 자동 구성이 내놓는 것이 공유 `ToolCallingAdvisor.Builder` 빈이라, 켜면 그 빈을 쓰는 `ChatClient` 가 전부 영향을 받는다.

- **도구 호출의 토큰 집계가 고쳐졌다.** 2.0.1 에 `org.springframework.ai.chat.client.advisor.UsageAccumulator` 가 새로 생겼고, `ToolCallingAdvisor` 의 스트리밍 메서드 셋(`internalStream`·`streamWithToolCallResponses`·`handleToolCallRecursion`)이 이 타입을 파라미터로 받도록 시그니처가 바뀌었다. 블로킹도 해당된다 — `adviseCall` 은 직접 참조하지 않지만 `doFinalizeLoop` 이 12 곳에서 쓰고, 블로킹 경로가 그 메서드를 거친다. **2.0.0 jar 에는 이 클래스가 아예 없다.** 도구를 쓰는 요청에서 `chatResponse().metadata.usage` 를 과금 집계에 쓰고 있었다면 2.0.0 까지는 적게 잡혔다는 뜻이라, §9 에 경고로 넣었다.

**우리에게 해당 없는 breaking change** — Mistral 상수 제거, Redis 아티팩트명 변경, OpenAI strict 기본값, `Media` 빌더, `DeepSeekApi` 개정, Couchbase 클라이언트. phase1 은 Anthropic 만 쓴다.

**검증 범위** — 컴파일과 의존성 해석(`spring-ai-anthropic:2.0.1`)까지만 확인했다. API 키가 필요해 실행 검증은 하지 못했다.
