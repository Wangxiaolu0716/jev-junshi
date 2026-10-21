package io.junshi.cli

import io.junshi.core.BehaviourHint
import io.junshi.core.ChatText
import io.junshi.core.Junshi
import io.junshi.core.brief.BriefInput
import io.junshi.core.brief.Goal
import io.junshi.core.brief.PartnerProfile
import io.junshi.core.brief.Stage
import io.junshi.core.brief.StrategyBrief
import io.junshi.core.brief.UserProfile
import io.junshi.core.kb.KnowledgeBase
import io.junshi.core.memory.Confidence
import io.junshi.core.memory.JunshiMemory
import io.junshi.core.memory.MemoryScope
import io.junshi.core.memory.SourceType
import java.io.File

/**
 * `junshi` — the strategist on the command line.
 *
 * Every command works offline against the bundled corpus. No command sends a
 * message anywhere: `prompt` prints the prompt, `brief` prints the reading, and
 * the user decides what to do with them.
 */
fun main(args: Array<String>) {
    forceUtf8()
    val opts = Args(args)
    try {
        when (opts.command) {
            "brief" -> brief(opts)
            "prompt" -> prompt(opts)
            "pack" -> pack(opts)
            "kb" -> kb(opts)
            "memory" -> memory(opts)
            "version", "--version" -> println("junshi 0.1.0")
            "help", "--help", "-h", "" -> usage()
            else -> {
                System.err.println("未知命令：${opts.command}")
                usage()
                kotlin.system.exitProcess(2)
            }
        }
    } catch (e: Exception) {
        System.err.println("参数错误：${e.message}")
        kotlin.system.exitProcess(2)
    }
}

/**
 * Windows consoles default to a legacy code page, which turns piped Chinese
 * output into mojibake. The CLI speaks UTF-8 everywhere; set
 * -Djunshi.utf8=false to keep the platform default.
 */
private fun forceUtf8() {
    if (System.getProperty("junshi.utf8") == "false") return
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
    System.setErr(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.err), true, "UTF-8"))
}

private fun usage() {
    println(
        """
        军师 · jev-junshi CLI

          brief   --chat FILE [--stage STAGE] [--goal GOAL] [--json] [--budget N]
          pack    --chat FILE [--budget N] [--scene ROUTE]
          prompt  --chat FILE [--stage STAGE] [--goal GOAL] [--memory DIR] [--subject NAME] [--details TEXT]
          kb      search <query> [--limit N] [--doc k01,p10] | route <text> | behaviour <ID> | docs
          memory  status|init|list|put|forget|purge --dir DIR [...]

        全局：--kb DIR（默认读取打包进 jar 的 kb/）

        STAGE: unknown|just_connected|talking|dating|established|ex_friends|ex_distant
        GOAL : advance|confirm|repair|compare|exit
        """.trimIndent()
    )
}

private fun loadKb(opts: Args): KnowledgeBase {
    val dir = opts.option("kb")
    return if (dir != null) KnowledgeBase.fromDirectory(File(dir)) else KnowledgeBase.fromClasspath()
}

private fun loadInput(opts: Args): BriefInput {
    val chatFile = opts.option("chat") ?: error("缺少 --chat FILE")
    val turns = ChatText.read(File(chatFile))
    require(turns.isNotEmpty()) { "聊天文件是空的：$chatFile" }
    return BriefInput(
        turns = turns,
        stage = Stage.fromId(opts.option("stage") ?: "unknown"),
        goal = Goal.fromId(opts.option("goal") ?: "advance"),
        user = UserProfile(feelingIntensity = opts.option("intensity")?.toIntOrNull()),
        partner = PartnerProfile(
            code = opts.option("partner") ?: "对方",
            mbti = opts.option("mbti"),
            relationshipNote = opts.option("note") ?: "",
        ),
        notes = opts.option("notes") ?: "",
    )
}

private fun brief(opts: Args) {
    val junshi = Junshi(loadKb(opts))
    val input = loadInput(opts)
    val width = opts.option("budget")?.toIntOrNull() ?: 900
    val b = junshi.brief(input, width)
    if (opts.flag("json")) {
        println(Json.out(b))
    } else {
        println(Render.brief(b))
    }
}

private fun pack(opts: Args) {
    val junshi = Junshi(loadKb(opts))
    val input = loadInput(opts)
    val query = ChatText.render(input.turns.takeLast(10)) + "\n" + input.notes
    val budget = opts.option("budget")?.toIntOrNull() ?: 1500
    val packed = junshi.pack(query, budget, opts.option("scene"))
    if (packed.isEmpty) {
        println("（没有匹配到知识片段）")
        return
    }
    println(packed.text)
    println()
    println("— 命中 ${packed.hits.size} 段 / ${packed.chars} 字，路由：${packed.routes.joinToString { it.route.label }}")
}

private fun prompt(opts: Args) {
    val junshi = Junshi(loadKb(opts))
    val input = loadInput(opts)
    val b = junshi.brief(input)
    val query = ChatText.render(input.turns.takeLast(10)) + "\n" + input.notes
    val packed = junshi.pack(query, 1200, b.routes.firstOrNull())
    var digest = ""
    val memDir = opts.option("memory")
    if (memDir != null) {
        val mem = JunshiMemory(File(memDir))
        digest = mem.digest(opts.option("subject") ?: input.partner.code)
    }
    println(junshi.prompt(b, input, packed, digest))
}

