# phase2 학습 로그 — 로컬 LLM 연동

본문보다 날것의 메모. **관측한 것만** 적고, 예상은 예상이라고 표시한다.

---

## 실측 — Ollama 모델 크기와 도구 호출 지원 (2026-09-04)

`registry.ollama.ai`의 매니페스트를 직접 조회한 결과다. Ollama는 모델을 Docker Distribution v2 형식으로 서빙하므로 설치 없이도 크기를 알 수 있다.

```bash
curl -s https://registry.ollama.ai/v2/library/qwen3/manifests/8b
```

| 모델 | 크기 | 도구 호출 |
| --- | --- | --- |
| qwen3:0.6b | 0.49 GiB | — |
| qwen3:1.7b | 1.27 GiB | — |
| qwen3:4b | 2.33 GiB | 지원 |
| qwen3:8b | 4.87 GiB | 지원 |
| qwen3:14b | 8.64 GiB | — |
| glm4:9b | 5.08 GiB | 미지원 |
| gemma3:4b | 3.11 GiB | 미지원 |
| granite3.3 | 4.60 GiB | 지원 |

도구 호출 여부는 매니페스트의 template 레이어를 받아 도구 정의가 있는지로 판정했다. **추정이다** — `ollama show`로 확인해야 한다.

`tags/list` 엔드포인트는 404다. Ollama 레지스트리는 OCI 배포 API를 전부 구현하지는 않는다.

## 관측 결과 (2026-09-04, M4 / RAM 32GB)

4개 축 × 4개 모델. 온도 0.1 고정, 재시도 없음.

| 모델 | chat | structured | tool | RAG |
| --- | --- | --- | --- | --- |
| qwen3:1.7b | 4.2s | 4.8s | **호출 1회** 7.1s | 6.8s |
| granite3.3 | 12.2s | 8.9s | **호출 1회** 11.5s | 10.0s |
| qwen3:8b | 15.9s | 12.5s | **호출 1회** 24.5s | 17.0s |
| glm4:9b | 6.0s | 9.4s | **미호출** 9.7s | 8.9s |

### 예상이 빗나갔다 — 작은 모델도 다 통과한다

세운 가설은 "작은 모델일수록 구조화 출력에서 스키마를 벗어난다"였다. **틀렸다.**

**1.7B 모델이 4개 축을 전부 통과했다.** `.entity(Movie)` 역직렬화가 한 번도 깨지지 않았고(4개 모델 전부), 도구 호출도 인자까지 정확했다. 크기를 키워서 얻은 것은 사실상 없고 **느려지기만 했다** — qwen3 계열에서 1.7b→8b 는 도구 축 기준 7.1s→24.5s 로 3.5배 느려졌는데 결과는 같다.

이 규모(1.7B~9B)와 이 난이도에서는 **모델 크기가 변별력이 없다.** 더 어려운 과제를 줘야 서열이 드러난다.

### glm4:9b — 도구를 안 부르지만 지어내지도 않는다

가장 흥미로운 결과다. 3개 케이스 모두 **도구 호출 0회**인데, 지어내지 않고 **정직하게 거절했다.**

> 죄송합니다만, 주문 A-1024에 대한 실시간 위치 정보를 제공할 수 있는 기능이 없습니다.

없는 주문번호(`Z-9999`)에도 같은 태도였다. 다른 세 모델은 **도구를 호출해서** "찾을 수 없습니다"를 받아 그대로 전달했다 — 겉보기 답은 비슷하지만 근거가 완전히 다르다.

**`ollama show` 는 glm4:9b 의 capabilities 를 `tools, completion` 으로 표기한다.** 지원한다고 적혀 있는데 실제로는 부르지 않는다. 앞서 레지스트리 템플릿 검사로 "미지원"이라 추정한 것은 **거동은 맞혔지만 근거는 틀렸다** — Ollama 는 지원한다고 본다.

교훈: **모델 카드의 capabilities 표기는 "부를 수 있다"이지 "부른다"가 아니다.** 도입 전에 실제 프롬프트로 확인해야 한다.

