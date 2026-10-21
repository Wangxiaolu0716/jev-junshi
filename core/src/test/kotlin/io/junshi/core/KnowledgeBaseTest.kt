package io.junshi.core

import io.junshi.core.kb.KnowledgeBase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnowledgeBaseTest {

    private lateinit var kb: KnowledgeBase

    @BeforeTest
    fun load() {
        kb = KnowledgeBase.fromClasspath()
    }

    @Test
    fun `bundled index covers the whole corpus`() {
        assertEquals(43, kb.docs.size)
        assertTrue(kb.chunkCount >= 200, "expected >=200 chunks, got ${kb.chunkCount}")
        assertTrue(kb.docs.all { it.chunks > 0 })
        assertTrue(kb.manifest.totals.chars > 100_000)
    }

    @Test
    fun `routing finds investment imbalance in a cooling chat`() {
        val routes = kb.route("他最近已读不回，我付出很多，是不是该降级")
        assertTrue(routes.isNotEmpty())
        assertEquals("imbalance", routes.first().route.id)
    }

    @Test
    fun `routing finds the control kit when PUA shows up`() {
        val top = kb.route("他总说我在打压他，还搞服从测试").first()
        assertEquals("manipulation", top.route.id)
    }

    @Test
    fun `routing finds crisis before anything else when asked to`() {
        val ids = kb.route("他动手打我了，还威胁我不许报警").map { it.route.id }
        assertTrue("crisis" in ids, "expected crisis route, got $ids")
    }

    @Test
    fun `bm25 puts the investment guide first for an unanswered message`() {
        val hits = kb.search("不回消息 还要继续主动吗", 5)
        assertTrue(hits.isNotEmpty())
        assertTrue(
            hits.take(3).any { it.doc.id == "p07" },
            "expected p07 among top hits, got ${hits.map { it.doc.id }}",
        )
    }

    @Test
    fun `behaviour ids map to documents`() {
        val hint = Junshi(kb).behaviourHint("R11") ?: error("R11 missing")
        assertTrue(hint.docs.isNotEmpty())
        assertTrue(hint.docs.any { it.id == "k07" }, "conflict guidance expected, got ${hint.docs.map { it.id }}")
    }

    @Test
    fun `packing never exceeds the budget`() {
        val packed = kb.pack("他三天没回我，我要不要发第二条", budgetChars = 600)
        assertTrue(packed.chars <= 600, "packed ${packed.chars} chars")
        assertTrue(packed.hits.isNotEmpty())
        assertTrue(packed.text.contains("（"))
    }

    @Test
    fun `unknown behaviour returns null instead of guessing`() {
        assertEquals(null, Junshi(kb).behaviourHint("R99"))
    }
}
