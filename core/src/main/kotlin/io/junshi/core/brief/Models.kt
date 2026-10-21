package io.junshi.core.brief

import io.junshi.core.kb.Citation

/** Who wrote a line. SYSTEM covers "you added X", dates and other app rows. */
enum class Speaker { ME, OTHER, SYSTEM }

/** One line of the chat as read, in order. */
data class Turn(val speaker: Speaker, val text: String, val ts: Long? = null)

/**
 * The relationship stage, mirroring Jarvis' RomanceScene relationship ids so the
 * two codebases can share one vocabulary without a translation table.
 */
enum class Stage(val id: String, val label: String) {
    UNKNOWN("unknown", "还不确定"),
    JUST_CONNECTED("just_connected", "刚认识"),
    TALKING("talking", "暧昧期"),
    DATING("dating", "约会中"),
    ESTABLISHED("established", "稳定伴侣"),
    EX_FRIENDS("ex_friends", "前任·仍联系"),
    EX_DISTANT("ex_distant", "前任·几乎不联系");

    companion object {
        fun fromId(id: String?): Stage =
            entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

/** What the user wants out of this round. SKILL.md's five goals. */
enum class Goal(val id: String, val label: String) {
    ADVANCE("advance", "推进"),
    CONFIRM("confirm", "确认关系"),
    REPAIR("repair", "修复"),
    COMPARE("compare", "比较选择"),
    EXIT("exit", "退出");

    companion object {
        fun fromId(id: String?): Goal =
            entries.firstOrNull { it.id == id } ?: ADVANCE
    }
}

/** The user's own side of the profile the strategist keeps. */
data class UserProfile(
    val mbti: String? = null,
    val selfScore: Int? = null,
    val strengths: String? = null,
    val weaknesses: String? = null,
    val feelingIntensity: Int? = null,
)

/** One person the user is talking to. Never scored by the assistant itself. */
data class PartnerProfile(
    val code: String = "对方",
    val mbti: String? = null,
    val userScore: Int? = null,
    val relationshipNote: String = "",
)

/** A key event the user told us about. Not inferred. */
data class KeyEvent(val text: String, val whenLabel: String? = null)

data class BriefInput(
    val turns: List<Turn>,
    val stage: Stage = Stage.UNKNOWN,
    val goal: Goal = Goal.ADVANCE,
    val user: UserProfile = UserProfile(),
    val partner: PartnerProfile = PartnerProfile(),
    val keyEvents: List<KeyEvent> = emptyList(),
    /** Extra background: the user's own notes, a prior summary, boundaries. */
    val notes: String = "",
)

/** Step 1: name the feeling before analysing anything. */
data class EmotionRead(
    val feeling: String,
    val intensity: Int,
    val trigger: String,
    val conflict: String,
    val validation: String,
)

/** Step 2: what the text proves, what it merely suggests, and what is missing. */
data class EvidenceTriad(
    val facts: List<String>,
    val inferences: List<String>,
    val unknowns: List<String>,
) {
    val isEmpty: Boolean get() = facts.isEmpty() && inferences.isEmpty() && unknowns.isEmpty()
}

/** Step 3: reciprocity and cost, as numbers the user can argue with. */
data class InterestRead(
    /** 0..1: how balanced the visible investment is. */
    val reciprocity: Double,
    val signals: List<String>,
    val risks: List<String>,
    val opportunityCost: String,
)

/** Step 4: one headline, reasons, and at most three versions. */
data class Recommendation(
    val headline: String,
    val reasons: List<String>,
    val steady: String,
    val playful: String? = null,
    val firm: String? = null,
    /** Escalation tier, e.g. "low" when the last message does not need a reply. */
    val pressure: String = "normal",
)

/** Step 5: one small move now, a window to watch, and a stop condition. */
data class ActionPlan(
    val now: String,
    val window: String,
    val stopCondition: String,
    val comebackSignal: String,
)

/** A safety branch that outranks strategy. */
data class SafetyFlag(val kind: String, val guidance: String)

data class StrategyBrief(
    val emotion: EmotionRead,
    val evidence: EvidenceTriad,
    val interest: InterestRead,
    val recommendation: Recommendation,
    val action: ActionPlan,
    val citations: List<Citation>,
    /** 1..3 questions that would actually change the advice. */
    val questions: List<String>,
    val safety: List<SafetyFlag> = emptyList(),
    /** The route ids the knowledge base matched, for debugging and for the panel. */
    val routes: List<String> = emptyList(),
) {
    /** The line a reply must not contradict. */
    val guardrail: String
        get() = buildString {
            append("不追读心，不替用户表态；")
            if (safety.isNotEmpty()) append("先安全再策略；")
            append("只把原文当事实。")
        }
}
