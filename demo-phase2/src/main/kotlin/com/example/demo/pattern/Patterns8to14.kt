package com.example.demo.pattern

import com.example.demo.support.OrderService
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.document.Document
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.chat.evaluation.RelevancyEvaluator
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.stereotype.Component

@Component
class MemoryManagement : PatternRunner {
    override val spec = PatternSpec(
        8, "메모리 관리", "Memory Management",
        parts = "`ChatMemory` · `MessageChatMemoryAdvisor` · `ChatMemoryRepository`",
        summary = "LLM은 호출마다 아무것도 기억하지 못합니다. 맥락을 유지하려면 지난 메시지를 다시 넣어 줘야 합니다.",
        code = """
            val chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build()

            chatClient.prompt().user("방금 뭐라고 했지?")
                // 대화 ID는 호출 시점에 넘긴다. 빠뜨리면 예외가 난다
                .advisors { it.param(ChatMemory.CONVERSATION_ID, "user-42") }
                .call().content()
        """.trimIndent(),
        defaultInput = "내 이름은 준이야",
        fromDemo = true,
        model = "granite4:micro",
        modelWhy = "대화 이력을 다시 실어 보내기만 하면 되는 장이라 작은 쪽이 낫습니다",
    )

    override fun run(ctx: RunContext): RunResult {
        val memory: ChatMemory = MessageWindowChatMemory.builder().maxMessages(20).build()
        val advisor = MessageChatMemoryAdvisor.builder(memory).build()
        val id = "demo-" + System.nanoTime()

        val first = ctx.step("① 첫 턴") {
            it.user(ctx.input).advisors(advisor).advisors { a -> a.param(ChatMemory.CONVERSATION_ID, id) }
                .call().content()
        }
        val second = ctx.step("② 다음 턴 — 기억하나") {
            it.user("방금 내가 뭐라고 했지?").advisors(advisor).advisors { a -> a.param(ChatMemory.CONVERSATION_ID, id) }
                .call().content()
        }
        ctx.note("두 번째 호출의 프롬프트를 펼쳐 보면 첫 턴이 그대로 실려 있습니다")
        return ctx.result("1턴 — $first\n\n2턴 — $second")
    }
}

@Component
class LearningAndAdaptation(private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        9, "학습과 적응", "Learning and Adaptation",
        parts = "`VectorStore` · `Document` · `QuestionAnswerAdvisor`",
        summary = "설계 때 예상 못 한 상황에서는 짜 둔 로직이 안 통합니다. 실제 사용에서 얻은 피드백을 반영합니다.",
        code = """
            // 사람이 고쳐 준 좋은 답을 저장해 두고
            vectorStore.add(listOf(Document("질문: ${'$'}질문\n모범 답: ${'$'}고친답")))

            // 다음 질문에는 비슷한 사례가 예시로 딸려 들어간다
            chatClient.prompt().user(새질문)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .call().content()
        """.trimIndent(),
        defaultInput = "환불 배송비 얼마야?",
        model = "qwen3:4b-instruct",
        modelWhy = "사례 전후 차이가 또렷하게 갈립니다",
    )

    override fun run(ctx: RunContext): RunResult {
        val before = ctx.step("① 사례를 쌓기 전") { it.user("${ctx.input} 한 문장으로만 답해라.").call().content() }

        docs.lessons.add(
            listOf(
                Document("질문: 환불 배송비 얼마야?\n모범 답: 단순 변심이면 왕복 배송비 6,000원을 구매자가 부담합니다. 규정 문서를 근거로 답하고 추측하지 않습니다."),
            ),
        )
        ctx.note("사람이 고쳐 준 답 1건을 VectorStore 에 저장했습니다")

        val after = ctx.step("② 사례를 쌓은 뒤") {
            it.user("${ctx.input} 한 문장으로만 답해라.").advisors(QuestionAnswerAdvisor.builder(docs.lessons).build()).call().content()
        }
        ctx.note("모델을 다시 학습시킨 것이 아니라 프롬프트에 예시를 넣은 것입니다")
        return ctx.result("전 — $before\n\n후 — $after")
    }
}

