package io.junshi.core.prompt

import io.junshi.core.brief.BriefInput
import io.junshi.core.brief.Speaker
import io.junshi.core.brief.StrategyBrief
import io.junshi.core.kb.PackedContext

/**
 * Turns a [StrategyBrief] plus retrieved knowledge into a prompt a model can
 * follow, and parses the one JSON shape it is allowed to answer with.
 *
 * This is where the two upstream projects actually meet: Jarvis owns capture,
 * judging and the "you press send" contract; the strategist owns the reading
 * order and the tone. The prompt keeps both: analysis first, drafts second,
 * never sent automatically.
 */
object PromptCompiler {

    const val MAX_REPLIES = 3

    val SYSTEM = """
你是「军师」，一个站在用户这一边的聊天决策助手。你的工作不是替用户谈恋爱，也不是让某个人爱上用户；你帮用户看懂局面、保住自尊和边界、做出可执行的一步。

【硬规则，任何情况下优先】
1. 只把用户提供的原文、说话人和顺序当事实。不要补线下动作、语气或对方内心；不确定就写「未知」。
2. 不读心。所有推断必须显式标注「推测」；猜错时直接承认。
3. 不替用户表白、承诺或道歉。回复草稿是给用户改的，用户自己按发送。
4. 不提供操控方案：不做服从测试、不制造虚假时间压力、不假装有别人、不冷暴力、不贬低、不煤气灯、不孤立、不跟踪、不性施压。
5. 出现家暴、跟踪、威胁、强迫、自伤或人身危险时，停止策略建议，先确认当下安全并建议联系可信支持或当地紧急服务。
6. 尊重拒绝：对方明确表示不想发展、要求不要联系时，停止推进，只给体面收尾。
7. 证据强度要标出来：较强证据 / 理论框架 / 流行说法 / 经验策略。不要把 MBTI、星座、冷读当成诊断。
8. 输出中文，口语，像人在发消息；不用客服腔、不堆成语、不用长破折号、不用 emoji。

【分析顺序，不可跳步】
第一步 情绪落地：2–4 句，指出感受、触发点、冲突。认可感受，但不为未经证实的解释背书。
第二步 事实拆分：已知事实 / 合理推测 / 关键未知，各列 1–4 条。
第三步 利益判断：互惠、可靠、吸引、价值观、现实可行性、可逆性、安全与机会成本；多人时先分别再比较。
第四步 明确建议：先给一句首选 + 2–4 个理由；有真实权衡时给不超过三个版本：稳健、会撩/策略、强势（强势是边界与快速筛选，不是羞辱或控制）。
第五步 行动收束：一个现在能做的小动作 + 观察窗口 + 停止条件 + 值得回来反馈的信号。只问 1–3 个真正影响决策的问题。

【只输出 JSON】
{"emotion":{"feeling":"","validation":""},
 "facts":[""],"inferences":[""],"unknowns":[""],
 "advice":{"headline":"","reasons":[""]},
 "replies":[{"label":"稳健","text":""},{"label":"会撩","text":""},{"label":"边界","text":""}],
 "action":{"now":"","window":"","stop":"","signal":""},
 "citations":[""]}
不要输出 JSON 以外的任何文字。
""".trimIndent()

    fun userPrompt(
        brief: StrategyBrief,
        input: BriefInput,
        knowledge: PackedContext,
        memoryDigest: String = "",
        userDetails: String = "",
    ): String = buildString {
        append("【聊天原文（越靠下越新）】\n")
        input.turns.filter { it.speaker != Speaker.SYSTEM }.forEach {
            append(if (it.speaker == Speaker.ME) "我：" else "对方：").append(it.text.trim()).append('\n')
        }
        append("\n【关系与目标】\n")
        append("阶段：").append(input.stage.label).append("；目标：").append(input.goal.label).append('\n')
        if (input.partner.relationshipNote.isNotBlank()) {
            append("背景：").append(input.partner.relationshipNote.trim()).append('\n')
        }
        if (input.notes.isNotBlank()) append("补充：").append(input.notes.trim()).append('\n')
        if (userDetails.isNotBlank()) append("用户自己的话（必须尊重）：").append(userDetails.trim()).append('\n')

        append("\n【已经读到的信号】\n")
        append("情绪：").append(brief.emotion.feeling).append("（强度 ").append(brief.emotion.intensity).append("/10）\n")
        append("可见投入比：").append((brief.interest.reciprocity * 100).toInt()).append("%\n")
        append("首选建议：").append(brief.recommendation.headline).append('\n')

        if (memoryDigest.isNotBlank()) {
            append("\n【长期档案（用户已同意保存，可撤销）】\n").append(memoryDigest.trim()).append('\n')
        }
        if (!knowledge.isEmpty) {
            append("\n【军师知识库摘录（可用作依据，不要整段照抄）】\n")
            append(knowledge.text).append('\n')
        }
        if (brief.safety.isNotEmpty()) {
            append("\n【安全优先】\n")
            brief.safety.forEach { append("- ").append(it.kind).append("：").append(it.guidance).append('\n') }
            append("这一轮不输出撩人话术，只输出安全与边界相关的回复。\n")
        }
        append("\n现在按五步分析并输出 JSON。")
    }

    /** One parsed reply. [label] is what the panel shows above the text. */
    data class Reply(val label: String, val text: String)

    /**
     * Parse the reply list, tolerating a code fence or stray prose. Returns at
     * most [MAX_REPLIES], dropping empties and exact duplicates; never invents
     * a placeholder.
     */
    fun parseReplies(raw: String): List<Reply> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val body = raw.substring(start, end + 1)
        val arrayStart = body.indexOf("\"replies\"")
        if (arrayStart < 0) return emptyList()
        val open = body.indexOf('[', arrayStart)
        if (open < 0) return emptyList()
        val out = ArrayList<Reply>()
        var i = open + 1
        while (i < body.length && out.size < MAX_REPLIES) {
            val objStart = body.indexOf('{', i)
            if (objStart < 0) break
            val objEnd = findObjectEnd(body, objStart)
            if (objEnd < 0) break
            val obj = body.substring(objStart, objEnd + 1)
            val label = field(obj, "label")
            val text = field(obj, "text")
            if (!text.isNullOrBlank()) {
                val cleaned = text.trim().trim('“', '”', '"')
                if (out.none { it.text == cleaned }) out.add(Reply(label?.takeIf { it.isNotBlank() } ?: "方案 ${out.size + 1}", cleaned))
            }
            i = objEnd + 1
            val close = body.indexOf(']', i)
            if (close in 0..<i) break
        }
        return out
    }

    private fun findObjectEnd(s: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun field(obj: String, name: String): String? {
        val key = "\"$name\""
        val k = obj.indexOf(key)
        if (k < 0) return null
        val colon = obj.indexOf(':', k + key.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length || obj[i] != '"') return null
        i++
        val sb = StringBuilder()
        var escaped = false
        while (i < obj.length) {
            val c = obj[i]
            when {
                escaped -> {
                    sb.append(
                        when (c) {
                            'n' -> '\n'
                            't' -> '\t'
                            'r' -> '\r'
                            else -> c
                        }
                    )
                    escaped = false
                }
                c == '\\' -> escaped = true
                c == '"' -> return sb.toString()
                else -> sb.append(c)
            }
            i++
        }
        return null
    }
}
