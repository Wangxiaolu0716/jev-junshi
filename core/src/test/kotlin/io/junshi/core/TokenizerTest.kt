package io.junshi.core

import io.junshi.core.kb.Tokenizer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenizerTest {

    @Test
    fun `chinese becomes bigrams so unseen wording still matches`() {
        val terms = Tokenizer.terms("不回消息")
        assertTrue(terms.contains("回消"))
        assertTrue(terms.contains("消息"))
        // The query "消息回得很慢" shares 消息 with the chunk above.
        assertTrue(Tokenizer.terms("消息回得很慢").contains("消息"))
    }

    @Test
    fun `latin words are lowercased and kept whole`() {
        val terms = Tokenizer.terms("MBTI 和 ChatLab 不一样")
        assertTrue(terms.contains("mbti"))
        assertTrue(terms.contains("chatlab"))
    }

    @Test
    fun `single function characters are dropped`() {
        val terms = Tokenizer.terms("的 了 是 吗")
        assertFalse(terms.contains("的"))
        assertFalse(terms.contains("了"))
    }

    @Test
    fun `digits survive because dates and amounts matter`() {
        val terms = Tokenizer.terms("7点 3000 块")
        assertTrue(terms.contains("7"), "single digit kept: $terms")
        assertTrue(terms.contains("3000"), "amount kept: $terms")
    }
}