@Component
class GoalSetting : PatternRunner {
    override val spec = PatternSpec(
        11, "목표 설정과 모니터링", "Goal Setting and Monitoring",
        parts = "`.entity()` · `Evaluator`",
        summary = "목표가 없으면 에이전트는 시키는 것에만 반응합니다. 목표를 명시하고 달성 여부를 확인합니다.",
        code = """
            data class Goal(val description: String, val successCriteria: String)

            val goal = chatClient.prompt().system(GOAL_SETTER).user(요청)
                .call().entity(Goal::class.java)

            val done = evaluator
                .evaluate(EvaluationRequest(goal.successCriteria, 작업결과))
                .isPass
        """.trimIndent(),
        defaultInput = "고객에게 환불 절차를 안내한다",
        model = "qwen3:4b-instruct",
        modelWhy = "granite4:micro 는 자기가 세운 기준을 자기가 미달로 판정했습니다",
    )

    override fun run(ctx: RunContext): RunResult {
        val goal = ctx.step("① 목표 정의") {
            it.system("요청을 목표로 정리한다. successCriteria 에는 달성 여부를 판정할 기준을 한 문장으로 적는다.")
                .user(ctx.input).call().entity(Goal::class.java)
        } ?: return ctx.result("목표를 타입으로 받지 못했습니다", ok = false)

        ctx.note("성공 기준 — ${goal.successCriteria}")

        val work = ctx.step("② 작업 수행") { it.user("${goal.description} 두 문장 이내로 답해라.").call().content() }

        val verdict = runCatching {
            ctx.external("③ 달성 판정 (Evaluator)") { b ->
                RelevancyEvaluator.builder().chatClientBuilder(b).build()
                    .evaluate(EvaluationRequest(goal.successCriteria, work.orEmpty()))
            }
        }.getOrNull()
        ctx.note(
            if (verdict == null) "채점기 호출 실패"
            else "달성 판정 — ${verdict.verdict()}",
        )
        return ctx.result(work)
    }
}

@Component
class ExceptionHandling : PatternRunner {
    override val spec = PatternSpec(
        12, "예외 처리와 복구", "Exception Handling and Recovery",
        parts = "`spring.ai.retry` 프로퍼티",
        summary = "실제 환경에서는 도구가 실패하고 네트워크가 끊깁니다. 감지해서 복구 경로로 넘깁니다.",
        code = """
            # 기본값은 10회에 2초 → 50초 백오프. 모델이 없으면 화면이 몇 분 멈춘다
            spring.ai.retry.max-attempts=1

            val answer = runCatching {
                chatClient.prompt().user(질문).call().content()
            }.getOrElse {
                "지금 답변이 어렵습니다. 상담원에게 연결해 드릴까요?"   // 폴백
            }
        """.trimIndent(),
        defaultInput = "환불 규정 알려줘",
        model = "granite4:micro",
        modelWhy = "1차 실패 → 폴백이 도는 것만 보면 되는 장입니다. 답은 짧을수록 좋습니다",
    )

    override fun run(ctx: RunContext): RunResult {
        // 없는 모델을 불러 실제로 실패시킨다
        val failed = runCatching {
            ctx.chatClient.prompt().user(ctx.input)
                .options(ChatOptions.builder().model("존재하지-않는-모델")).call().content()
        }
        ctx.note(
            failed.exceptionOrNull()
                ?.let { "1차 시도 실패 — ${it::class.simpleName}: ${it.message?.take(90)}" }
                ?: "실패하지 않았습니다",
        )
        ctx.note("재시도를 1회로 낮춰 두어 2초 안에 실패가 돌아옵니다. 기본값 10회면 몇 분을 기다립니다")

        val answer = ctx.step("폴백 경로") { it.user(ctx.input).call().content() }
        return ctx.result(answer)
    }
}

