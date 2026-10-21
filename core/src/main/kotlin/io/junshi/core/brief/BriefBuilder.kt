package io.junshi.core.brief

import io.junshi.core.kb.Citation
import io.junshi.core.kb.KnowledgeBase

/**
 * Compiles the five steps of the strategist into one [StrategyBrief].
 *
 * The order is the contract, not a suggestion: emotion first, then facts, then
 * interests, then a recommendation, then one small action. A high safety flag
 * short-circuits steps 3–5 — no amount of strategy outranks someone's safety.
 */
class BriefBuilder(private val kb: KnowledgeBase) {

    fun build(input: BriefInput, knowledgeBudget: Int = 1100): StrategyBrief {
        val emotion = EmotionAnalyzer.read(input)
        val evidence = EvidenceAnalyzer.split(input)
        val interest = ReciprocityAnalyzer.read(input)
        val safety = SafetyAnalyzer.scan(input)

        val query = buildString {
            append(input.turns.takeLast(10).joinToString("\n") { it.text })
            append('\n')
            append(input.notes)
            append('\n')
            append(input.goal.label)
            input.partner.relationshipNote.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
        }
        val routes = kb.route(query, 2)

        // The goal and a safety flag steer which files open, even when no keyword
        // matched: a repair question must read the conflict guide, and a threat
        // must read the safety chapter before anything about relationships.
        val hint = when {
            safety.isNotEmpty() -> "crisis"
            input.goal == Goal.REPAIR -> "conflict"
            input.goal == Goal.EXIT -> "breakup"
            input.goal == Goal.COMPARE -> "imbalance"
            else -> routes.firstOrNull()?.route?.id
        }
        val effectiveQuery = if (safety.isNotEmpty()) {
            query + " 人身安全 法律 报警 家暴 威胁 边界 危机"
        } else {
            query
        }
        val packed = kb.pack(effectiveQuery, knowledgeBudget, sceneHint = hint, maxChunks = 4)
        val routeIds = (listOfNotNull(hint) + routes.map { it.route.id }).distinct().take(3)
        val opened = kb.routes.routes.firstOrNull { it.id == hint }
        // One citation per document: three different files read better than three
        // sections of the same file, and the panel has room for three lines.
        val citations = buildList {
            packed.hits.forEach { add(Citation(it.doc, it.chunk.heading, it.doc.strength)) }
            opened?.docs?.forEach { id -> kb.doc(id)?.let { add(Citation(it, "", it.strength)) } }
            routes.forEach { r -> r.route.docs.forEach { id -> kb.doc(id)?.let { add(Citation(it, "", it.strength)) } } }
        }.distinctBy { it.doc.id }.take(3)

        if (safety.isNotEmpty()) {
            return StrategyBrief(
                emotion = emotion,
                evidence = evidence,
                interest = interest,
                recommendation = Recommendation(
                    headline = "这一轮先不聊策略：把安全放在第一位",
                    reasons = safety.map { it.guidance },
                    steady = "先确认当下安全，再联系可信支持或当地紧急服务。",
                    pressure = "safety",
                ),
                action = ActionPlan(
                    now = "确认自己在安全的地方，告诉一个可信任的人你现在的情况",
                    window = "今天之内",
                    stopCondition = "任何让你感到危险的对话立即停止",
                    comebackSignal = "等你安全、并且有支持的人在身边时再回来讨论关系",
                ),
                citations = citations,
                questions = listOf("你现在是否安全？身边有没有可以立刻联系的人？"),
                safety = safety,
                routes = routeIds,
            )
        }

        val rec = recommend(input, emotion, evidence, interest)
        val action = plan(input, emotion, interest, rec)
        val questions = questions(input, evidence)
        return StrategyBrief(
            emotion = emotion,
            evidence = evidence,
            interest = interest,
            recommendation = rec,
            action = action,
            citations = citations,
            questions = questions,
            safety = safety,
            routes = routeIds,
        )
    }

