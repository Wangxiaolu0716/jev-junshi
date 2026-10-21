package io.junshi.core.brief

/**
 * Rule-based reading of the chat. No model call, no inference beyond what the
 * text shows: every derived statement is labelled as an inference by
 * [EvidenceAnalyzer], and the strategist is forbidden from presenting it as fact.
 */

private val EMO_WORDS: Map<String, Pair<String, Int>> = linkedMapOf(
    "委屈" to ("委屈" to 3),
    "难受" to ("难受" to 3),
    "心累" to ("疲惫" to 2),
    "累" to ("疲惫" to 1),
    "崩溃" to ("崩溃" to 4),
    "烦" to ("烦躁" to 2),
    "生气" to ("生气" to 3),
    "气死" to ("生气" to 4),
    "恼" to ("生气" to 2),
    "失望" to ("失望" to 3),
    "失落" to ("失落" to 2),
    "难过" to ("难过" to 3),
    "伤心" to ("难过" to 3),
    "哭" to ("难过" to 3),
    "焦虑" to ("焦虑" to 3),
    "怕" to ("不安" to 2),
    "慌" to ("不安" to 2),
    "紧张" to ("紧张" to 2),
    "不安" to ("不安" to 3),
    "内耗" to ("内耗" to 3),
    "尴尬" to ("尴尬" to 2),
    "无语" to ("无语" to 2),
    "开心" to ("开心" to 2),
    "高兴" to ("开心" to 2),
    "甜" to ("开心" to 2),
    "喜欢" to ("心动" to 2),
    "想你" to ("想念" to 2),
    "想你" to ("想念" to 2),
    "爱你" to ("亲密" to 3),
    "忙" to ("忙碌" to 1),
    "随便" to ("冷淡" to 2),
    "都行" to ("冷淡" to 1),
    "算了" to ("失望" to 2),
    "再说吧" to ("冷淡" to 2),
    "改天" to ("冷淡" to 1),
)

object EmotionAnalyzer {

    fun read(input: BriefInput): EmotionRead {
        val recent = input.turns.filter { it.speaker == Speaker.OTHER }.takeLast(3)
        val text = recent.joinToString("\n") { it.text }
        var best: String? = null
        var bestWeight = 0
        EMO_WORDS.forEach { (needle, pair) ->
            if (text.contains(needle) && pair.second > bestWeight) {
                best = pair.first
                bestWeight = pair.second
            }
        }
        val exclaim = text.count { it == '!' || it == '！' }
        val repeat = Regex("[?？!！。]{2,}").findAll(text).count()
        val intensity = (2 + bestWeight * 1.4 + exclaim + repeat * 0.5)
            .coerceAtMost(10.0).let { kotlin.math.round(it).toInt() }
            .coerceAtLeast(if (best == null) 2 else 3)

        val feeling = best ?: if (recent.isEmpty()) "平静" else "说不清，偏中性"
        val last = recent.lastOrNull()?.text?.trim().orEmpty()
        val trigger = when {
            last.isEmpty() -> "这轮还没有对方的新消息"
            last.length <= 36 -> "对方说「${last}」"
            else -> "对方说「${last.take(36)}…」"
        }
        val conflict = when (input.goal) {
            Goal.ADVANCE -> "你想往前走一步，但当前的信号还不支持直接加码"
            Goal.CONFIRM -> "你想要一个定义，但直接要定义会把选择压到对方那边"
            Goal.REPAIR -> "你想把关系拉回来，但先被处理的是情绪而不是道理"
            Goal.COMPARE -> "你在几个人之间权衡，但比较之前得先分清可得性与互惠"
            Goal.EXIT -> "你想退出，但退出也需要一个体面的、可执行的收尾"
        }
        val validation = when (feeling) {
            "委屈", "失望", "难过" -> "会难受是正常的：你在意的东西没有被接住，这不是你太敏感。"
            "生气", "烦躁" -> "生气说明边界被碰到了，先别在气头上把话说死。"
            "焦虑", "不安", "紧张" -> "不安来自不确定，先把「已经发生的事」和「你担心的事」分开。"
            "疲惫", "内耗" -> "累往往不是因为一件事，而是因为一直在猜；这轮只处理一小步。"
            "开心", "心动", "亲密", "想念" -> "开心是真的，不用急着分析它；先享受，再决定要不要推进。"
            "冷淡" -> "对方的短回复可能是没空，也可能是降温；现在还不能下结论。"
            else -> "先把感受说出来就好，不需要马上证明它合理。"
        }
        return EmotionRead(feeling, intensity, trigger, conflict, validation)
    }
}