@Component
class HumanInTheLoop(private val orders: OrderService) : PatternRunner {
    override val spec = PatternSpec(
        13, "사람 개입", "Human-in-the-Loop",
        parts = "`@Tool` · 그리고 LLM이 없는 평범한 `@PostMapping`",
        summary = "실수의 대가가 큰 일을 모델에게 맡기는 것은 위험합니다. 결정적인 지점에 사람의 판단을 끼웁니다.",
        code = """
            // description 에 "실제로 취소하지 않는다"고 적어 둔다. 모델이 읽는 계약이다
            @Tool(description = "주문 취소를 요청한다. 실제로 취소하지 않고 고객 승인을 받는 단계다.")
            fun requestCancellation(orderId: String): String {
                pendingCancel = orders.find(orderId)!!.id      // 요청만 기록. 상태는 그대로
                return "주문 ${'$'}pendingCancel 은 취소 가능합니다. 승인 버튼을 눌러야 실제로 취소됩니다."
            }

            // 평범한 컨트롤러. ChatClient 도 Advisor 도 등장하지 않는다
            @PostMapping("/approve")
            fun approve(@RequestBody req: ApproveRequest) = orders.cancel(req.orderId)
        """.trimIndent(),
        defaultInput = "A-1024 취소해줘",
        fromDemo = true,
        model = "qwen3:4b-instruct",
        modelWhy = "취소 요청 도구를 정확히 한 번만 부릅니다",
    )

    override fun run(ctx: RunContext): RunResult {
        val before = orders.find("A-1024")?.status
        val tools = SupportTools(orders)
        val answer = ctx.step("도구 왕복") { it.user(ctx.input).tools(tools).call().content() }
        val after = orders.find("A-1024")?.status

        ctx.note("도구 ${tools.calls.size}회 — ${tools.calls.joinToString().ifBlank { "없음" }}")
        ctx.note("주문 상태 — 호출 전 `$before` · 호출 후 `$after` (바뀌지 않았습니다)")
        tools.pendingCancel?.let { ctx.note("승인 대기 — $it. 화면의 [승인] 버튼이 눌려야 실제로 바뀝니다") }
        return ctx.result(answer)
    }
}

@Component
class KnowledgeRetrieval(private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        14, "지식 검색", "Knowledge Retrieval (RAG)",
        parts = "`QuestionAnswerAdvisor` · `VectorStore` · `SearchRequest`",
        summary = "모델은 학습 데이터 밖의 것을 모릅니다. 답하기 전에 관련 문서를 찾아 프롬프트에 넣어 근거를 만듭니다.",
        code = """
            .advisors(
                QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(
                        SearchRequest.builder()
                            .topK(3)                   // 상위 몇 건까지
                            .similarityThreshold(0.5)  // 이 점수 미만이면 아예 주입하지 않는다
                            .build(),
                    ).build(),
            )
        """.trimIndent(),
        defaultInput = "단순 변심으로 환불하면 배송비 얼마 내야 해?",
        fromDemo = true,
        model = "qwen3:4b-instruct",
        modelWhy = "검색해 온 규정을 그대로 인용해야 합니다",
    )

    override fun run(ctx: RunContext): RunResult {
        val search = SearchRequest.builder().topK(3).similarityThreshold(0.5).build()
        val answer = ctx.step("검색 + 답") {
            it.user(ctx.input)
                .advisors(QuestionAnswerAdvisor.builder(docs.store).searchRequest(search).build())
                .call().content()
        }
        val found = docs.store.similaritySearch(SearchRequest.from(search).query(ctx.input).build()).orEmpty()
        if (found.isEmpty()) ctx.note("검색 0건 — 임계값 0.5 를 넘는 문서가 없어 아무것도 주입되지 않았습니다")
        found.forEach { ctx.note("%.3f · %s".format(it.score ?: 0.0, it.text.orEmpty().take(50))) }
        return ctx.result(answer)
    }
}
