package io.junshi.core.kb

import kotlin.math.ln

/**
 * Okapi BM25 over the chunk index.
 *
 * Deliberately small: no embeddings, no server, no model download. The corpus
 * is ~120k characters, so an in-memory inverted index costs a few megabytes and
 * answers in microseconds — which is what an on-device assistant needs.
 */
class Bm25(
    private val chunks: List<KnowledgeChunk>,
    private val docs: Map<String, DocMeta>,
    private val k1: Double = 1.2,
    private val b: Double = 0.75,
) {
    private val termFreq: List<Map<String, Int>> = chunks.map { chunk ->
        val counts = HashMap<String, Int>(256)
        Tokenizer.tokenize(chunk.text).forEach { counts.merge(it, 1, Int::plus) }
        // Title and tags are part of what the chunk is about, at one occurrence each.
        val meta = docs[chunk.doc]
        if (meta != null) {
            Tokenizer.tokenize(meta.title).distinct().forEach { counts.merge(it, 1, Int::plus) }
            meta.tags.forEach { tag -> Tokenizer.tokenize(tag).distinct().forEach { counts.merge(it, 1, Int::plus) } }
        }
        counts
    }
    private val lengths: IntArray = termFreq.map { it.values.sum() }.toIntArray()
    private val avgLength: Double = if (lengths.isEmpty()) 0.0 else lengths.average()
    private val df: Map<String, Int> = buildMap {
        val seen = HashMap<String, Int>(4096)
        termFreq.forEach { tf -> tf.keys.forEach { seen.merge(it, 1, Int::plus) } }
        putAll(seen)
    }

    val size: Int get() = chunks.size

    /**
     * Ranked chunks. [onlyDocs] restricts the search to those doc ids (used
     * after routing so that a scene's must-read files win).
     */
    fun search(query: String, limit: Int = 10, onlyDocs: Set<String>? = null): List<ChunkHit> {
        val q = Tokenizer.tokenize(query)
        if (q.isEmpty() || chunks.isEmpty()) return emptyList()
        val qTerms = q.groupingBy { it }.eachCount()
        val n = chunks.size.toDouble()
        val results = ArrayList<ChunkHit>()
        for (i in chunks.indices) {
            val chunk = chunks[i]
            if (onlyDocs != null && chunk.doc !in onlyDocs) continue
            val tf = termFreq[i]
            var score = 0.0
            for ((term, qtf) in qTerms) {
                val f = tf[term] ?: continue
                val docFreq = df[term] ?: continue
                val idf = ln(1.0 + (n - docFreq + 0.5) / (docFreq + 0.5))
                val denom = f + k1 * (1.0 - b + b * lengths[i] / avgLength)
                score += idf * (f * (k1 + 1.0) / denom) * (1.0 + 0.2 * (qtf - 1))
            }
            if (score <= 0.0) continue
            val doc = docs[chunk.doc] ?: continue
            results.add(ChunkHit(chunk, doc, score))
        }
        results.sortWith(compareByDescending<ChunkHit> { it.score }.thenBy { it.chunk.id })
        return results.take(limit)
    }
}
