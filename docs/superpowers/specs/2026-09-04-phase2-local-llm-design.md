# phase2 설계 — 로컬 LLM 연동

작성일: 2026-09-04
상태: 승인됨

## 배경

phase1(`Spring AI 2.0 — 개념부터 에이전트까지`)은 핵심 기능 9개를 폭넓게 다뤘다. 본문 664줄 중 454줄이 기능 설명이고 기능당 평균 50줄로, **폭은 넓고 깊이는 균일하게 얕다.** 데모는 Anthropic(`claude-sonnet-4-5`) 한 벤더로만 동작했다.

phase2는 그 위에서 **로컬 LLM 연동**을 다룬다. 궁극적으로는 사내 로컬 LLM에 붙이는 것이 목표이고, 그 1단계다.

## 왜 이 주제인가

phase1 문서의 1번 관통 테마가 **이식성**이다. "벤더를 바꿔도 코드는 유지된다"를 문서는 설명만 했고, 데모는 한 번도 검증하지 않았다. phase2는 그 주장을 가장 가혹한 조건(상용 프런티어 모델 → 로컬 오픈 모델)에서 시험한다.

학습 목표는 "된다/안 된다"가 아니라 **"어디서 깨지는가"** 다.

## 참여자가 얻어갈 것

1. **이식성의 경계선** — ChatClient 호출 코드는 안 바뀌지만 그 위층은 어떤가
2. **기능별로 요구되는 모델 지능의 서열** — 구조화 출력·도구 호출·RAG·다단계 에이전트 중 무엇이 몇 B부터 되는가. 벤더 문서에 없는 지식이고 사내 도입 의사결정에 직결된다
3. **로컬 운영의 실물 감각** — 모델 크기↔메모리↔속도, 첫 토큰 지연, 컨텍스트 한계
4. **사내 도입 관점** — 데이터 미유출, 비용이 종량제에서 고정비로, 사내망 제약
5. **진입 장벽 0** — phase1은 각자 `ANTHROPIC_API_KEY`가 필요했다. phase2는 키가 하나도 필요 없다

## 범위

**포함**: 로컬 모델 연동, 모델 간 거동 비교, 오프라인 반입
**제외**: MCP 상호운용 및 LangChain/LangGraph 결합 → phase3

## 디렉토리 구조

```
README.md              허브 (phase 목록 + 관통 테마)
docs/
├── phase1/            완료·동결
│   ├── Spring AI 2.0 — 개념부터 에이전트까지.md
│   ├── decisions.md
│   └── study-log.md
└── phase2/            진행
    ├── decisions.md
    ├── study-log.md   관측 기록이 쌓일 곳
    └── offline-kit.md 사내망 반입 절차
demo/                  phase2 기준 재출발, 단일 Gradle 프로젝트
```

**문서는 phase로 가르고 코드는 하나로 둔다.** phase별로 Gradle 프로젝트를 복제하면 "같은 코드가 다른 모델에서 돈다"를 보여줄 수 없어 주제와 어긋난다.

## demo 재출발

phase1 예제(`ch01`~`ch10`)는 보존하지 않는다. git 히스토리에 남는다.

**제거**: `ch01`~`ch10` 전부, `spring-ai-starter-model-anthropic`, `ANTHROPIC_API_KEY` 관련 일체
**유지**: Gradle 설정, `DemoApplication`, 공용 DTO, H2 대화 메모리, 로컬 ONNX 임베딩
**투입**: `spring-ai-starter-model-ollama`

결과적으로 **API 키가 하나도 필요 없는 저장소**가 된다.

## 핵심 화면 — 모델 비교 격자

phase1 데모가 "기능별 버튼 11개"였다면, phase2의 핵심 화면은 **같은 요청 × 모델 N개를 나란히** 놓는 것이다. "어디서 무너지는가"는 설명보다 나란히 놓고 보는 편이 빠르다.

```
┌─ 프롬프트 + 시험 축 선택 ──────────────┐
│ 기본대화 │ 구조화출력 │ 도구호출 │ RAG  │
└────────────────────────────────────────┘
┌ qwen3:1.7b ┐┌ qwen3:8b ┐┌ granite3.3 ┐
│  응답      ││  응답     ││  응답       │
│  3.2s      ││  1.9s     ││  2.4s       │
└────────────┘└───────────┘└─────────────┘
```

응답·소요시간·성공여부를 나란히 표시한다. 작은 모델이 구조화 출력 파싱에 실패하거나 도구 호출을 놓치는 장면이 곧 데모의 클라이맥스다.

## 모델 전환 방식

**런타임 옵션으로 요청마다 모델을 지정한다.** 프로필 전환은 재시작이 필요해 비교 실습에 맞지 않는다.

```kotlin
chatClient.prompt(질문)
    .options(OllamaChatOptions.builder().model("qwen3:8b"))
    .call().content()
```

2.0에서 `ChatClient.options()`는 **빌더를 그대로** 받는다(`.build()` 하지 않음). 1.x에서 올라오면 컴파일이 깨지는 지점이다. (출처: 2.0 upgrade-notes)

ChatClient는 하나, 모델만 요청마다 바꾼다.

## 모델 구성

`registry.ollama.ai` 매니페스트 직접 조회 결과(2026-09-04 실측):

