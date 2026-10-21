package io.junshi.core

import io.junshi.core.memory.Confidence
import io.junshi.core.memory.JunshiMemory
import io.junshi.core.memory.MemoryScope
import io.junshi.core.memory.SourceType
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryTest {

    private val dir = Files.createTempDirectory("junshi-mem").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `nothing is written before explicit consent`() {
        val mem = JunshiMemory(dir)
        val r = mem.put(MemoryScope.OBJECT, "A", "mbti", "INFP", SourceType.USER_EXPLICIT)
        assertFalse(r.ok)
        assertEquals("NOT_ENABLED", r.code)
        assertTrue(mem.status().records.isEmpty())
    }

    @Test
    fun `enable requires a reason, then writes and recalls`() {
        val mem = JunshiMemory(dir)
        assertFalse(mem.enable("  ").ok)
        assertTrue(mem.enable("用户要求记住关系背景").ok)
        val r = mem.put(MemoryScope.OBJECT, "A", "mbti", "INFP", SourceType.USER_EXPLICIT, Confidence.HIGH)
        assertTrue(r.ok)
        assertEquals(1, mem.recall("A").size)
        assertEquals("INFP", mem.recall("A").first().value)
        assertTrue(mem.digest("A").contains("INFP"))
    }

    @Test
    fun `same field is updated rather than duplicated`() {
        val mem = JunshiMemory(dir)
        mem.enable("test")
        mem.put(MemoryScope.OBJECT, "A", "stage", "暧昧", SourceType.USER_EXPLICIT)
        mem.put(MemoryScope.OBJECT, "A", "stage", "约会", SourceType.USER_REPORT)
        val rows = mem.recall("A")
        assertEquals(1, rows.size)
        assertEquals("约会", rows.first().value)
        assertTrue(mem.operations().any { it.action == "update" })
    }

    @Test
    fun `forget removes the record but keeps an auditable ledger`() {
        val mem = JunshiMemory(dir)
        mem.enable("test")
        val r = mem.put(MemoryScope.EVENT, "A", "last_meet", "上周六看了电影", SourceType.USER_REPORT)
        val id = r.record!!.id
        assertTrue(mem.forget(id).ok)
        assertTrue(mem.recall("A").isEmpty())
        assertTrue(mem.operations().any { it.action == "forget" && it.beforeJson.contains("上周六") })
    }

    @Test
    fun `pause stops writing and purge deletes everything`() {
        val mem = JunshiMemory(dir)
        mem.enable("test")
        mem.put(MemoryScope.USER, "me", "mbti", "INTJ", SourceType.USER_EXPLICIT)
        assertTrue(mem.pause().ok)
        assertFalse(mem.put(MemoryScope.USER, "me", "mbti", "ENFP", SourceType.USER_EXPLICIT).ok)
        assertTrue(mem.purge().ok)
        assertTrue(mem.status().records.isEmpty())
        assertFalse(mem.status().consent)
    }

    @Test
    fun `over-long values are refused instead of silently truncated`() {
        val mem = JunshiMemory(dir)
        mem.enable("test")
        val r = mem.put(MemoryScope.EVENT, "A", "story", "x".repeat(201), SourceType.USER_REPORT)
        assertFalse(r.ok)
        assertEquals("TOO_LONG", r.code)
    }

    @Test
    fun `per-scope cap evicts the oldest and records it`() {
        val mem = JunshiMemory(dir)
        mem.enable("test")
        repeat(17) { i ->
            mem.put(MemoryScope.OBJECT, "A", "field$i", "v$i", SourceType.USER_EXPLICIT)
        }
        val objects = mem.status().records.filter { it.scope == MemoryScope.OBJECT }
        assertEquals(MemoryScope.OBJECT.cap, objects.size)
        assertTrue(mem.operations().any { it.action == "evict" })
    }
}
