package io.junshi.core

import io.junshi.core.brief.BriefBuilder
import io.junshi.core.brief.BriefInput
import io.junshi.core.brief.StrategyBrief
import io.junshi.core.kb.ChunkHit
import io.junshi.core.kb.DocMeta
import io.junshi.core.kb.KnowledgeBase
import io.junshi.core.kb.PackedContext
import io.junshi.core.kb.RouteHit
import io.junshi.core.prompt.PromptCompiler
import java.io.File

/** One-shot facade: the only entry point a host (CLI, Jarvis, a server) needs. */
class Junshi(val kb: KnowledgeBase) {

    fun brief(input: BriefInput, knowledgeBudget: Int = 900): StrategyBrief =
        BriefBuilder(kb).build(input, knowledgeBudget)

    fun pack(query: String, budget: Int = 1500, sceneHint: String? = null): PackedContext =
        kb.pack(query, budget, sceneHint)

    fun routes(text: String): List<RouteHit> = kb.route(text)

    fun prompt(brief: StrategyBrief, input: BriefInput, knowledge: PackedContext, memoryDigest: String = ""): String =
        "${PromptCompiler.SYSTEM}\n\n${PromptCompiler.userPrompt(brief, input, knowledge, memoryDigest)}"

    fun behaviourHint(behaviourId: String): BehaviourHint? = BehaviourHint.of(kb, behaviourId)

    companion object {
        fun bundled(): Junshi = Junshi(KnowledgeBase.fromClasspath())

        fun fromDirectory(dir: File): Junshi = Junshi(KnowledgeBase.fromDirectory(dir))
    }
}

/**
 * What Jarvis' behaviour ids (R01..R23) open in the corpus. The label and
 * rationale here are what the floating panel can show under a stance card.
 */
data class BehaviourHint(
    val id: String,
    val label: String,
    val rationale: String,
    val docs: List<DocMeta>,
    val chunks: List<ChunkHit>,
) {
    companion object {
        fun of(kb: KnowledgeBase, behaviourId: String): BehaviourHint? {
            val route = kb.behaviourRoute(behaviourId) ?: return null
            val docs = route.docs.mapNotNull { kb.doc(it) }
            val chunks = kb.search(docs.joinToString(" ") { it.title + " " + it.tags.joinToString(" ") }, 3,
                route.docs.toSet())
            return BehaviourHint(behaviourId, route.label, route.rationale, docs, chunks)
        }
    }
}