private val TIME_RE = Regex("(\\d{1,2}[点:：]\\d{0,2}|今天|明天|后天|昨天|前天|周末|周[一二三四五六日天]|下周|下个月|这个月|早上|中午|晚上|下午)")
private val PROMISE_RE = Regex("(说好|答应|约好|保证|改天|下次|一定|我会|我会去|我请)")
private val ACTION_RE = Regex("(见面|见个面|出来|一起|接你|送你|打电话|视频|吃饭|看电影|喝一杯|喝咖啡)")
internal val QUESTION_RE = Regex("[?？]|(吗|呢|怎么|为什么|什么时候|哪|多少)\\s*$")

object EvidenceAnalyzer {

    fun split(input: BriefInput): EvidenceTriad {
        val facts = LinkedHashSet<String>()
        input.turns.filter { it.speaker != Speaker.SYSTEM }.forEach { turn ->
            val t = turn.text.trim()
            val who = if (turn.speaker == Speaker.ME) "你" else "对方"
            if (TIME_RE.containsMatchIn(t)) {
                facts.add("${who}提到时间：${quote(t, 30)}")
            } else if (PROMISE_RE.containsMatchIn(t)) {
                facts.add("${who}有承诺或约定：${quote(t, 30)}")
            } else if (ACTION_RE.containsMatchIn(t)) {
                facts.add("${who}提到具体行动：${quote(t, 30)}")
            }
        }
        input.keyEvents.forEach { facts.add("你提供的经过：${it.text}${it.whenLabel?.let { w -> "（${w}）" } ?: ""}") }

        val inferences = ArrayList<String>()
        val otherText = input.turns.filter { it.speaker == Speaker.OTHER }.joinToString("\n") { it.text }
        if (Regex("^(嗯|哦|好|行|随便|都行|再说吧)[。.!！]?$").containsMatchIn(otherText.trim())) {
            inferences.add("回复只剩单字，可能是没空，也可能是降温——需要下一轮才能分辨（推测）")
        }
        if (otherText.contains("你忙") || otherText.contains("不打扰")) {
            inferences.add("「你忙吧」常是试探被在意程度，不一定是真的让你别回（推测）")
        }
        val asks = input.turns.count { it.speaker == Speaker.OTHER && QUESTION_RE.containsMatchIn(it.text) }
        if (asks >= 1) {
            inferences.add("对方主动发问，说明还愿意维持对话（推测）")
        }
        val lengths = input.turns.filter { it.speaker != Speaker.SYSTEM }.map { it.text.length }
        if (lengths.size >= 4) {
            val head = lengths.take(lengths.size / 2).average()
            val tail = lengths.takeLast(lengths.size / 2).average()
            if (head > 10 && tail < head * 0.6) inferences.add("后半段消息明显变短，热度在下降（推测）")
        }
        input.partner.relationshipNote.takeIf { it.isNotBlank() }?.let {
            inferences.add("你给出的关系背景：${quote(it, 40)}（你提供，不是原文事实）")
        }

        val unknowns = LinkedHashSet<String>()
        if (input.stage == Stage.UNKNOWN) unknowns.add("你们现在的关系阶段（刚认识 / 暧昧 / 约会 / 稳定）")
        if (input.turns.none { ACTION_RE.containsMatchIn(it.text) }) unknowns.add("对方最近有没有主动提出见面或具体安排")
        if (input.turns.count { it.speaker == Speaker.OTHER } < 3) unknowns.add("对方在更长一段时间的回复节奏（现在样本太少）")
        unknowns.add("对方最近的现实压力（工作、家庭、健康），这会改变对短回复的解释")
        if (input.partner.relationshipNote.isBlank()) unknowns.add("你们认识多久、见过几次、有没有共同社交圈")

        return EvidenceTriad(
            facts = facts.take(5).toList(),
            inferences = inferences.take(4),
            unknowns = unknowns.take(4).toList(),
        )
    }

    private fun quote(text: String, max: Int): String =
        if (text.length <= max) "「${text}」" else "「${text.take(max)}…」"
}

object ReciprocityAnalyzer {