| 모델 | 크기 | 도구 호출 |
| --- | --- | --- |
| qwen3:0.6b | 0.49 GiB | — |
| qwen3:1.7b | 1.27 GiB | — |
| qwen3:4b | 2.33 GiB | 지원 |
| qwen3:8b | 4.87 GiB | 지원 |
| qwen3:14b | 8.64 GiB | — |
| glm4:9b | 5.08 GiB | **미지원** |
| gemma3:4b | 3.11 GiB | 미지원 |
| granite3.3 | 4.60 GiB | 지원 |

도구 호출 여부는 채팅 템플릿에 도구 정의가 포함되는지로 판정했다. **추정이므로 설치 후 `ollama show`로 재확인이 필요하다.**

**채택**: `qwen3:1.7b`(작은 모델의 한계) + `qwen3:8b`(실용 하한) + `granite3.3`(같은 급 다른 계열) + `glm4:9b`(도구 미지원 대조군). 합계 약 11 GiB.

GLM4를 대조군으로 남기는 이유 — "도구 호출을 지원하지 않는 모델에 도구를 주면 무슨 일이 일어나는가"가 그 자체로 학습 장면이다.

**개발 머신**: Apple M4 / RAM 32GB / 여유 181GB. 위 구성에 충분.

## 오프라인 반입 (사내망 시연용)

시연 대상은 사내망 노트북이며 **git과 brew만 열려 있다.**

### GitHub 배포 타진 결과 — 세 갈래 모두 불가·반쪽

- **GHCR을 Ollama 레지스트리로**: `registry.ollama.ai`가 Docker Distribution v2를 쓰고 GHCR 익명 토큰도 발급되지만(실측 확인), Ollama 클라이언트가 **블롭 pull에서 실패**한다(`no Location header in response`). 서드파티 OCI 레지스트리는 공식 미지원이며 관련 이슈가 열려 있다 (ollama/ollama#7244, #2745, #9409)
- **GitHub Releases에 GGUF**: 파일당 2 GiB 제한(qwen3:4b는 2.33 GiB로 초과). 총량·대역폭 제한은 없어 분할하면 우회 가능하지만, 실측 결과 자산이 `release-assets.githubusercontent.com`으로 리다이렉트된다 — **phase1에서 이미 사내망에 막혔던 호스트 계열**이다
- **Git LFS**: 무료 한도가 1GB 수준. 논외

### 채택 경로 — oras + GHCR

**brew가 열려 있다는 것은 ghcr.io가 열려 있다는 뜻이다.** Homebrew 병 저장소가 `https://ghcr.io/v2/homebrew/core`다(실측 확인).

Ollama가 GHCR에서 직접 못 받는 것이지, **제대로 된 OCI 클라이언트를 중간에 끼우면 우회된다.** `ollama`와 `oras` 모두 Homebrew 포뮬러로 존재한다.

```
[외부] GGUF → oras push → ghcr.io (공개 패키지)
[사내]  oras pull → GGUF → ollama create → 완료
```

```bash
brew install ollama oras
oras pull ghcr.io/<계정>/<저장소>:qwen3-8b
ollama create qwen3-8b -f Modelfile   # FROM ./qwen3-8b.gguf
```

임베딩 ONNX 캐시도 같은 방식으로 나른다 — phase1에서 막혔던 RAG 문제가 함께 풀린다.

**미검증**: 수 GB 레이어의 GHCR 푸시/풀(Homebrew 병은 수십 MB급이라 대용량 실증이 필요), 사내 프록시 환경에서의 `oras` 동작(`HTTPS_PROXY`).

### 대안

사내에 이미 제공되는 Qwen 서비스가 있다. OpenAI 호환이면 모델 반입 자체가 불필요해진다.

```properties
spring.ai.openai.base-url=<엔드포인트>
spring.ai.openai.chat.options.model=<모델명>
```

`/v1/models` 응답 여부로 판별한다. 확인되면 phase2 문서에 별도 절로 추가한다.

## 참조 정리

7558d5c의 파일명 변경 이후 방치된 옛 참조를 이번 이동분과 함께 수정한다.

| 파일 | 문제 |
| --- | --- |
| `README.md` | `./spring-ai-study.md` — 허브 구조로 전면 교체 |
| `docs/decisions.md` | 본문 파일명 옛것 |
| `docs/study-log.md` | 본문 파일명 옛것 |
| `demo/README.md` | `../spring-ai-study.md` + 매핑표가 phase1 코드 기준 |
| `application.properties:14` | `docs/study-log.md` 경로 |

루트 `data/`(H2 잔재, `demo/data/`와 중복)와 `demo/.kotlin/errors/` 삭제. `.gitignore`에 모델·GGUF 관련 항목 추가.

## 이행 순서

1. 미커밋 문서 변경 커밋 (완료: 4a22bbe)
2. `git mv`로 phase1 문서 이동, phase2 씨앗 생성
3. 참조 수정 + README 허브화
4. demo 재출발 (의존성 교체, phase1 코드 제거, 비교 백엔드 구현)
5. 비교 화면 작성
6. 빌드 검증

## 검증되지 않은 것

- 모델별 도구 호출 지원은 템플릿 검사 기반 **추정**이다. `ollama show`로 확인해야 한다
- 로컬 모델이 구조화 출력·도구 호출에서 실제로 어떻게 거동하는지는 **돌려봐야 안다**. 이 문서의 예상은 결론이 아니며, 관측 결과는 `docs/phase2/study-log.md`에 쌓는다
- 2.0 레퍼런스의 도구 호출 스니펫 중 일부가 M7→M8 마일스톤 시절 API(`OpenAiChatClient.toolSpecifications`)로 되어 있어 GA 문법과 어긋난다. 근거로 쓰지 않는다
