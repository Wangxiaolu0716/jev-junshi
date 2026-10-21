package io.junshi.desktop

import io.junshi.core.ChatText
import io.junshi.core.Junshi
import io.junshi.core.brief.BriefInput
import io.junshi.core.brief.Goal
import io.junshi.core.brief.PartnerProfile
import io.junshi.core.brief.Stage
import io.junshi.core.kb.KnowledgeBase
import io.junshi.core.render.BriefRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop shell for the strategist core.
 *
 * Paste (or drop) a transcript, pick the stage and the goal, press 生成.
 * Everything runs locally against the bundled corpus; nothing is uploaded and
 * nothing is sent on the user's behalf.
 */
fun main(args: Array<String>) {
    System.setProperty("sun.java2d.uiScale.enabled", "true")
    val preload = args.indexOf("--chat").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val uiFont = Font("Microsoft YaHei UI", Font.PLAIN, 13)
    UIManager.put("Label.font", uiFont)
    UIManager.put("Button.font", uiFont)
    UIManager.put("ComboBox.font", uiFont)
    UIManager.put("TextArea.font", uiFont)
    SwingUtilities.invokeLater { JunshiWindow(preload).open() }
}

private const val SAMPLE = """我: 这周末有空吗？想请你吃那家火锅
我: 上次你说想试试
对方: 嗯
我: 那周五还是周六？
对方: 再说吧"""

private class JunshiWindow(preload: String?) {

    private val junshi: Junshi by lazy { Junshi(KnowledgeBase.fromClasspath()) }
    private val input = JTextArea(preload?.let { runCatching { File(it).readText() }.getOrNull() } ?: "")
    private val output = JTextArea()
    private val status = JLabel("就绪")
    private val stageBox = JComboBox(STAGE_LABELS.map { it.second }.toTypedArray())
    private val goalBox = JComboBox(GOAL_LABELS.map { it.second }.toTypedArray())
    private val generate = JButton("生成简报")

    fun open() {
        val frame = JFrame("军师 · Jev Junshi")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = BorderLayout()

        input.lineWrap = true
        input.wrapStyleWord = true
        input.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
        input.toolTipText = "格式：「我: …」「对方: …」，一行一条。也可以把 .txt 拖进来。"
        output.isEditable = false
        output.lineWrap = true
        output.wrapStyleWord = true
        output.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
        output.text = "左边粘贴聊天记录，右下角选阶段和目标，然后点「生成简报」。\n\n所有判断都在本地完成，不会上传。"

        val left = JPanel(BorderLayout()).apply {
            add(header("聊天原文"), BorderLayout.NORTH)
            add(JScrollPane(input), BorderLayout.CENTER)
        }
        val right = JPanel(BorderLayout()).apply {
            add(header("军师简报"), BorderLayout.NORTH)
            add(JScrollPane(output), BorderLayout.CENTER)
        }
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right).apply {
            resizeWeight = 0.45
            border = BorderFactory.createEmptyBorder()
        }
        frame.add(split, BorderLayout.CENTER)
        frame.add(toolbar(frame), BorderLayout.NORTH)
        frame.add(statusBar(), BorderLayout.SOUTH)

        generate.addActionListener { runBrief() }
        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx), "generate")
        input.actionMap.put("generate", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = runBrief()
        })

        frame.minimumSize = Dimension(860, 560)
        frame.setSize(1020, 720)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        if (input.text.isBlank()) {
            input.text = SAMPLE
            input.caretPosition = 0
        }
        status.text = "知识库已就绪：${runCatching { junshi.kb.docs.size }.getOrDefault(0)} 篇 / " +
            "${runCatching { junshi.kb.chunkCount }.getOrDefault(0)} 段 · Ctrl+Enter 生成"
    }

    private fun header(text: String) = JLabel("  $text").apply {
        font = Font("Microsoft YaHei UI", Font.BOLD, 13)
        border = BorderFactory.createEmptyBorder(8, 6, 8, 6)
    }

    private fun toolbar(frame: JFrame): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(10, 12, 6, 12)
        add(JLabel("阶段 "))
        add(stageBox)
        add(Box.createHorizontalStrut(12))
        add(JLabel("目标 "))
        add(goalBox)
        add(Box.createHorizontalStrut(16))
        add(generate)
        add(Box.createHorizontalStrut(8))
        add(JButton("打开文件").apply {
            addActionListener {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("文本 (*.txt, *.md)", "txt", "md")
                }
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    input.text = runCatching { chooser.selectedFile.readText() }
                        .getOrElse { status.text = "读取失败：${it.message}"; "" }
                }
            }
        })
        add(Box.createHorizontalStrut(8))
        add(JButton("复制简报").apply {
            addActionListener {
                if (output.text.isBlank()) return@addActionListener
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(output.text), null)
                status.text = "已复制到剪贴板"
            }
        })
        add(Box.createHorizontalGlue())
    }

    private fun statusBar() = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(4, 14, 8, 14)
        add(status, BorderLayout.WEST)
    }

    private fun runBrief() {
        val raw = input.text.trim()
        if (raw.isEmpty()) {
            status.text = "先在左边粘贴聊天记录"
            return
        }
        val stage = STAGE_LABELS[stageBox.selectedIndex].first
        val goal = GOAL_LABELS[goalBox.selectedIndex].first
        generate.isEnabled = false
        status.text = "正在分析…"
        val started = System.currentTimeMillis()
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                val turns = ChatText.parse(raw)
                require(turns.isNotEmpty()) { "没有识别到消息" }
                val brief = junshi.brief(
                    BriefInput(
                        turns = turns,
                        stage = stage,
                        goal = goal,
                        partner = PartnerProfile(code = "对方"),
                    )
                )
                return BriefRenderer.brief(brief)
            }

            override fun done() {
                generate.isEnabled = true
                try {
                    output.text = get()
                    output.caretPosition = 0
                    status.text = "完成 · ${System.currentTimeMillis() - started} ms · ${output.text.length} 字"
                } catch (e: Exception) {
                    val cause = (e.cause ?: e).message ?: e.toString()
                    output.text = "无法生成：$cause\n\n请检查格式，每行写成「我: …」或「对方: …」。"
                    status.text = "格式错误"
                }
            }
        }.execute()
    }

    companion object {
        private val STAGE_LABELS = listOf(
            Stage.UNKNOWN to "还不确定",
            Stage.JUST_CONNECTED to "刚认识",
            Stage.TALKING to "暧昧期",
            Stage.DATING to "约会中",
            Stage.ESTABLISHED to "稳定伴侣",
            Stage.EX_FRIENDS to "前任·仍联系",
            Stage.EX_DISTANT to "前任·几乎不联系",
        )
        private val GOAL_LABELS = listOf(
            Goal.ADVANCE to "推进",
            Goal.CONFIRM to "确认关系",
            Goal.REPAIR to "修复",
            Goal.COMPARE to "比较选择",
            Goal.EXIT to "退出",
        )
    }
}
