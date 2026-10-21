package io.junshi.core.kb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Evidence strength, mirroring corpus/knowledge/01-证据分级与内容边界.md. */
@Serializable
enum class Strength {
    @SerialName("policy") POLICY,
    @SerialName("strong") STRONG,
    @SerialName("framework") FRAMEWORK,
    @SerialName("popular") POPULAR,
    @SerialName("experience") EXPERIENCE;

    /** The wording a citation may use: never stronger than the source allows. */
    val label: String
        get() = when (this) {
            POLICY -> "规则"
            STRONG -> "较强证据"
            FRAMEWORK -> "理论框架"
            POPULAR -> "流行说法"
            EXPERIENCE -> "经验策略"
        }
}

@Serializable
data class KnowledgeChunk(
    val id: String,
    val doc: String,
    val layer: String,
    val title: String,
    val heading: String = "",
    val text: String,
    val chars: Int,
)

@Serializable
data class DocMeta(
    val id: String,
    val path: String,
    val title: String,
    val layer: String,
    val strength: Strength,
    val tags: List<String> = emptyList(),
    val chunks: Int = 0,
    val chars: Int = 0,
)

@Serializable
data class Upstream(
    val repo: String,
    val role: String = "",
    val license: String = "",
)

@Serializable
data class Totals(
    val docs: Int = 0,
    val chunks: Int = 0,
    val chars: Int = 0,
    val knowledgeDocs: Int = 0,
    val practicalDocs: Int = 0,
)

@Serializable
data class Manifest(
    val schema: Int,
    val source: String,
    val upstreams: List<Upstream> = emptyList(),
    val maxChunkChars: Int = 2400,
    val docs: List<DocMeta> = emptyList(),
    val totals: Totals = Totals(),
)

@Serializable
data class Route(
    val id: String,
    val label: String,
    val keywords: List<String> = emptyList(),
    val docs: List<String> = emptyList(),
    val rationale: String = "",
)

@Serializable
data class BehaviourRoute(
    val label: String,
    val docs: List<String> = emptyList(),
    val rationale: String = "",
)

@Serializable
data class RoutesFile(
    val schema: Int = 1,
    val source: String = "",
    val routes: List<Route> = emptyList(),
    val behaviours: Map<String, BehaviourRoute> = emptyMap(),
)

/** A chunk that matched, with the score and the doc it came from. */
data class ChunkHit(
    val chunk: KnowledgeChunk,
    val doc: DocMeta,
    val score: Double,
)

/** A route that matched the conversation, with why it matched. */
data class RouteHit(
    val route: Route,
    val score: Double,
    val matched: List<String>,
)

/** Context packed to a character budget, ready to go into a prompt. */
data class PackedContext(
    val text: String,
    val hits: List<ChunkHit>,
    val routes: List<RouteHit>,
    val chars: Int,
) {
    val isEmpty: Boolean get() = text.isBlank()
}

/** One citation line the strategist may show the user. */
data class Citation(
    val doc: DocMeta,
    val heading: String,
    val strength: Strength,
) {
    fun line(): String = when {
        heading.isBlank() || heading == doc.title -> "${doc.title}（${strength.label}）"
        else -> "${doc.title} · $heading（${strength.label}）"
    }
}