### 우리 데모의 문구가 틀렸다

카드 노트를 처음에 이렇게 적었다.

> 도구를 호출하지 않음 — 답이 그럴듯해도 근거 없이 지어낸 것이다

glm4 결과가 이 단정을 반증했다. 도구 미호출에는 **지어내기와 거절하기 두 갈래**가 있고, 후자는 오히려 바람직한 거동이다. 사실만 적고 판단은 사람에게 넘기도록 고쳤다.

### 진짜 약한 고리는 임베딩이었다

**4개 모델 전부 RAG 답이 맞았지만, 정답 문서는 매번 검색 순위 4위(꼴찌)였다.**

```
1) 디지털 상품은 다운로드 이력이 있으면…
2) 사내 장비 대여는 총무팀 승인 후…
3) 교환은 수령 후 7일 이내…
4) 환불은 구매 후 14일 이내에… 단순 변심이면 왕복 배송비 6,000원   ← 정답
```

질문이 "단순 변심으로 환불하면 배송비 얼마?"인데 정답 문서가 꼴찌다. 답이 맞은 것은 **검색이 잘돼서가 아니라 문서가 4건뿐이라 topK 안에 전부 들어왔기 때문**이다. 문서가 수십 건만 돼도 정답이 잘려 나가고, 그러면 모델은 조용히 틀린 답을 만든다.

채팅 모델과 무관하게 4회 모두 동일했다 — 임베딩은 `nomic-embed-text` 하나뿐이니 당연하다. **한국어 임베딩 품질이 이 구성의 병목이다.**

비교하려던 것은 채팅 모델이었는데, 정작 문제는 그 아래층에 있었다.

→ 이후 상담 봇을 만들다 실제 오답으로 터졌고, 원인을 끝까지 파서 고쳤다. 아래 "임베딩을 갈아치우기까지" 참고.

### 라우팅 워크플로 — 정확도는 전부 만점, 그런데 손해다

앞선 결과("1.7b 도 다 통과하는데 8b 는 3.5배 느리다")를 근거로 라우팅 축을 붙였다. 라우터가 질문을 order / policy / general 로 분류하고 필요한 경로만 태운다. 분류는 `.entity(RouteDecision)` 으로 받는다.

6개 질문(주문 2 · 규정 2 · 일반 2) × 4개 모델:

| 모델 | 분류 정확도 | 소요 |
| --- | --- | --- |
| qwen3:1.7b | 6/6 | 4.4~8.7s |
| glm4:9b | 6/6 | 5.2~13.9s |
| granite3.3 | 6/6 | 7.9~16.5s |
| qwen3:8b | 6/6 | 19.5~38.2s |

**전부 만점이다.** 1.7B 가 8B 와 똑같이 정확하면서 4~5배 빠르다 — "작은 모델을 라우터로" 가설은 실측으로 뒷받침된다.

**그런데 이 데모에서 라우팅은 오히려 느리다.**

| qwen3:1.7b | 라우팅 없이 | 라우팅 | 차이 |
| --- | --- | --- | --- |
| 규정 질문 | 6.8s | 9.3s | +2.5s |
| 일반 질문 | 4.2s | 8.5s | +4.3s |

분류가 LLM 호출 한 번을 통째로 더 쓰는데, 그렇게 해서 아끼는 것은 **문서 4건짜리 인메모리 유사도 검색**이다. 쓰는 쪽이 훨씬 크다.

라우팅이 이득이 되는 조건은 **건너뛰는 경로가 분류 비용보다 비쌀 때**다 — 문서 수만 건 벡터 검색, 외부 API 를 때리는 도구, 프롬프트에 도구 수십 개를 싣는 경우, 또는 큰 모델 호출. 이 데모는 셋 다 아니다.

교훈: 워크플로 패턴은 공짜가 아니다. **분류 비용과 절감액을 재보지 않고 얹으면 느려지기만 한다.** 레퍼런스의 패턴 예제는 "이렇게 조립한다"를 보여줄 뿐 "언제 이득인가"는 말해 주지 않는다.

