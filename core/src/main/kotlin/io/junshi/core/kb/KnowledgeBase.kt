package io.junshi.core.kb

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * The strategist corpus, loaded once and queried offline.
 *
 * Two lookups that answer different questions:
 *  - [route]  "which two or three documents does this situation call for?"
 *             (compiled from SKILL.md's 按需加载 table)
 *  - [search] "which passages inside those documents are relevant?"
 *
 * Loading is from a directory (CLI) or the classpath (Android / desktop jar).
 */
class KnowledgeBase private constructor(
    val manifest: Manifest,
    val routes: RoutesFile,
    val chunks: List<KnowledgeChunk>,
) {
    private val docsById: Map<String, DocMeta> = manifest.docs.associateBy { it.id }
    private val bm25 = Bm25(chunks, docsById)

    val docs: List<DocMeta> get() = manifest.docs
    val chunkCount: Int get() = chunks.size

    fun doc(id: String): DocMeta? = docsById[id]

    fun chunksOf(docId: String): List<KnowledgeChunk> = chunks.filter { it.doc == docId }

    /**
     * Scene routing. Scores literal keyword hits (longer keywords weigh more),
     * with the route label as a weak hint. Never returns nothing: `general`
     * scores 0.5 so the caller always has a safe entry point.
     */
    fun route(text: String, limit: Int = 3): List<RouteHit> {
        val haystack = text.lowercase()
        val hits = routes.routes.mapNotNull { route ->
            if (route.keywords.isEmpty()) {
                return@mapNotNull RouteHit(route, if (haystack.isBlank()) 1.0 else 0.5, emptyList())
            }
            var score = 0.0
            val matched = ArrayList<String>()
            route.keywords.forEach { raw ->
                val kw = raw.lowercase()
                if (kw.isNotBlank() && haystack.contains(kw)) {
                    score += 1.0 + kw.length * 0.15
                    matched.add(raw)
                }
            }
            if (route.label.isNotBlank() && haystack.contains(route.label.lowercase())) {
                score += 0.5
                matched.add(route.label)
            }
            if (score <= 0.0) null else RouteHit(route, score, matched)
        }
        return hits.sortedWith(compareByDescending<RouteHit> { it.score }.thenBy { it.route.id }).take(limit)
    }

    /** Ranked chunks, optionally restricted to a document set and its tags. */
    fun search(query: String, limit: Int = 10, onlyDocs: Set<String>? = null): List<ChunkHit> =
        bm25.search(query, limit, onlyDocs)

    /** Documents a behaviour id (Jarvis R01..R23) should open. */
    fun behaviourRoute(behaviourId: String): BehaviourRoute? = routes.behaviours[behaviourId]

    /**
     * Pack retrieval into a character budget.
     *
     * Order: the routed documents first (they are what SKILL.md says to read),
     * then BM25 hits elsewhere. Whole chunks only — an excerpt that stops
     * mid-sentence would be quoted as if it were the source's claim.
     */
    fun pack(
        query: String,
        budgetChars: Int = 1500,
        sceneHint: String? = null,
        maxChunks: Int = 6,
    ): PackedContext {
        val routeHits = if (sceneHint != null) {
            routes.routes.filter { it.id == sceneHint }.map { RouteHit(it, 1.0, emptyList()) } +
                route(query, 2).filter { it.route.id != sceneHint }
        } else {
            route(query, 2)
        }
        // The scene's own files come first, in the order the skill lists them;
        // keyword routing only fills what is left of the budget.
        val primary = if (sceneHint != null) {
            routes.routes.firstOrNull { it.id == sceneHint }?.docs?.toList().orEmpty()
        } else {
            emptyList()
        }
        val preferred = LinkedHashSet<String>()
        preferred.addAll(primary)
        routeHits.forEach { preferred.addAll(it.route.docs) }
        val secondary = preferred.filter { it !in primary }

        // Order matters, and it is the skill's order: the routed documents come
        // first (their matching sections, then their lead section even when no
        // keyword matched), and only then the rest of the corpus. A routed file
        // is read because the situation calls for it, not because it scored.
        val stream = ArrayList<ChunkHit>()
        fun addDocGroup(docIds: List<String>) {
            if (docIds.isEmpty()) return
            stream += search(query, maxChunks * 2, docIds.toSet())
            docIds.forEach { docId ->
                val lead = chunks.firstOrNull { it.doc == docId }
                val meta = docsById[docId]
                if (lead != null && meta != null) stream += ChunkHit(lead, meta, 0.01)
            }
        }
        addDocGroup(primary)
        addDocGroup(secondary)
        stream += search(query, maxChunks * 3).filter { it.chunk.doc !in preferred }

        // At most two sections per document: one long file must not eat the budget.
        val ranked = ArrayList<ChunkHit>()
        val perDoc = HashMap<String, Int>()
        stream.forEach { hit ->
            if (ranked.any { it.chunk.id == hit.chunk.id }) return@forEach
            val count = perDoc.getOrDefault(hit.chunk.doc, 0)
            if (count >= MAX_CHUNKS_PER_DOC) return@forEach
            perDoc[hit.chunk.doc] = count + 1
            ranked += hit
        }

        val sb = StringBuilder()
        val used = ArrayList<ChunkHit>()
        var budget = budgetChars
        for (hit in ranked) {
            if (used.size >= maxChunks) break
            val header = buildString {
                append("【").append(hit.doc.title)
                val h = hit.chunk.heading
                if (h.isNotBlank() && h != hit.doc.title) append(" · ").append(h)
                append("（").append(hit.doc.strength.label).append("）】")
                appendLine()
            }
            val cost = header.length + hit.chunk.text.length + 2
            if (cost > budget) {
                if (used.isNotEmpty()) continue
                // First chunk may be clipped at a sentence boundary so the budget is never blown.
                val room = budget - header.length - 2
                if (room <= 40) break
                val clipped = clipToSentence(hit.chunk.text, room)
                if (clipped.isNotBlank()) {
                    sb.append(header).append(clipped).append('…').appendLine()
                    sb.appendLine()
                    used.add(hit)
                }
                break
            }
            sb.append(header).append(hit.chunk.text).appendLine()
            sb.appendLine()
            used.add(hit)
            budget -= cost
        }
        val text = sb.toString().trim()
        return PackedContext(text, used, routeHits, text.length)
    }

    private fun clipToSentence(text: String, room: Int): String {
        if (text.length <= room) return text
        val cut = text.lastIndexOf('。', room).let { if (it > 60) it else text.lastIndexOf('\n', room) }
        val end = if (cut > 60) cut + 1 else room
        return text.substring(0, end).trimEnd()
    }

    companion object {
        private const val MAX_CHUNKS_PER_DOC = 2
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** Load from a directory that contains index.jsonl / routes.json / manifest.json. */
        fun fromDirectory(dir: File): KnowledgeBase {
            require(dir.isDirectory) { "knowledge directory not found: ${dir.path}" }
            fun read(name: String): String {
                val f = File(dir, name)
                require(f.isFile) { "missing ${f.path}; run: python tools/build_kb.py" }
                return f.readText(StandardCharsets.UTF_8)
            }
            val manifest = json.decodeFromString(Manifest.serializer(), read("manifest.json"))
            val routes = json.decodeFromString(RoutesFile.serializer(), read("routes.json"))
            val chunks = read("index.jsonl").lineSequence()
                .filter { it.isNotBlank() }
                .map { json.decodeFromString(KnowledgeChunk.serializer(), it) }
                .toList()
            require(chunks.isNotEmpty()) { "index.jsonl is empty; run: python tools/build_kb.py" }
            return KnowledgeBase(manifest, routes, chunks)
        }

        /** Load the index bundled as a resource (jar / Android assets unpacked). */
        fun fromClasspath(prefix: String = ""): KnowledgeBase {
            fun read(name: String): String {
                val path = if (prefix.isBlank()) name else "${prefix.trimEnd('/')}/$name"
                val stream = KnowledgeBase::class.java.classLoader.getResourceAsStream(path)
                    ?: error("resource not found: $path (run python tools/build_kb.py)")
                return stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            }
            val manifest = json.decodeFromString(Manifest.serializer(), read("manifest.json"))
            val routes = json.decodeFromString(RoutesFile.serializer(), read("routes.json"))
            val chunks = read("index.jsonl").lineSequence()
                .filter { it.isNotBlank() }
                .map { json.decodeFromString(KnowledgeChunk.serializer(), it) }
                .toList()
            return KnowledgeBase(manifest, routes, chunks)
        }
    }
}
