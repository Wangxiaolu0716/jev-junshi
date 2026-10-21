package io.junshi.core.kb

/**
 * Dependency-free tokenizer for mixed Chinese/English chat text.
 *
 * Chinese has no spaces, so a dictionary would be needed for real word
 * segmentation. CJK bigrams are the standard workaround: "回消息" becomes
 * 回消 / 消息, which matches "不回消息" and "消息回得很慢" without a word list.
 * Latin runs are lowercased words; digits are kept because dates and amounts
 * matter in relationship chats ("7点", "3000").
 */
object Tokenizer {

    /** Function words that would otherwise dominate every Chinese chunk. */
    private val STOP = setOf(
        "的", "了", "是", "在", "我", "你", "他", "她", "它", "们", "这", "那", "也", "都", "就",
        "不", "和", "与", "或", "而", "但", "又", "很", "还", "把", "被", "让", "给", "对", "从",
        "到", "为", "以", "及", "其", "之", "者", "所", "上", "下", "中", "里", "个", "有", "会",
        "要", "能", "可", "没", "没有", "一个", "什么", "怎么", "这样", "那样", "如果", "因为",
        "所以", "但是", "然后", "已经", "可以", "我们", "你们", "他们", "自己", "时候",
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "are", "was",
        "were", "be", "been", "it", "this", "that", "with", "as", "at", "by", "from", "but",
        "not", "you", "i", "he", "she", "they", "we", "my", "your",
    )

    private fun isCjk(cp: Int): Boolean =
        cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0xF900..0xFAFF

    private fun isLatin(cp: Int): Boolean =
        cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code

    private fun isDigit(cp: Int): Boolean = cp in '0'.code..'9'.code

    /** Tokens in order, with duplicates (BM25 needs term frequency). */
    fun tokenize(text: String): List<String> {
        val out = ArrayList<String>(text.length / 2 + 4)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when {
                isCjk(cp) -> {
                    // Collect the whole CJK run, then emit bigrams (plus single chars for length 1).
                    var j = i
                    val run = StringBuilder()
                    while (j < text.length) {
                        val c = text.codePointAt(j)
                        if (!isCjk(c)) break
                        run.appendCodePoint(c)
                        j += Character.charCount(c)
                    }
                    if (run.length == 1) {
                        val t = run.toString()
                        if (t !in STOP) out.add(t)
                    } else {
                        for (k in 0 until run.length - 1) {
                            val bigram = run.substring(k, k + 2)
                            if (bigram !in STOP) out.add(bigram)
                        }
                    }
                    i = j
                }
                isLatin(cp) || isDigit(cp) -> {
                    var j = i
                    val word = StringBuilder()
                    while (j < text.length) {
                        val c = text.codePointAt(j)
                        if (!(isLatin(c) || isDigit(c) || c == '-'.code || c == '_'.code)) break
                        word.appendCodePoint(c)
                        j += Character.charCount(c)
                    }
                    val w = word.toString().lowercase()
                    // Single digits are meaningful ("7点", "3000" splits on the CJK side);
                    // single Latin letters are not.
                    if (w.isNotEmpty() && (w.length > 1 || w[0].isDigit()) && w !in STOP) out.add(w)
                    i = j
                }
                else -> i += Character.charCount(cp)
            }
        }
        return out
    }

    /** Distinct tokens, for query-side matching and keyword overlap. */
    fun terms(text: String): Set<String> = tokenize(text).toSet()
}
