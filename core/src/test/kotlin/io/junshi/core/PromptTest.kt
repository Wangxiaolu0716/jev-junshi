package io.junshi.core

import io.junshi.core.prompt.PromptCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptTest {

    @Test
    fun `system prompt carries the non-negotiables`() {
        val s = PromptCompiler.SYSTEM
        assertTrue(s.contains("不读心"))
        assertTrue(s.contains("不提供操控方案"))
        assertTrue(s.contains("家暴"))
        assertTrue(s.contains("用户自己按发送"))
        assertTrue(s.contains("\"replies\""))
    }

    @Test
    fun `parses a fenced json answer`() {
        val raw = """
            这是分析：
            ```json
            {"emotion":{"feeling":"失落"},
             "replies":[{"label":"稳健","text":"我在，等你方便再聊。"},
                        {"label":"会撩","text":"再不理我，我就要开始编故事了。"},
                        {"label":"边界","text":"我需要一个明确的时间。"}]}
            ```
        """.trimIndent()
        val replies = PromptCompiler.parseReplies(raw)
        assertEquals(3, replies.size)
        assertEquals("稳健", replies[0].label)
        assertTrue(replies[1].text.contains("编故事"))
    }

    @Test
    fun `drops duplicates and never invents a reply`() {
        val raw = """{"replies":[{"label":"a","text":"好"},{"label":"b","text":"好"}]}"""
        assertEquals(1, PromptCompiler.parseReplies(raw).size)
        assertEquals(0, PromptCompiler.parseReplies("没有 JSON").size)
        assertEquals(0, PromptCompiler.parseReplies("{\"replies\":[]}").size)
    }

    @Test
    fun `chat text parser insists on known speakers`() {
        val turns = ChatText.parse("我: 在吗\n对方: 嗯\n")
        assertEquals(2, turns.size)
        assertEquals(io.junshi.core.brief.Speaker.ME, turns[0].speaker)
        val ts = ChatText.parse("me\t在吗\nother\t嗯")
        assertEquals(2, ts.size)
        var failed = false
        runCatching { ChatText.parse("路人: 你好") }.onFailure { failed = true }
        assertTrue(failed)
    }
}
