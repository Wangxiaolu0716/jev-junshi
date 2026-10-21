package io.junshi.adapter

import io.junshi.core.BehaviourHint
import io.junshi.core.Junshi
import io.junshi.core.brief.BriefInput
import io.junshi.core.brief.Goal
import io.junshi.core.brief.PartnerProfile
import io.junshi.core.brief.Speaker
import io.junshi.core.brief.Stage
import io.junshi.core.brief.StrategyBrief
import io.junshi.core.brief.Turn
import io.junshi.core.kb.KnowledgeBase
import java.io.File

/**
 * Adapter for jev-chat/jev-chat-jarvis.
 *
 * Jarvis owns capture, judgement and the "you press send" contract; this bridge
 * only answers two questions inside it:
 *
 *  1. [backgroundFor] — what should be injected as `ChatContext.background`
 *     so the reply model writes with the strategist's reading in hand.
 *  2. [hintFor] — why a stance card suggests what it suggests, shown under it.
 *
 * It mirrors Jarvis' own vocabulary (Scene ids, RomanceScene relationship ids
 * and behaviour ids) so no translation table is needed at the call site.
 * Jarvis code is not imported: the bridge compiles on its own and can be
 * dropped into `cn/app` or `global/core` unchanged.
 */
class JunshiBridge(private val junshi: Junshi) {

    constructor(kb: KnowledgeBase) : this(Junshi(kb))

    /** Jarvis' five scenes. */
    enum class Scene(val id: String) {
        WORK("work"), ROMANCE("romance"), FRIENDS("friends"), FAMILY("family"), GENERAL("general");

        companion object {
            fun fromId(id: String?): Scene = entries.firstOrNull { it.id == id } ?: GENERAL
        }
    }

    data class Line(val fromMe: Boolean, val text: String)

    data class BridgeResult(
        /** Ready to become `ChatContext.background`; blank means "inject nothing". */
        val background: String,
        val routes: List<String>,
        val citations: List<String>,
        val chars: Int,
        val hint: String? = null,
    )

    /**
     * Build the background string for one analysis.
     *
     * @param lines the on-screen conversation, oldest first.
     * @param scene Jarvis' selected scene id (`work`, `romance`, ...).
     * @param relationshipId RomanceScene relationship id, e.g. `talking`.
     * @param goal what the user is trying to do; defaults to advancing.
     */
    fun backgroundFor(
        lines: List<Line>,
        scene: String = "romance",
        relationshipId: String? = null,
        goal: String = "advance",
        budget: Int = 1200,
        partnerCode: String = "对方",
        note: String = "",
    ): BridgeResult {
        if (lines.isEmpty()) return BridgeResult("", emptyList(), emptyList(), 0)
        val turns = lines.map { Turn(if (it.fromMe) Speaker.ME else Speaker.OTHER, it.text.trim()) }
        val input = BriefInput(
            turns = turns,
            stage = Stage.fromId(relationshipId),
            goal = Goal.fromId(goal),
            partner = PartnerProfile(code = partnerCode, relationshipNote = note),
            notes = "场景：${Scene.fromId(scene).id}",
        )
        val brief = junshi.brief(input, knowledgeBudget = budget.coerceIn(400, 2400))
        val packed = junshi.pack(
            turns.takeLast(8).joinToString("\n") { it.text } + "\n" + note,
            budget,
            sceneHint = sceneHintFor(scene, brief),
        )
        return BridgeResult(
            background = render(brief, packed.text),
            routes = brief.routes,
            citations = brief.citations.map { it.line() },
            chars = packed.chars,
        )
    }

    /** The full brief, for a panel that wants to show the five steps. */
    fun briefFor(
        lines: List<Line>,
        scene: String = "romance",
        relationshipId: String? = null,
        goal: String = "advance",
        note: String = "",
    ): StrategyBrief {
        val turns = lines.map { Turn(if (it.fromMe) Speaker.ME else Speaker.OTHER, it.text.trim()) }
        return junshi.brief(
            BriefInput(
                turns = turns,
                stage = Stage.fromId(relationshipId),
                goal = Goal.fromId(goal),
                partner = PartnerProfile(relationshipNote = note),
            )
        )
    }

    /** "为什么这样建议" for a behaviour card (R01..R23, loyalty_test, ultimatum). */
    fun hintFor(behaviourId: String): String? {
        val hint: BehaviourHint = junshi.behaviourHint(behaviourId) ?: return null
        return buildString {
            append("军师：").append(hint.rationale)
            if (hint.docs.isNotEmpty()) {
                append("（依据：")
                append(hint.docs.joinToString("、") { it.title })
                append("）")
            }
        }
    }

    fun leadFor(behaviourId: String): String? = junshi.behaviourHint(behaviourId)?.docs?.firstOrNull()?.title

    private fun sceneHintFor(scene: String, brief: StrategyBrief): String? = when (Scene.fromId(scene)) {
        Scene.ROMANCE -> brief.routes.firstOrNull() ?: "reply_now"
        else -> brief.routes.firstOrNull()
    }

    /**
     * Background text. It follows SKILL.md's reading order and ends with an
     * instruction the reply model can obey: facts only, no mind-reading, no
     * new promises, one action per message.
     */
    private fun render(brief: StrategyBrief, knowledge: String): String = buildString {
        append("【军师读到的局面】")
        append("情绪：").append(brief.emotion.feeling).append("（强度 ").append(brief.emotion.intensity).append("/10）；")
        append("可见投入比：").append((brief.interest.reciprocity * 100).toInt()).append("%。")
        append(brief.emotion.validation).append('\n')
        if (brief.evidence.facts.isNotEmpty()) {
            append("事实：").append(brief.evidence.facts.take(3).joinToString("；")).append('\n')
        }
        if (brief.evidence.inferences.isNotEmpty()) {
            append("推测（不可当事实写进回复）：").append(brief.evidence.inferences.take(2).joinToString("；")).append('\n')
        }
        if (brief.evidence.unknowns.isNotEmpty()) {
            append("未知：").append(brief.evidence.unknowns.take(2).joinToString("；")).append('\n')
        }
        append("建议方向：").append(brief.recommendation.headline).append('\n')
        brief.safety.forEach { append("安全优先：").append(it.kind).append("——").append(it.guidance).append('\n') }
        if (brief.citations.isNotEmpty()) {
            append("依据：").append(brief.citations.joinToString("；") { it.line() }).append('\n')
        }
        if (knowledge.isNotBlank()) {
            append("\n【可引用的知识摘录】\n").append(knowledge).append('\n')
        }
        append("\n写回复时：只使用上面的事实，不编造经历与承诺；不读心；一条消息只做一个动作；对方明确拒绝就停止推进。")
    }

    companion object {
        /** Convenience for hosts that keep the index next to the executable. */
        fun fromDirectory(dir: File): JunshiBridge = JunshiBridge(Junshi.fromDirectory(dir))

        fun bundled(): JunshiBridge = JunshiBridge(Junshi.bundled())
    }
}
