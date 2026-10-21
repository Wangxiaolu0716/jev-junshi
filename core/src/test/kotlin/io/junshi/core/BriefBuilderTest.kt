package io.junshi.core

import io.junshi.core.brief.BriefBuilder
import io.junshi.core.brief.BriefInput
import io.junshi.core.brief.Goal
import io.junshi.core.brief.Speaker
import io.junshi.core.brief.Stage
import io.junshi.core.brief.Turn
import io.junshi.core.brief.UserProfile
import io.junshi.core.kb.KnowledgeBase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BriefBuilderTest {

    private lateinit var kb: KnowledgeBase

    @BeforeTest
    fun load() {
        kb = KnowledgeBase.fromClasspath()
    }

    private fun build(turns: List<Turn>, goal: Goal = Goal.ADVANCE, stage: Stage = Stage.UNKNOWN, intensity: Int? = null) =
        BriefBuilder(kb).build(
            BriefInput(
                turns = turns,
                goal = goal,
                stage = stage,
                user = UserProfile(feelingIntensity = intensity),
            )
        )

    @Test
    fun `cold short replies read as low investment and say so as a guess`() {
        val b = build(
            listOf(
                Turn(Speaker.ME, "这周末有空吗？我想请你吃饭"),
                Turn(Speaker.ME, "你说想吃那家火锅的"),
                Turn(Speaker.OTHER, "嗯"),
                Turn(Speaker.ME, "那周五还是周六？"),
                Turn(Speaker.OTHER, "再说吧"),
            )
        )
        assertTrue(b.interest.reciprocity < 0.55, "reciprocity was ${b.interest.reciprocity}")
        assertTrue(b.evidence.inferences.any { it.contains("推测") })
        assertTrue(b.recommendation.pressure == "low")
        assertTrue(b.action.stopCondition.isNotBlank())
    }

    @Test
    fun `repair goal leads with acknowledging impact`() {
        val b = build(
            listOf(
                Turn(Speaker.ME, "昨天没回你消息"),
                Turn(Speaker.OTHER, "我等你到十二点"),
            ),
            goal = Goal.REPAIR,
        )
        assertTrue(b.recommendation.headline.contains("承认影响"), b.recommendation.headline)
        assertTrue(b.action.now.contains("承认影响") || b.action.now.contains("短句"))
    }

    @Test
    fun `crisis words stop strategy and put safety first`() {
        val b = build(
            listOf(
                Turn(Speaker.OTHER, "你敢分手我就去你公司闹，还威胁要打我"),
                Turn(Speaker.ME, "我很害怕"),
            ),
            goal = Goal.EXIT,
        )
        assertTrue(b.safety.isNotEmpty())
        assertEquals("safety", b.recommendation.pressure)
        assertTrue(b.recommendation.playful == null, "no flirty drafts under a safety flag")
        assertTrue(b.questions.first().contains("安全"))
    }

    @Test
    fun `emotion is named before advice and cites a source`() {
        val b = build(
            listOf(
                Turn(Speaker.OTHER, "最近真的心累，什么都不想管"),
                Turn(Speaker.ME, "怎么了"),
            )
        )
        assertTrue(b.emotion.feeling.contains("疲惫") || b.emotion.feeling.contains("累"))
        assertTrue(b.emotion.validation.isNotBlank())
        assertTrue(b.citations.isNotEmpty(), "brief must cite at least one source")
        assertTrue(b.citations.all { it.strength.label.isNotBlank() })
    }

    @Test
    fun `advance with real reciprocity allows a concrete invitation`() {
        val b = build(
            listOf(
                Turn(Speaker.OTHER, "周五晚上我一般都有空，你呢"),
                Turn(Speaker.ME, "我也空，想去看那个展"),
                Turn(Speaker.OTHER, "好啊，几点？"),
                Turn(Speaker.ME, "七点？"),
                Turn(Speaker.OTHER, "可以，那家店我也想试"),
            ),
            goal = Goal.ADVANCE,
            stage = Stage.DATING,
        )
        assertTrue(b.interest.reciprocity >= 0.6, "reciprocity ${b.interest.reciprocity}")
        assertTrue(b.recommendation.headline.contains("邀约"), b.recommendation.headline)
        assertTrue(b.recommendation.playful != null)
    }

    @Test
    fun `empty chat still produces unknowns instead of invented facts`() {
        val b = build(listOf(Turn(Speaker.ME, "在吗")))
        assertFalse(b.evidence.facts.isNotEmpty() && b.evidence.unknowns.isEmpty())
        assertTrue(b.evidence.unknowns.isNotEmpty())
        assertTrue(b.questions.isNotEmpty())
    }

    @Test
    fun `emotional overload adds a safety flag even without crisis words`() {
        val b = build(listOf(Turn(Speaker.OTHER, "随便你")), intensity = 9)
        assertTrue(b.safety.any { it.kind == "情绪过载" })
    }
}
