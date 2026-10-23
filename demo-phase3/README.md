# Koog: Spring AI 위에 얹는 에이전트 프레임워크

JetBrains의 Kotlin 에이전트 프레임워크 Koog 1.3.0을 공식 Spring AI 브리지로 기존 Spring AI 스택 위에 얹은 데모입니다. 일정과 회사 말투를 아는 작은 비서를 여섯 단계로 만들면서 Koog가 내세우는 그래프 워크플로, 퍼시스턴스, 관측을 직접 돌려 봅니다.

**Spring Boot 3.5.14 + Spring AI 1.1.6 + Koog 1.3.0 + 로컬 Ollama. API 키가 필요 없습니다.** Spring AI 버전은 브리지(koog-spring-ai-starter 1.3.0-beta)가 고정한 값입니다.

문서는 [Koog.md](./docs/Koog.md)(청중용 본문), [발표 스크립트](./docs/발표%20스크립트.md), [데모 시나리오](./docs/데모%20시나리오.md). 결정 근거는 [docs/phase3/decisions.md](../docs/phase3/decisions.md).

## 준비

```bash
brew install ollama
brew services start ollama
ollama pull qwen3:4b-instruct   # 2.5 GB. 모델은 이것 하나
```

## 실행

프로필 둘이고 둘 다 터미널 REPL입니다. 웹 서버는 띄우지 않습니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=step1'     # 1/6 기본 에이전트: AIAgent 하나와 입력 루프
./gradlew bootRun --args='--spring.profiles.active=harness'   # 2/6부터 끝까지: 라우터 + 비서 + 스킬 에이전트
```

`❯`가 뜨면 입력하고, `exit`로 끝냅니다. 단계별 입력과 실측 답은 [데모 시나리오](./docs/데모%20시나리오.md)에 있습니다.

발표 때는 bootJar를 만들어 `java -jar`로 띄우는 짧은 명령을 PATH에 두고 씁니다. JDK 24 이상이면 JLine의 네이티브 로딩 경고를 끄는 옵션이 필요합니다.

```bash
#!/bin/sh
#   koog          하네스 (harness 프로필)
#   koog step1    기본 에이전트 (step1 프로필)
cd /path/to/spring-ai-study/demo-phase3 || exit 1
./gradlew bootJar -q || exit 1
exec java --enable-native-access=ALL-UNNAMED -jar build/libs/spring-ai-demo-phase3-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active="${1:-harness}"
```

## 무엇을 보여주는가

| 단계 | 더하는 것 | 확인하는 것 |
|---|---|---|
| 1/6 기본 에이전트 | `AIAgent` 하나와 입력 루프 | REPL 응답 |
| 2/6 도구 | `@Tool` 캘린더 함수 | 도구 호출 왕복. 함수 시그니처가 곧 스키마 |
| 3/6 스킬 | SKILL.md와 파일 읽기 도구 | 요청이 스킬에 해당할 때만 파일을 읽고 따름 |
| 4/6 오케스트레이션 | 조건 엣지 두 줄의 전략 그래프 | 판교 사투리는 스킬 에이전트로, 나머지는 비서로 |
| 5/6 챗 메모리 | `ChatMemory` 기능과 세션 ID | 앞 일정을 도구 호출 없이 답함 |
| 6/6 퍼시스턴스 | `Persistence` 기능, 검증 노드, 반대 도구 등록 | 강제 종료 뒤 재시작하면 중단 지점부터 완료. 겹치는 일정은 도구 실행 전으로 롤백하고 remove가 자동 호출 |

답 위에 찍히는 들여쓴 줄은 Koog `EventHandler`로 남기는 실행 기록입니다. 어느 에이전트가 모델을 몇 번 불렀고 어떤 도구를 어떤 인자로 불렀는지가 보입니다.

## 코드

| 파일 | 역할 |
|---|---|
| `Harness07.kt` | step1 프로필. AIAgent 하나와 readLine 루프 |
| `Harness12.kt` | harness 프로필. 캘린더·체크포인트 경로, 기동 때 중단된 실행 재개, 입력 루프 시작 |
| `DemoAgents.kt` | 라우터, 비서, 스킬 에이전트 생성. 스킬 카탈로그 준비, 직전 답변 붙이기 |
| `DispatchRouter.kt` | 요청을 스킬 에이전트와 비서로 가르는 전략 그래프 |
| `AssistantStrategy.kt` | 비서 전략. receive, request, execute, verify, report. 겹치면 receive 뒤 체크포인트로 롤백 |
| `CalendarToolSet.kt` | 캘린더 도구 schedules, add, remove. 일정은 텍스트 파일에 저장 |
| `Repl.kt` | JLine 입력 루프 |
| `Trace.kt` | 실행 기록 출력 |
| `KoogModels.kt` | Ollama 모델 정의 |
| `src/main/resources/skills/pangyo-dialect/SKILL.md` | 판교 사투리 스킬 |
| `src/main/resources/static/slides.html` | 슬라이드 15장. file://로 연다 |

## 실행 중 생기는 파일

`data/` 아래에 생기고 git이 무시합니다. 발표나 리허설 전에는 비웁니다.

| 파일 | 내용 |
|---|---|
| `data/koog-checkpoints/checkpoints/demo/` | 노드마다 남는 체크포인트 JSON과 완료 표시. 완료 표시 없이 남아 있으면 다음 기동 때 자동 재개 |
| `data/koog-calendar.txt` | 캘린더 일정. 한 줄에 "날짜<탭>시각 제목" |

```bash
rm -rf data
```

## 테스트

```bash
./gradlew test   # 18건. Ollama와 qwen3:4b-instruct가 떠 있어야 한다
```

모델 응답은 글자 단위로 흔들릴 수 있어서, 단정은 도구 호출 기록과 체크포인트 파일처럼 결정적인 것으로 합니다.
