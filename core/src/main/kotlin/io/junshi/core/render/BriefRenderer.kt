package io.junshi.core.render

import io.junshi.core.brief.StrategyBrief

/**
 * Human-readable rendering of a brief. Plain text on purpose: it pastes into
 * chat, into a terminal, or into the desktop window, and it carries no markup
 * the user would have to delete.
 */
object BriefRenderer {

    fun brief(b: StrategyBrief): String = buildString {
        appendLine("【军师简报】")
        appendLine()
        appendLine("① 情绪落地")
        appendLine("  感受：${b.emotion.feeling}（强度 ${b.emotion.intensity}/10）")
        appendLine("  触发：${b.emotion.trigger}")
        appendLine("  冲突：${b.emotion.conflict}")
        appendLine("  ${b.emotion.validation}")
        appendLine()
        appendLine("② 事实拆分")
        if (b.evidence.facts.isEmpty()) appendLine("  已知事实：暂无（原文不足）") else {
            appendLine("  已知事实：")
            b.evidence.facts.forEach { appendLine("    - $it") }
        }
        if (b.evidence.inferences.isNotEmpty()) {
            appendLine("  合理推测：")
            b.evidence.inferences.forEach { appendLine("    - $it") }
        }
        if (b.evidence.unknowns.isNotEmpty()) {
            appendLine("  关键未知：")
            b.evidence.unknowns.forEach { appendLine("    - $it") }
        }
        appendLine()
        appendLine("③ 利益判断")
        appendLine("  可见投入比：${(b.interest.reciprocity * 100).toInt()}%")
        b.interest.signals.forEach { appendLine("  · $it") }
        b.interest.risks.forEach { appendLine("  ! $it") }
        appendLine("  机会成本：${b.interest.opportunityCost}")
        appendLine()
        appendLine("④ 明确建议")
        appendLine("  首选：${b.recommendation.headline}")
        b.recommendation.reasons.forEachIndexed { i, r -> appendLine("  ${i + 1}. $r") }
        appendLine("  · 稳健：${b.recommendation.steady}")
        b.recommendation.playful?.let { appendLine("  · 会撩：$it") }
        b.recommendation.firm?.let { appendLine("  · 边界：$it") }
        appendLine("  （以上是例句，按你自己的口吻改，别照抄）")
        appendLine()
        appendLine("⑤ 行动收束")
        appendLine("  现在：${b.action.now}")
        appendLine("  窗口：${b.action.window}")
        appendLine("  停止：${b.action.stopCondition}")
        appendLine("  回访信号：${b.action.comebackSignal}")
        if (b.citations.isNotEmpty()) {
            appendLine()
            appendLine("依据：")
            b.citations.forEach { appendLine("  - ${it.line()}") }
        }
        if (b.questions.isNotEmpty()) {
            appendLine()
            appendLine("还需要你回答（会影响建议）：")
            b.questions.forEach { appendLine("  ? $it") }
        }
        if (b.safety.isNotEmpty()) {
            appendLine()
            appendLine("⚠ 安全优先")
            b.safety.forEach { appendLine("  - ${it.kind}：${it.guidance}") }
        }
        appendLine()
        appendLine("注意：这是一份判断，不是读心；所有「推测」都欢迎你用事实纠正。")
    }
}
