package io.junshi.cli

import io.junshi.core.brief.StrategyBrief

/** Minimal JSON writer for `--json`. Escapes only what JSON requires. */
object Json {

    fun out(b: StrategyBrief): String = buildString {
        append("{")
        append("\"emotion\":{")
        append("\"feeling\":").append(str(b.emotion.feeling)).append(',')
        append("\"intensity\":").append(b.emotion.intensity).append(',')
        append("\"trigger\":").append(str(b.emotion.trigger)).append(',')
        append("\"conflict\":").append(str(b.emotion.conflict)).append(',')
        append("\"validation\":").append(str(b.emotion.validation))
        append("},")
        append("\"facts\":").append(arr(b.evidence.facts)).append(',')
        append("\"inferences\":").append(arr(b.evidence.inferences)).append(',')
        append("\"unknowns\":").append(arr(b.evidence.unknowns)).append(',')
        append("\"reciprocity\":").append(String.format("%.3f", b.interest.reciprocity)).append(',')
        append("\"signals\":").append(arr(b.interest.signals)).append(',')
        append("\"risks\":").append(arr(b.interest.risks)).append(',')
        append("\"opportunityCost\":").append(str(b.interest.opportunityCost)).append(',')
        append("\"advice\":{")
        append("\"headline\":").append(str(b.recommendation.headline)).append(',')
        append("\"reasons\":").append(arr(b.recommendation.reasons)).append(',')
        append("\"steady\":").append(str(b.recommendation.steady)).append(',')
        append("\"playful\":").append(opt(b.recommendation.playful)).append(',')
        append("\"firm\":").append(opt(b.recommendation.firm)).append(',')
        append("\"pressure\":").append(str(b.recommendation.pressure))
        append("},")
        append("\"action\":{")
        append("\"now\":").append(str(b.action.now)).append(',')
        append("\"window\":").append(str(b.action.window)).append(',')
        append("\"stop\":").append(str(b.action.stopCondition)).append(',')
        append("\"signal\":").append(str(b.action.comebackSignal))
        append("},")
        append("\"citations\":").append(arr(b.citations.map { it.line() })).append(',')
        append("\"questions\":").append(arr(b.questions)).append(',')
        append("\"safety\":").append(arr(b.safety.map { "${it.kind}: ${it.guidance}" })).append(',')
        append("\"routes\":").append(arr(b.routes))
        append("}")
    }

    private fun str(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun opt(s: String?): String = if (s == null) "null" else str(s)

    private fun arr(items: List<String>): String = items.joinToString(",", "[", "]") { str(it) }
}