    fun read(input: BriefInput): InterestRead {
        val chat = input.turns.filter { it.speaker != Speaker.SYSTEM }.takeLast(12)
        if (chat.isEmpty()) {
            return InterestRead(
                reciprocity = 0.5,
                signals = listOf("还没有可比较的聊天样本"),
                risks = listOf("没有样本时不要下判断"),
                opportunityCost = "先补三条真实对话，再谈投入是否失衡",
            )
        }
        val me = chat.count { it.speaker == Speaker.ME }
        val other = chat.count { it.speaker == Speaker.OTHER }
        val meLen = chat.filter { it.speaker == Speaker.ME }.map { it.text.length }.average().let { if (it.isNaN()) 0.0 else it }
        val otherLen = chat.filter { it.speaker == Speaker.OTHER }.map { it.text.length }.average().let { if (it.isNaN()) 0.0 else it }
        val otherQuestions = chat.count { it.speaker == Speaker.OTHER && QUESTION_RE.containsMatchIn(it.text) }
        val lastSpeaker = chat.last().speaker

        var score = 0.5
        val total = (me + other).toDouble().coerceAtLeast(1.0)
        score += ((other - me) / total) * 0.2
        if (otherLen > 0 && meLen > 0) {
            val ratio = (otherLen / meLen).coerceIn(0.2, 5.0)
            score += if (ratio in 0.7..1.4) 0.1 else if (ratio < 0.5 || ratio > 2.2) -0.12 else 0.0
        }
        score += otherQuestions.coerceAtMost(3) * 0.04
        if (lastSpeaker == Speaker.ME) score -= 0.08
        score = score.coerceIn(0.0, 1.0)

        val signals = ArrayList<String>()
        signals.add("最近 ${chat.size} 条里：你 ${me} 条 / 对方 ${other} 条")
        signals.add("平均长度：你 ${meLen.round1()} 字 / 对方 ${otherLen.round1()} 字")
        if (otherQuestions > 0) signals.add("对方主动发问 ${otherQuestions} 次")
        signals.add(if (lastSpeaker == Speaker.ME) "最后一条是你发的，球在对方那边" else "最后一条是对方发的，你可以选择回或不回")

        val risks = ArrayList<String>()
        if (me >= other * 2 && me >= 4) risks.add("发起与追问明显偏向你，继续加码容易变成单方面投入")
        if (otherLen > 0 && meLen > otherLen * 2.2) risks.add("你的消息长度远高于对方，信息密度不对等")
        if (otherQuestions == 0) risks.add("对方没有主动了解你，互惠证据不足")
        if (risks.isEmpty()) risks.add("暂时没有明显的投入失衡信号")

        val cost = when {
            score < 0.4 -> "继续按现在的强度投入，代价是你的时间和情绪；先把投入降一级，用一次具体邀约测试回应"
            score < 0.65 -> "可以再观察一个周期（约一周），只推进一件小事，不叠加新的期待"
            else -> "互惠基础尚可，代价主要在机会成本：确认对方是否也把这段关系当优先项"
        }
        return InterestRead(score, signals, risks, cost)
    }

    private fun Double.round1(): String = String.format("%.1f", this)
}

private val CRISIS_WORDS = listOf(
    "家暴", "打我", "动手", "威胁", "杀了", "弄死", "自杀", "不想活", "跟踪", "强迫", "下药",
    "偷拍", "勒索", "报警", "人身安全", "囚禁", "限制人身",
)
private val CONTROL_WORDS = listOf(
    "PUA", "服从测试", "煤气灯", "冷暴力", "贬低", "打压", "查手机", "定位", "监控", "孤立",
    "情绪勒索", "不给我就", "分手威胁",
)

object SafetyAnalyzer {

    fun scan(input: BriefInput): List<SafetyFlag> {
        val text = input.turns.joinToString("\n") { it.text } + "\n" + input.notes
        val flags = ArrayList<SafetyFlag>()
        if (CRISIS_WORDS.any { text.contains(it) }) {
            flags.add(
                SafetyFlag(
                    "人身安全",
                    "先确认你现在是否安全：有没有受伤、是否被限制自由、是否有人在身边。优先联系可信的亲友或当地紧急服务，保留证据（聊天、录音、就医记录）。这一轮不讨论话术和策略。",
                )
            )
        }
        if (CONTROL_WORDS.any { text.contains(it) }) {
            flags.add(
                SafetyFlag(
                    "操控模式",
                    "这段互动里出现了控制或贬低的信号。先把它命名为模式而不是「他/她只是不会表达」，再决定是否继续；不建议用话术去「赢」一场控制。",
                )
            )
        }
        if (input.user.feelingIntensity != null && input.user.feelingIntensity >= 8) {
            flags.add(
                SafetyFlag(
                    "情绪过载",
                    "你现在情绪强度很高。先不做重大决定、不发长文；把要说的话写下来，隔 24 小时再决定发不发。",
                )
            )
        }
        return flags
    }
}
