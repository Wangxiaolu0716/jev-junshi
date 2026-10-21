package io.junshi.cli

import io.junshi.core.brief.StrategyBrief
import io.junshi.core.render.BriefRenderer

/** Kept as a thin alias so the CLI and the desktop window render identically. */
object Render {
    fun brief(b: StrategyBrief): String = BriefRenderer.brief(b)
}