    private fun recommend(
        input: BriefInput,
        emotion: EmotionRead,
        evidence: EvidenceTriad,
        interest: InterestRead,
    ): Recommendation {
        val lastOther = input.turns.lastOrNull { it.speaker == Speaker.OTHER }?.text.orEmpty()
        val passive = lastOther.trim().let { it.length <= 2 || it in setOf("嗯", "哦", "好", "行", "ok", "OK", "嗯嗯") }
        val reasons = ArrayList<String>()
        reasons.add("对方的情绪读数是「${emotion.feeling}」，强度 ${emotion.intensity}/10；这一轮先处理关系温度，不是先讲道理。")
        reasons.add("可见投入比 ${(interest.reciprocity * 100).toInt()}%：${interest.signals.firstOrNull() ?: "样本有限"}。")
        evidence.facts.firstOrNull()?.let { reasons.add("原文能证明的是：${it}。") }

        return when (input.goal) {
            Goal.ADVANCE -> {
                if (interest.reciprocity >= 0.6 && input.stage in setOf(Stage.TALKING, Stage.DATING, Stage.ESTABLISHED)) {
                    reasons.add("互惠证据足够，可以给一个具体邀约，而不是继续闲聊。")
                    Recommendation(
                        headline = "给一个具体、可拒绝的邀约（时间 + 场景），只发这一次",
                        reasons = reasons,
                        steady = "“${weekdayHint()}晚上有空吗？想去${placeHint()}，你要是忙就改天。”",
                        playful = "“发现一家${placeHint()}，感觉你会喜欢。${weekdayHint()}去试试？”",
                        firm = "“我想见你。${weekdayHint()}或周末，你选一个。”",
                        pressure = if (passive) "low" else "normal",
                    )
                } else {
                    reasons.add("互惠证据还不足以支撑直接加码，先做一次低强度试探。")
                    Recommendation(
                        headline = "先做一次低强度试探：给一个可拒绝的小邀请，然后停下",
                        reasons = reasons,
                        steady = "“这周有件小事想跟你一起，${weekdayHint()}你方便吗？”",
                        playful = "“有个问题只能当面问，${weekdayHint()}出来一下？”",
                        firm = "“我想更了解你一点，找个时间聊聊。”",
                        pressure = "low",
                    )
                }
            }
            Goal.CONFIRM -> {
                reasons.add("确认关系是双向决定，表达要清晰但不索取答案。")
                Recommendation(
                    headline = "用一次清晰、不索取的表达确认，把选择留给对方",
                    reasons = reasons,
                    steady = "“我很喜欢现在的相处。你怎么看我们？”",
                    playful = "“再这样下去我会当真哦，你怎么想？”",
                    firm = "“我想要一段明确的关系。你不用现在回答，但我想让你知道。”",
                )
            }
            Goal.REPAIR -> {
                reasons.add("修复的顺序是：承认影响 → 简短说明 → 具体补救，不用长篇自辩。")
                Recommendation(
                    headline = "先承认影响，再给一个具体补救，不解释动机",
                    reasons = reasons,
                    steady = "“昨天那件事是我没做好，让你难受了。${repairHint()}，你看行吗？”",
                    playful = null,
                    firm = "“这件事我认。${repairHint()}。你要是还需要时间，我等。”",
                )
            }
            Goal.COMPARE -> {
                reasons.add("比较之前先分清「高分但不可得」和「略低但互惠稳定」。")
                Recommendation(
                    headline = "先分别评估可得性与互惠，再比较，不按单项分数排序",
                    reasons = reasons,
                    steady = "把每个人写成三行：最近两周谁主动、谁兑现、谁让你更安心，然后只比较这三行。",
                    firm = "给自己一个截止时间：到那天还没出现的选项，按「不可得」处理。",
                )
            }
            Goal.EXIT -> {
                reasons.add("退出也要可执行：一句明确结束，不追加解释，不再试探。")
                Recommendation(
                    headline = "体面收尾：一句明确结束 + 停止追加",
                    reasons = reasons,
                    steady = "“这段时间谢谢你。我想我们就到这里，祝你顺利。”",
                    firm = "“我不打算继续了。之后不用再联系，祝你一切都好。”",
                    pressure = "low",
                )
            }
        }
    }

    private fun plan(
        input: BriefInput,
        emotion: EmotionRead,
        interest: InterestRead,
        rec: Recommendation,
    ): ActionPlan {
        val now = when {
            rec.pressure == "low" -> "这条可以不回；如果要回，只回一句，不追加第二个问题。"
            emotion.intensity >= 7 -> "先别发。把想说的话写在备忘录里，等 24 小时再决定。"
            input.goal == Goal.REPAIR -> "先发那条承认影响的短句，不加解释。"
            else -> "发一条只承载一个主动作的消息（不要同时邀约、解释和追问）。"
        }
        val window = when (input.goal) {
            Goal.EXIT -> "发完之后 72 小时内不再主动联系"
            Goal.REPAIR -> "给对方 24–48 小时消化，不追问「你还在生气吗」"
            else -> "观察 3–7 天：对方是否主动发起、是否给出具体时间、是否兑现"
        }
        val stop = when {
            interest.reciprocity < 0.4 -> "两次具体邀请都没有替代安排，就停止加码，降到普通朋友频率"
            input.goal == Goal.EXIT -> "对方开始拉扯或给模糊承诺时，不回到谈判桌"
            else -> "对方明确说不想继续、要求不要联系，就立即停止推进"
        }
        val comeback = when (input.goal) {
            Goal.REPAIR -> "对方开始回应具体内容（而不只是「嗯」），说明可以往下谈"
            Goal.EXIT -> "没有回访信号也算正常；不要用对方的反应验证自己的决定"
            else -> "对方主动发问、提出时间、或兑现了一个小承诺——这三个出现任意一个，才可以推进"
        }
        return ActionPlan(now, window, stop, comeback)
    }

    private fun questions(input: BriefInput, evidence: EvidenceTriad): List<String> {
        val out = ArrayList<String>()
        if (input.stage == Stage.UNKNOWN) out.add("你们现在算哪个阶段：刚认识、暧昧、约会中，还是已经稳定？")
        if (input.goal == Goal.COMPARE) out.add("你比较的几个人，最近两周分别主动找过你几次？")
        evidence.unknowns.firstOrNull { it.contains("现实压力") }?.let {
            out.add("对方最近有没有明确的现实压力（加班、考试、家里的事）？")
        }
        if (input.turns.lastOrNull { it.speaker == Speaker.OTHER }?.text?.let { QUESTION_RE.containsMatchIn(it) } == true) {
            out.add("对方最后那条是在问你问题——你想直接回答，还是先把话题拉回你想说的？")
        }
        return out.distinct().take(3)
    }

    private fun weekdayHint(): String {
        val today = java.time.LocalDate.now()
        val delta = (5 - today.dayOfWeek.value + 7) % 7
        val target = today.plusDays(if (delta == 0) 7L else delta.toLong())
        return if (target.monthValue == today.monthValue) "这周五" else "下周五"
    }
    private fun placeHint(): String = "那家新开的店"
    private fun repairHint(): String = "这周末我把时间空出来"
}
