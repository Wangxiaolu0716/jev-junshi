package io.junshi.adapter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JunshiBridgeTest {

    private val bridge = JunshiBridge.bundled()

    @Test
    fun `behaviour hint explains a complaint with the conflict guide`() {
        val hint = bridge.hintFor("R11")
        assertNotNull(hint)
        assertTrue(hint.contains("军师"))
        assertTrue(hint.contains("沟通"), hint)
    }

    @Test
    fun `background is usable as ChatContext background`() {
        val lines = listOf(
            JunshiBridge.Line(false, "最近好累，什么都不想弄"),
            JunshiBridge.Line(true, "怎么了，要不要说说"),
            JunshiBridge.Line(false, "算了，说了也没用"),
        )
        val result = bridge.backgroundFor(lines, scene = "romance", relationshipId = "talking", goal = "advance")
        assertTrue(result.background.isNotBlank())
        assertTrue(result.chars > 0)
        assertTrue(result.background.contains("不编造"))
        assertTrue(result.routes.isNotEmpty())
        assertTrue(result.citations.isNotEmpty())
    }

    @Test
    fun `empty screen injects nothing rather than a template`() {
        val result = bridge.backgroundFor(emptyList())
        assertEquals("", result.background)
        assertEquals(0, result.chars)
    }

    @Test
    fun `scene ids match jarvis`() {
        assertEquals(JunshiBridge.Scene.ROMANCE, JunshiBridge.Scene.fromId("romance"))
        assertEquals(JunshiBridge.Scene.GENERAL, JunshiBridge.Scene.fromId("nope"))
    }
}
