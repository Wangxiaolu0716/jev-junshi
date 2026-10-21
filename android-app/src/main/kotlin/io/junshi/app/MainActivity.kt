package io.junshi.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import io.junshi.core.ChatText
import io.junshi.core.Junshi
import io.junshi.core.brief.BriefInput
import io.junshi.core.brief.Goal
import io.junshi.core.brief.PartnerProfile
import io.junshi.core.brief.Stage
import io.junshi.core.kb.KnowledgeBase
import io.junshi.core.render.BriefRenderer
import java.io.File

/**
 * Phone shell for the strategist core.
 *
 * Paste a transcript, pick stage and goal, read the five steps. The corpus is
 * bundled: no network permission is requested, and nothing leaves the device.
 */
class MainActivity : Activity() {

    private val stages = listOf(
        Stage.UNKNOWN to "还不确定",
        Stage.JUST_CONNECTED to "刚认识",
        Stage.TALKING to "暧昧期",
        Stage.DATING to "约会中",
        Stage.ESTABLISHED to "稳定伴侣",
        Stage.EX_FRIENDS to "前任·仍联系",
        Stage.EX_DISTANT to "前任·几乎不联系",
    )
    private val goals = listOf(
        Goal.ADVANCE to "推进",
        Goal.CONFIRM to "确认关系",
        Goal.REPAIR to "修复",
        Goal.COMPARE to "比较选择",
        Goal.EXIT to "退出",
    )

    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var stageSpinner: Spinner
    private lateinit var goalSpinner: Spinner
    private var kbInstance: KnowledgeBase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#111827")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7F7F5"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "军师"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
        })
        root.addView(TextView(this).apply {
            text = "离线 · 本地知识库 · 不发送任何消息"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(2), 0, dp(10))
        })

        val picker = pickerRow("阶段", stages.map { it.second }, goals.map { it.second })
        root.addView(picker)
        stageSpinner = picker.getChildAt(1) as Spinner
        goalSpinner = picker.getChildAt(3) as Spinner

        input = EditText(this).apply {
            hint = "每行一条：「我: …」或「对方: …」"
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            minLines = 5
            setTextColor(Color.parseColor("#111827"))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.1f))

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            addView(Button(this@MainActivity).apply {
                text = "生成简报"
                setOnClickListener { runBrief() }
            })
            addView(Button(this@MainActivity).apply {
                text = "清空"
                setOnClickListener {
                    input.setText("")
                    output.text = ""
                    status.text = ""
                }
            })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        })

        output = TextView(this).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#1F2937"))
            setTextIsSelectable(true)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            text = "粘贴聊天记录，选好阶段和目标，点「生成简报」。\n\n全部在手机上算，飞行模式也能用。"
        }
        root.addView(ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(output)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f))

        status = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(status)

        setContentView(root)
        status.text = "知识库：${kb().docs.size} 篇 / ${kb().chunkCount} 段"
    }

    /** One row holding both spinners; ids are used to find them back. */
    private fun pickerRow(stageLabel: String, stageLabels: List<String>, goalLabels: List<String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        row.addView(TextView(this).apply { text = stageLabel; textSize = 12f; setTextColor(Color.parseColor("#6B7280")) })
        row.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, stageLabels)
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "目标"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(dp(12), 0, 0, 0)
        })
        row.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, goalLabels)
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun runBrief() {
        val raw = input.text.toString().trim()
        if (raw.isEmpty()) {
            status.text = "先粘贴聊天记录"
            return
        }
        val stage = stages[stageSpinner.selectedItemPosition].first
        val goal = goals[goalSpinner.selectedItemPosition].first
        status.text = "正在分析…"
        Thread {
            val text = runCatching {
                val turns = ChatText.parse(raw)
                require(turns.isNotEmpty()) { "没有识别到消息" }
                BriefRenderer.brief(
                    Junshi(kb()).brief(
                        BriefInput(turns = turns, stage = stage, goal = goal, partner = PartnerProfile())
                    )
                )
            }.getOrElse { "无法生成：${it.message}\n\n请检查格式，每行写成「我: …」或「对方: …」。" }
            runOnUiThread {
                output.text = text
                status.text = if (text.startsWith("无法生成")) "格式错误" else "完成 · ${text.length} 字"
            }
        }.start()
    }

    /**
     * The index ships twice: as JVM resources (works on plain jars) and under
     * assets (always readable on Android). Resources first, assets as fallback.
     */
    private fun kb(): KnowledgeBase = kbInstance ?: synchronized(this) {
        kbInstance ?: runCatching { KnowledgeBase.fromClasspath() }.getOrElse {
            val dir = File(filesDir, "junshi-kb")
            if (!File(dir, "manifest.json").isFile) {
                dir.mkdirs()
                assets.list("junshi")?.forEach { name ->
                    assets.open("junshi/$name").use { input ->
                        File(dir, name).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            KnowledgeBase.fromDirectory(dir)
        }.also { kbInstance = it }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
