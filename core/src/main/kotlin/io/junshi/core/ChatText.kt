package io.junshi.core

import io.junshi.core.brief.Speaker
import io.junshi.core.brief.Turn
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Parses the plain-text transcript format used by the CLI, fixtures and the
 * Jarvis bridge:
 *
 * ```
 * 我: 在吗
 * 对方: 嗯
 * me\t在吗        (tab-separated, same meaning)
 * ```
 *
 * Unknown labels are an error rather than a guess: the strategist must know who
 * spoke before it can say anything about investment.
 */
object ChatText {

    private val prefixes = mapOf("我" to Speaker.ME, "me" to Speaker.ME, "self" to Speaker.ME,
        "对方" to Speaker.OTHER, "other" to Speaker.OTHER, "ta" to Speaker.OTHER, "她" to Speaker.OTHER, "他" to Speaker.OTHER)

    fun parse(text: String): List<Turn> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val sep = when {
                line.contains('\t') -> line.indexOf('\t')
                line.contains("：") -> line.indexOf("：")
                line.contains(":") -> line.indexOf(':')
                else -> -1
            }
            require(sep > 0) { "无法识别说话人，请写成「我: …」或「对方: …」：$line" }
            val who = line.substring(0, sep).trim().lowercase()
            val speaker = prefixes[who] ?: error("未知说话人「$who」：$line")
            Turn(speaker, line.substring(sep + 1).trim())
        }
        .toList()

    fun read(file: File): List<Turn> = parse(file.readText(StandardCharsets.UTF_8))

    fun render(turns: List<Turn>): String = turns.joinToString("\n") { turn ->
        val who = when (turn.speaker) {
            Speaker.ME -> "我"
            Speaker.OTHER -> "对方"
            Speaker.SYSTEM -> "系统"
        }
        "$who: ${turn.text}"
    }
}