부수 관측 — glm4:9b 는 order 경로로 **정확히 분류하고도 도구를 부르지 않았다.** 라우팅은 되는데 실행이 안 된다. 앞선 도구 축 결과와 일관된다.

### 그 밖에

- **granite3.3 은 한국어 질문에 영어로 답할 때가 있다.** RAG 축에서 재현됐다(문서는 한국어). 같은 모델이 chat 축에서는 한국어로 답했으니 컨텍스트가 길어질 때 언어 고정이 풀리는 것으로 보인다.
- **granite3.3 이 "1 더하기 1은?"에 빈 응답을 0.3초에 반환했다.** 도구를 준 상태에서만 관측됐다. 도구가 걸린 상태의 단순 질문에서 무언가 어긋난다.
- 요약표의 성공/실패는 **"예외 없이 응답했나"** 일 뿐이다. glm4 의 도구 축이 `OK` 로 찍히지만 도구는 안 불렀다. 축의 목적을 달성했는지는 노트를 봐야 한다.

## 사내망 반입 경로 타진 (2026-09-04)

시연 대상 노트북은 **git과 brew만** 열려 있다.

### GitHub으로 모델을 나를 수 있나 — 없다

- **GHCR을 Ollama 레지스트리로**: `registry.ollama.ai`가 Docker Distribution v2를 쓰고 GHCR 익명 토큰도 발급되지만, Ollama 클라이언트가 블롭 pull에서 `no Location header in response`로 실패한다. 레지스트리의 블롭 리다이렉트를 못 따라간다. 서드파티 OCI 레지스트리는 공식 미지원 (ollama/ollama#7244)
- **GitHub Releases**: 파일당 2 GiB 제한. qwen3:4b가 2.33 GiB로 이미 초과다. 총량·대역폭 제한이 없어 분할하면 우회되지만, 자산이 `release-assets.githubusercontent.com`으로 리다이렉트된다 — phase1에서 임베딩 ONNX가 막혔던 바로 그 호스트 계열이다
- **Git LFS**: 무료 한도 1GB 수준. 논외

```bash
curl -sIL https://github.com/ollama/ollama/releases/latest/download/ollama-darwin.tgz | grep -i location
# → https://release-assets.githubusercontent.com/...
```

### 그런데 brew가 열려 있다는 게 단서였다

Homebrew 병 저장소가 **ghcr.io**다.

```bash
curl -s https://formulae.brew.sh/api/formula/jq.json | jq -r .bottle.stable.root_url
# → https://ghcr.io/v2/homebrew/core
```

즉 brew가 되면 ghcr.io가 열려 있다. Ollama가 GHCR에서 직접 못 받는 것이지 GHCR 자체가 막힌 게 아니므로, **제대로 된 OCI 클라이언트를 중간에 끼우면 우회된다.** `ollama`와 `oras`가 둘 다 Homebrew 포뮬러로 있다.

절차는 [offline-kit.md](./offline-kit.md).

## 임베딩을 갈아치우기까지 — RAG 가 조용히 틀리는 과정

상담 봇을 만들자마자 첫 턴부터 이상했다.

```
나 ▶ A-1024 지금 어디쯤 왔어?
봇 ◀ 사내 장비 대여 관련 정보는 제공하지 않습니다…
     도구 0회 · 검색 4건
```

주문을 물었는데 **도구를 안 부르고 엉뚱한 소리**를 한다. 프롬프트 전문을 꺼내 보니 원인이 보였다 — `QuestionAnswerAdvisor` 가 질문과 무관한 사내 문서를 **무조건** 주입했고, 모델이 그 컨텍스트에 끌려갔다.

### 1차 시도 — 유사도 임계값. 실패했다

무관한 문서를 애초에 안 딸려오게 하면 된다고 봤다. 추가 LLM 호출이 없어 라우팅보다 싸다.

```kotlin
SearchRequest.builder().topK(3).similarityThreshold(0.5).build()
```

**안 걸러졌다.** 주문 질문에 여전히 3건이 붙었다. 게다가 `topK` 를 3으로 줄인 탓에 **규정 질문에서 정답 문서가 잘려 나가** 오답이 나왔다 — "환불 요금은 명시되지 않았습니다". 정답이 4위라는 걸 알면서 topK 를 줄인 게 자충수였다.

### 2차 — 점수를 찍어 봤다

추측을 멈추고 유사도 점수를 화면에 노출했다.

**"A-1024 지금 어디쯤 왔어?"**
```
0.676  사내 장비 대여는 총무팀 승인 후…
0.670  환불은 구매 후 14일 이내에…
0.657  디지털 상품은 다운로드 이력이…
```

**"단순 변심으로 환불하면 배송비 얼마 내야 해?"**
```
0.823  디지털 상품은 다운로드 이력이…      ← 1위
0.781  사내 장비 대여는 총무팀 승인 후…    ← 2위, 완전 무관
0.765  교환은 수령 후 7일 이내…
(잘림) 환불은… 단순 변심이면 왕복 배송비 6,000원…   ← 정답
```

질문에 **"단순 변심", "환불", "배송비" 가 글자 그대로 들어 있는 문서**가 "사내 장비 대여" 보다 낮다. 주문 질문에서는 무관한 문서 셋이 전부 0.66~0.68 로 높고 평평하다.

**점수가 안 벌어지니 임계값이 원리적으로 불가능했다.** 순위도 사실상 무작위다. `nomic-embed-text` 는 한국어에서 변별력이 없다.

### 3차 — 임베딩 교체. 설정 한 줄

```properties
spring.ai.ollama.embedding.options.model=bge-m3
```

> 당시 적은 그대로 둔다. 이 이름은 2.0 부터 deprecated 였고(대체: `spring.ai.ollama.embedding.model`),
> 2026-09-07 에 바꿨다 — 아래 "deprecated 프로퍼티를 걷어내다" 참고.

| | nomic-embed-text | bge-m3 |
| --- | --- | --- |
| 주문 질문 검색 건수 | 3건 (무관 문서 주입) | **0건** |
| 주문 질문 도구 호출 | 0회 — 오염되어 실패 | **1회 성공** |
| 규정 질문 정답 순위 | 4위 (잘림) | **1위** |
| 규정 질문 점수 분포 | 0.823 / 0.781 / 0.765 | **0.813 / 0.611 / 0.561** |

점수가 제대로 벌어지자 **임계값도 그제서야 작동했다.** 주문 질문에서는 모든 문서가 0.5 아래로 떨어져 아예 주입되지 않고, 그래서 모델이 컨텍스트에 끌려가지 않고 도구를 부른다. 두 버그가 한 줄로 같이 사라졌다.

### 남는 것

- **RAG 가 이상하면 LLM 부터 의심하지 마라.** 여기서 채팅 모델은 아무 잘못이 없었다. 네 모델 모두 같은 오염된 컨텍스트를 받고 비슷하게 헤맸다
- **유사도 점수를 안 보면 진단이 안 된다.** 검색된 문서 목록만 보면 "왜 이게 나왔지" 로 끝나는데, 점수를 보면 "평평하다 → 임베딩이 문제다" 로 넘어간다. 그래서 추적 패널에 점수를 상시 노출한다
- **한국어를 쓴다면 임베딩 모델을 반드시 따로 검증해라.** 영어 벤치마크 순위는 근거가 안 된다
- 임계값·topK 튜닝은 **점수가 벌어진 다음에** 의미가 있다. 안 벌어진 상태에서 만지면 정답만 잘려 나간다

## 밟은 것 — 데모를 만들며

### VectorStore 빈이 앱 기동을 볼모로 잡는다

`@Bean fun vectorStore(...)` 안에서 문서를 시드했더니 앱이 아예 안 떴다.

```
Error creating bean with name 'vectorStore' … Factory method 'vectorStore' threw exception
  with message: I/O error on POST "http://localhost:11434/api/embed": Connection refused
```

`add(documents)`가 곧 임베딩 호출이라 **Ollama 가 떠 있어야만 컨텍스트가 뜬다.** RAG 축 하나 때문에 나머지 세 축도 못 보게 된다.

빈 생성에서 첫 사용 시점으로 미뤘다. 앱은 항상 뜨고 RAG 실패는 그 카드에만 나타난다. 실패를 관측하는 게 목적인 데모에서는 **실패가 크래시가 아니라 결과여야 한다.**

화면이 모델 수만큼 병렬로 때리므로 시드는 이중 검사 락으로 감쌌다.

### 재시도 기본값이 화면을 몇 분간 멈춘다

Ollama 를 끄고 호출했더니 응답이 40초가 지나도 안 왔다. 예외는 로그에 찍히는데 HTTP 응답이 안 돌아온다.

`spring.ai.retry` 기본값 때문이다 — **max-attempts 10, 백오프 2초 → ×5 → 최대 3분.** 연결 거부처럼 즉시 실패하는 상황에서도 10번을 기다린다.

```properties
spring.ai.retry.max-attempts=1
```

껐더니 2.1초에 실패가 돌아온다. 비교 데모에서 재시도는 관측을 흐리기만 한다 — 알고 싶은 건 "첫 시도에 이 모델이 무엇을 했나"다.

운영이라면 반대다. 기본값이 10회인 데는 이유가 있다.

### Jackson 3 — `com.fasterxml.jackson.databind` 가 없다

```
e: Unresolved reference 'databind'
```

Boot 4 / Spring AI 2.0 이 Jackson 3 로 올라가면서 databind 가 `com.fasterxml.jackson` 아래에서 빠졌다. 1.x 코드를 그대로 옮기면 여기서 걸린다. 이 데모는 Jackson 을 직접 쓸 이유가 없어 의존 자체를 없앴다.

### 확인 못 한 속성은 넣지 않았다

`spring.ai.ollama.read-timeout` 으로 타임아웃을 늘리려 했으나 2.0 레퍼런스에서 확인되지 않는다. Spring Boot 는 모르는 속성을 조용히 무시하므로, 있으나 마나 한 줄이 설정에 남고 나중에 "설정했는데 왜 안 되지"가 된다. 확인된 것만 남겼다.

## 실측 — 21개 패턴 × 모델 (2026-09-06, M4 / RAM 32GB)

주제를 에이전틱 패턴으로 바꾼 뒤, 장마다 어떤 모델을 미리 골라 둘지 정하려고 전부 돌려 봤다.
같은 입력·같은 코드, 모델만 갈아 끼웠다. 토큰 상한은 160.

### qwen3:0.6b — 시연에 쓸 수 없다

가장 빠르지만 **thinking 에 토큰을 다 쓴다.** 상한 안에서 사고를 끝내지 못해 정작 답이 남지 않는다.

| 증상 | 장 |
| --- | --- |
| `.entity()` 파싱 실패 | 1 · 6 · 11 · 20 |
| 도구를 부르지 않음 | 2 · 5 |
| 답이 통째로 빈칸 | 14 · 17 · 18 · 19 |
| 채점기가 정답도 미달 처리 | 4 · 19 |

기본 모델을 `qwen3:4b-instruct`(non-thinking)로 잡아 둔 이유가 그대로 재현됐다.
**드롭다운에는 남겼다** — 작은 모델이 어디서 무너지는지 눌러서 보여 줄 수 있어서다.

### granite4:micro — 네 개 장에서 더 빠르고 결과가 같다

| 장 | qwen3:4b-instruct | granite4:micro |
| --- | --- | --- |
| 8장 대화 기억 | 7.7s | 8.0s |
| 12장 예외 처리 | 16.0s | 14.7s |
| 18장 가드레일 | 15.3s | **8.1s** |
| 19장 평가 | 29.8s | **11.7s** |

19장이 가장 크다. **정상 답 1.00 통과 / 엉뚱한 답 0.00 미달** 판정이 두 모델에서 똑같이 나오는데 시간은 2.5배 차이다.
채점기는 답을 만드는 모델보다 작아도 된다 — 회귀 테스트를 매번 돌릴 생각이라면 이 차이가 그대로 비용이다.

반대로 못 쓰는 곳도 분명하다.

- **6장** — 계획 단계를 영어로 쓴다
- **3장** — 배송 지연 항의 문장을 `중립` 으로 분류한다 (4b는 `부정`)
- **11장** — 자기가 세운 성공 기준을 자기가 미달로 판정한다
- **20장** — JSON 을 ``` 로 감싸서 내보낸다

### 토큰 상한을 250 → 160 으로 내린 효과

시연 중 대기가 길어 상한을 내렸다. 긴 답을 내던 장이 눈에 띄게 짧아진다.

| 장 | 250 | 160 |
| --- | --- | --- |
| 1장 프롬프트 체이닝 | 32.6s | 12.0s |
| 17장 추론 기법 | 53.8s | 31.3s |
| 19장 평가 | 42.4s | 29.8s |
| 12장 예외 처리 | 25.4s | 16.0s |

대신 **20장이 깨졌다.**

```
UnexpectedEndOfInputException: Unexpected end-of-input:
  was expecting closing quote for a string value
```

네 건의 작업에 각각 근거 문장을 붙여 JSON 으로 받는데, 160 토큰 안에 닫히지 않는다.
상한을 전역으로 되돌리는 대신 `PatternSpec.maxTokens` 를 두어 그 장만 500 으로 올렸다.

> **`maxTokens` 는 포터블 옵션이다.** Ollama 에서는 `num_predict` 로 매핑된다.
> 벤더 옵션(`OllamaChatOptions`)을 꺼내지 않고도 상한이 걸린다.

### 남는 이야기

모델이 약해지면 **가장 먼저 깨지는 것은 구조화 출력과 도구 호출**이다. 문장 생성은 마지막까지 버틴다.
`.entity()` 와 `@Tool` 은 프레임워크가 프롬프트·파싱·왕복을 대신해 주지만, **모델이 형식을 못 맞추는 것까지 대신해 주지는 않는다.**

## 설계에서 바꾼 것

작업하며 [설계 문서](../superpowers/specs/2026-09-04-phase2-local-llm-design.md)와 달라진 부분.

| 항목 | 설계 | 실제 | 이유 |
| --- | --- | --- | --- |
| 임베딩 | 로컬 ONNX(transformers) | Ollama (`nomic-embed-text`) | 반입 채널이 하나로 통일된다. ONNX 는 기본 URI 가 githubusercontent 라 phase1 에서 사내망에 막혔다 |
| 대화 메모리 | H2 유지 | 제거 | 어떤 시험 축도 쓰지 않는다. 다중 턴 축을 넣게 되면 되살린다 |
| 모델 옵션 | `OllamaChatOptions` | 포터블 `ChatOptions` | 벤더 중립이라 OpenAI 호환 사내 서버로 바꿔도 코드가 그대로다. 이식성이 논점인 데모에 더 맞다 |

## Spring AI 2.0.1 로 올리고 다시 재기 (2026-09-07)

`springAi = "2.0.0"` → `"2.0.1"` 한 줄만 바꾸고 21장을 전부 다시 돌렸다.

**실패 0건. 장별 호출 횟수도 그대로다** — 5·13장 2회(도구 왕복), 10·15장 0회(MCP 는 모델을 안 부른다), 21장 6회(후보 3 생성 + 3 채점). RAG 유사도도 `0.813 / 0.611 / 0.561` 로 동일하다.

### 새로 생긴 천장

2.0.1 에 도구 호출 상한이 들어왔다. 아티팩트에서 직접 확인한 값이다.

```
spring-ai-model-2.0.1.jar
  org/springframework/ai/model/tool/ToolCallLimits.class
  org/springframework/ai/model/tool/ToolCallLimitBehavior.class      THROW · RETURN_ERROR_RESPONSE
  org/springframework/ai/model/tool/ToolCallLimitExceededException.class

spring-ai-autoconfigure-model-tool-2.0.1.jar
  ToolCallingProperties$Limits 생성자 →  bipush 40  /  sipush 150
```

`spring-configuration-metadata.json` 쪽에는 숫자 기본값이 안 실려 있어(`defaultValue=null`) 문서 사이트의 40·150 을 그대로 믿을 수 없었다. **필드 초기화 바이트코드를 봐야 확인된다.**

`on-limit-exceeded` 는 `throw`, `resolution.fallback.enabled` 는 `false` — 이 둘은 메타데이터에 기본값이 실려 있다.

### BOM 이 164 → 169 개

2.0.1 에서 다섯 개가 들어왔다.

```
spring-ai-autoconfigure-model-chat-memory-repository-redis
spring-ai-autoconfigure-tool-search-advisor
spring-ai-google-genai-image
spring-ai-starter-model-google-genai-image
spring-ai-starter-tool-search-advisor
```

phase1 에서 "ToolSearch 는 starter 가 BOM 에 없어 버전을 직접 적어야 한다"고 적어 둔 제약이 여기서 풀렸다. 다만 자동 구성이 내놓는 것은 공유 `ToolCallingAdvisor.Builder` 빈이라, **켜면 전역이라는 지적은 그대로다.**

`agent` 가 이름에 들어간 아티팩트는 2.0.1 에서도 **0 개**다. phase1 에서 확인한 사실이 유지된다.

## deprecated 프로퍼티를 걷어내다 (2026-09-07)

2.0.1 로 올리며 코드와 문서 전체를 deprecated 기준으로 훑었다. **컴파일 경고는 양쪽 데모 모두 0건**이었지만, 프로퍼티에서 셋이 나왔다.

| 쓰던 이름 | 대체 |
| --- | --- |
| `spring.ai.ollama.chat.options.model` | `spring.ai.ollama.chat.model` |
| `spring.ai.ollama.chat.options.temperature` | `spring.ai.ollama.chat.temperature` |
| `spring.ai.ollama.embedding.options.model` | `spring.ai.ollama.embedding.model` |

**2.0.1 에서 새로 deprecated 된 것이 아니다.** 2.0.0 의 `spring-ai-autoconfigure-model-ollama` 메타데이터에도 이미 deprecation 이 붙어 있었다(2.0.0 은 37 개, 2.0.1 은 57 개). 처음부터 옛 이름을 쓰고 있었고, 발표 자료 14장도 그 이름을 가르치고 있었다.

**왜 안 보였나** — Spring Boot 는 모르는 프로퍼티를 조용히 무시하고, deprecated 프로퍼티도 기동 로그에 경고를 남기지 않는다. 동작에 지장이 없으니 드러날 계기가 없었다. `spring-configuration-metadata.json` 의 `deprecation` 필드를 직접 읽어야 나온다.

```
jar → META-INF/spring-configuration-metadata.json → properties[].deprecation.replacement
```

**검증** — 임베딩이 카나리아였다. 이름을 바꾸고 14장을 다시 돌려 유사도가 `0.813 / 0.611 / 0.561` 로 **소수점까지 같았다.** 새 이름이 실제로 바인딩된다는 증거다. 이어서 21장 전수를 브라우저로 다시 돌려 실패 0 건을 확인했다.

**타입·메서드 쪽** — `javap -v` 로 2.0.1 jar 33 개를 훑어 `Deprecated: true` 를 색인하고 문서·코드가 언급한 이름과 대조했다. 걸린 것은 `ChatClient.Builder.defaultToolCallbacks(...)` 와 `ChatClientRequestSpec.toolCallbacks(...)` 인데, **우리는 `.tools(...)` 만 쓰고 문서도 그것만 가르친다.** `TokenTextSplitter` · `ChatModel` 도 처음엔 걸렸으나 `Deprecated: true` 가 0 개인 오탐이었다 — LocalVariableTable 을 시그니처로 잘못 읽은 것이었다.