private fun kb(opts: Args) {
    val kb = loadKb(opts)
    when (val sub = opts.positional.getOrNull(1) ?: "docs") {
        "search" -> {
            val query = opts.positional.drop(2).joinToString(" ")
            require(query.isNotBlank()) { "用法：kb search <query>" }
            val limit = opts.option("limit")?.toIntOrNull() ?: 8
            val only = opts.option("doc")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            val hits = kb.search(query, limit, only)
            if (hits.isEmpty()) {
                println("（无匹配）")
                return
            }
            hits.forEachIndexed { i, h ->
                println("${i + 1}. [${"%.2f".format(h.score)}] ${h.doc.title} · ${h.chunk.heading.ifBlank { "导言" }}（${h.doc.strength.label}）")
                println("   ${h.chunk.text.replace("\n", " ").take(110)}…")
            }
        }
        "route" -> {
            val text = opts.positional.drop(2).joinToString(" ")
            require(text.isNotBlank()) { "用法：kb route <text>" }
            kb.route(text, 5).forEach { r ->
                println("${r.route.id}\t${r.route.label}\t${"%.1f".format(r.score)}\t命中: ${r.matched.joinToString("/")}")
            }
        }
        "behaviour" -> {
            val id = opts.positional.getOrNull(2) ?: error("用法：kb behaviour <R01..R23>")
            val hint: BehaviourHint = Junshi(kb).behaviourHint(id) ?: error("没有这个行为：$id")
            println("${hint.id} ${hint.label}")
            println("为什么：${hint.rationale}")
            hint.docs.forEach { println("  - ${it.title}（${it.strength.label}）${it.path}") }
            hint.chunks.forEach { println("  > ${it.chunk.text.replace("\n", " ").take(90)}…") }
        }
        "docs" -> {
            kb.docs.forEach { d ->
                println("${d.id}\t${d.layer}\t${d.strength.label}\t${d.chunks}段\t${d.title}")
            }
            println("共 ${kb.docs.size} 篇 / ${kb.chunkCount} 段（${kb.manifest.source}）")
        }
        else -> error("未知子命令：kb $sub")
    }
}

private fun memory(opts: Args) {
    val dir = opts.option("dir") ?: error("缺少 --dir DIR")
    val mem = JunshiMemory(File(dir))
    when (val sub = opts.positional.getOrNull(1) ?: "status") {
        "status" -> {
            val s = mem.status()
            println("同意：${if (s.consent) "已启用（${s.consentReason}）" else "未启用"}")
            println("记录：${s.records.size} 条；操作台账：${s.operations.size} 条")
            s.records.groupBy { it.scope }.forEach { (scope, list) ->
                println("  ${scope.name.lowercase()}: ${list.size}/${scope.cap}")
            }
        }
        "init" -> {
            val reason = opts.option("reason") ?: "用户明确要求保存关系档案，用于后续建议；可随时撤销"
            report(mem.enable(reason))
        }
        "pause" -> report(mem.pause())
        "list" -> {
            val subject = opts.option("subject") ?: "对方"
            mem.recall(subject, limit = opts.option("limit")?.toIntOrNull() ?: 20).forEach {
                println("${it.id}\t${it.scope.name.lowercase()}/${it.field}\t${it.value}\t[${it.sourceType.name.lowercase()}, ${it.confidence.name.lowercase()}]")
            }
        }
        "put" -> {
            val scope = MemoryScope.valueOf((opts.option("scope") ?: "object").uppercase())
            val source = SourceType.valueOf((opts.option("source") ?: "user_explicit").uppercase())
            val conf = Confidence.valueOf((opts.option("confidence") ?: "medium").uppercase())
            report(
                mem.put(
                    scope = scope,
                    subjectId = opts.option("subject") ?: "对方",
                    field = opts.option("field") ?: error("缺少 --field"),
                    value = opts.option("value") ?: error("缺少 --value"),
                    sourceType = source,
                    confidence = conf,
                    sourceRef = opts.option("ref") ?: "",
                )
            )
        }
        "forget" -> report(mem.forget(opts.option("id") ?: error("缺少 --id")))
        "purge" -> report(mem.purge())
        "export" -> println(mem.exportJson())
        else -> error("未知子命令：memory $sub")
    }
}

private fun report(r: io.junshi.core.memory.MemoryResult) {
    println("${if (r.ok) "OK" else "拒绝"} [${r.code}] ${r.message}")
}

/** Tiny option parser: `--k v`, `--flag`, and positionals. */
private class Args(argv: Array<String>) {
    val positional = ArrayList<String>()
    private val options = HashMap<String, String>()
    private val flags = HashSet<String>()

    val command: String

    init {
        var i = 0
        while (i < argv.size) {
            val a = argv[i]
            when {
                a == "--" -> {
                    positional.addAll(argv.drop(i + 1)); i = argv.size
                }
                a.startsWith("--") -> {
                    val key = a.removePrefix("--")
                    if (key.contains('=')) {
                        val (k, v) = key.split('=', limit = 2)
                        options[k] = v
                    } else if (i + 1 < argv.size && !argv[i + 1].startsWith("--")) {
                        options[key] = argv[i + 1]; i++
                    } else {
                        flags.add(key)
                    }
                }
                else -> positional.add(a)
            }
            i++
        }
        command = positional.firstOrNull() ?: ""
    }

    fun option(name: String): String? = options[name]
    fun flag(name: String): Boolean = flags.contains(name) || options[name] == "true"
}
