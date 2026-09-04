# 오프라인 반입 키트

외부망이 막힌 사내망 노트북에서 이 데모를 돌리기 위한 절차.

전제 — 시연 노트북은 **git과 brew만** 열려 있다. `registry.ollama.ai`는 막혀 있다고 가정한다.
근거와 타진 과정은 [study-log.md](./study-log.md#사내망-반입-경로-타진-2026-09-04).

---

## 0. 먼저 확인 — 정말 막혀 있나

노트북에서 실행한다. `000`이나 에러 문구면 차단, 숫자가 나오면 도달이다(401·404도 "열려 있음"이다).

```bash
for h in ghcr.io registry.ollama.ai github.com \
         release-assets.githubusercontent.com huggingface.co formulae.brew.sh; do
  printf '%-42s %s\n' "$h" \
    "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 8 "https://$h" 2>&1 | tail -1)"
done
```

**`registry.ollama.ai`가 열려 있으면 이 문서는 필요 없다.** `ollama pull`로 끝난다.

## 1. 설치 (사내 노트북)

brew 채널만 사용한다.

```bash
brew install ollama oras
```

## 2. 모델 반입

### 경로 A — oras + GHCR (권장)

**외부망에서 (1회)**

GGUF를 확보해 공개 GHCR 패키지로 올린다.

```bash
# 예: 이미 ollama가 있는 머신이라면 blobs에서 모델 레이어를 꺼낸다
#     (매니페스트의 application/vnd.ollama.image.model 레이어)
oras push ghcr.io/<계정>/spring-ai-models:qwen3-8b ./qwen3-8b.gguf
```

패키지를 **public**으로 바꾼다. 그래야 사내에서 인증 없이 받을 수 있다.

**사내 노트북에서**

```bash
oras pull ghcr.io/<계정>/spring-ai-models:qwen3-8b

cat > Modelfile <<'EOF'
FROM ./qwen3-8b.gguf
EOF

ollama create qwen3-8b -f Modelfile
ollama list
```

프록시를 타야 하면:

```bash
export HTTPS_PROXY=<사내 프록시>
export HTTP_PROXY=<사내 프록시>
```

### 경로 B — 모델 디렉토리 통째 반입 (USB 가능할 때)

외부망 머신에서 `ollama pull`을 끝내고 저장 디렉토리를 복사한다.

| OS | 위치 |
| --- | --- |
| macOS | `~/.ollama/models` |
| Linux | `/usr/share/ollama/.ollama/models` |
| Windows | `C:\Users\%username%\.ollama\models` |

`OLLAMA_MODELS` 환경변수로 위치를 바꿀 수 있어, 외장 디스크를 그대로 가리켜도 된다.

```bash
export OLLAMA_MODELS=/Volumes/<외장>/ollama-models
ollama list
```

## 3. 임베딩 모델도 반입 목록에 넣는다

RAG 는 **`bge-m3`** 를 쓴다. 채팅 모델과 같은 Ollama 채널이라 반입 방법이 동일하다 — 위 경로 A/B 로 함께 나르면 된다.

```bash
ollama pull bge-m3      # 외부망에서 미리
```

phase1 은 로컬 ONNX(`all-MiniLM-L6-v2`)를 썼는데 기본 URI 가 githubusercontent 라 사내망에서 첫 기동에 깨졌다. phase2 가 임베딩을 Ollama 로 옮긴 이유가 이것이다 — **반입 채널이 하나로 통일된다.**

`bge-m3` 를 쓰는 이유는 사내망과 무관하다. `nomic-embed-text` 는 한국어 변별력이 없어 RAG 가 조용히 틀린다([study-log](./study-log.md#임베딩을-갈아치우기까지--rag-가-조용히-틀리는-과정)). **다른 임베딩으로 바꿀 거라면 한국어 문서로 유사도 점수를 먼저 재 보라.**

## 4. 오프라인 완주 검증

**시연 당일에 발견하면 늦다.** 반입이 끝나면 네트워크를 끊고 처음부터 완주한다.

```bash
# Wi-Fi를 끄고
ollama list                      # 모델이 다 보이는가
ollama run qwen3-8b "안녕"        # 응답이 오는가
cd demo-phase2 && ./gradlew bootRun  # 기동되는가 (Gradle 의존성도 미리 받아둘 것)
```

브라우저에서 상담 봇으로 한 바퀴 돌려 본다(조회 → 규정 → 취소 → 승인). **규정 질문이 특히 중요하다** — 임베딩 초기화는 첫 요청에 일어나므로 기동 성공만으로는 검증되지 않는다.

### Gradle 의존성도 반입 대상이다

`./gradlew build`를 외부망에서 한 번 돌려 `~/.gradle/caches`를 채워둔다. 사내망에서 Maven Central이 막혀 있으면 빌드 자체가 안 된다.

## 미검증

- **수 GB 레이어의 GHCR 푸시/풀** — Homebrew 병은 수십 MB급이라 이 경로가 대용량에서도 도는지는 실증하지 않았다. 가장 작은 모델로 먼저 뚫어볼 것
- 사내 프록시 환경에서 `oras`가 실제로 통과하는지

## 대안 — 사내 LLM 서비스가 있다면

사내에 이미 제공되는 Qwen 등이 있고 OpenAI 호환이라면, **모델 반입 자체가 불필요하다.**

```bash
curl -s <엔드포인트>/v1/models -H "Authorization: Bearer <키>"
```

모델 목록이 나오면 설정 세 줄로 끝난다.

```properties
spring.ai.openai.base-url=<엔드포인트>
spring.ai.openai.api-key=${INTERNAL_LLM_KEY}
spring.ai.openai.chat.options.model=<모델명>
```
