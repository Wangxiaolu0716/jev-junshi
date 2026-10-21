# 接入 Jarvis（`jev-chat/jev-chat-jarvis`）

目标：让 Jarvis 的候选回复带上军师的判断与依据，同时**不改**它「用户自己按发送」的契约。

## 0. 前置

```bash
# 本仓库
python tools/build_kb.py && python tools/check_kb.py
./gradlew :adapter-jarvis:build
```

`adapter-jarvis` 只依赖 `core`，不 import Jarvis 的任何类；Jarvis 的 `Scene`/`relationship.id`/`R01..R23` 以字符串形式传入。

## 1. 把内核放进 Jarvis 工程

两种方式，选一种：

**A. 源码内联（最省事，推荐先这样验证）**

```bash
# 在 jev-chat-jarvis 仓库根
mkdir -p cn/app/src/main/java/io/junshi
cp -r ../jev-junshi/core/src/main/kotlin/io/junshi/core cn/app/src/main/java/io/junshi/
cp -r ../jev-junshi/adapter-jarvis/src/main/kotlin/io/junshi/adapter cn/app/src/main/java/io/junshi/
# 依赖
#   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
#   plugins { kotlin("plugin.serialization") }
```

**B. 发布成 Maven 本地包**

```bash
./gradlew :core:publishToMavenLocal :adapter-jarvis:publishToMavenLocal
# Jarvis: implementation("io.junshi:adapter-jarvis:0.1.0")
```

## 2. 把索引放进 APK

`core` 的 `resources.srcDir("../kb")` 会把三个文件打进 jar 根目录。Android 上更稳的做法是放进 assets：

```bash
mkdir -p cn/app/src/main/assets/junshi
cp kb/index.jsonl kb/routes.json kb/manifest.json cn/app/src/main/assets/junshi/
```

首次启动时解到 `filesDir/junshi-kb`（`KB` 只读，AssetManager 不能直接给 `File`）：

```kotlin
fun installBundledKb(context: Context): File {
    val dir = File(context.filesDir, "junshi-kb")
    if (File(dir, "manifest.json").isFile) return dir
    dir.mkdirs()
    listOf("index.jsonl", "routes.json", "manifest.json").forEach { name ->
        context.assets.open("junshi/$name").use { input ->
            File(dir, name).outputStream().use { input.copyTo(it) }
        }
    }
    return dir
}

val bridge by lazy { JunshiBridge.fromDirectory(installBundledKb(context)) }
```

> 索引约 300 KB，解压一次即可；`corpus/` 不必进 APK。

## 3. 替换 `ContextBuilder` 的 notes 匹配

Jarvis 现在是「alwaysOn 笔记 + 标题包含匹配」。保留用户笔记，把**匹配这一段**换成军师的检索：

```kotlin
// cn/app/.../core/kb/ContextBuilder.kt
fun build(context: Context, snapshot: ChatSnapshot, app: String, prefs: Prefs): ChatContext {
    val store = KbStore.get(context)
    val contact = store.findContact(snapshot.title ?: "", app)
    val history = if (prefs.contextEnabled && contact != null) historyFor(...) else emptyList()

    val userNotes = store.notes().filter { it.enabled && it.alwaysOn }      // 用户手写的照旧
    val junshi = runCatching {
        val lines = snapshot.messages.map { JunshiBridge.Line(fromMe = it.side == "me", text = it.text) }
        bridge.backgroundFor(
            lines = lines,
            scene = prefs.sceneId,                 // "romance" / "work" / ...
            relationshipId = contact?.relationship, // 已经是 talking/dating 就用上
            goal = prefs.goalId ?: "advance",
            budget = 900,
        )
    }.getOrNull()

    val extra = junshi?.background.orEmpty()
    val notes = buildList {
        addAll(userNotes)
        if (extra.isNotBlank()) add(Note(id = "junshi", title = "军师读到的局面", content = extra, alwaysOn = true))
    }
    return ChatContext(contact, history, notes)
}
```

`ChatContext.background()` 会把 note 拼成 `标题: 内容`，`ReplyClient` 已经把它前置到提示词里——**不需要改 `ReplyClient`**。

## 4. 在状态卡上显示「为什么」

Jarvis 的行为 id 只是判定结果。加一行依据，用户会更愿意相信，也更愿意纠错：

```kotlin
// 悬浮窗渲染 stance 卡片时
val why = bridge.hintFor(behavior.id)   // "军师：承认影响，聚焦具体行为……（依据：沟通冲突与修复）"
if (why != null) card.addFootnote(why)
```

`hintFor` 对 `R01..R23`、`loyalty_test`、`ultimatum` 都有映射；未知 id 返回 `null`，调用方不必兜底文案。

## 5. 安全分支

`StrategyBrief.safety` 非空时，`background` 已经把「安全优先」写进提示词，并禁止撩人话术。建议 Jarvis 侧同时：

```kotlin
if (brief.safety.isNotEmpty()) {
    panel.showSafetyCard(brief.safety.first().guidance)   // 置顶，遮住候选回复
}
```

## 6. 验收清单

- [ ] 飞行模式下 `bridge.backgroundFor(...)` 返回非空，`chars > 0`
- [ ] 冷启动到第一次出简报 < 200 ms（中端机）
- [ ] `background` 里不出现 `null`、空标题、半句话
- [ ] 安全样例（`fixtures/crisis-chat.txt`）不出现撩人候选
- [ ] 发送按钮的调用路径没有任何新增
- [ ] 用户手写笔记仍然全部注入（`alwaysOn` 不变）

## 已知限制

- Jarvis 的 `global/` 是英文场景，`core` 的提示词是中文。英文场景需要在 `PromptCompiler` 里加一份英文系统提示词（结构可以照搬，五步顺序不变）。
- `core` 不做 OCR/读屏，它只吃已经归一化的消息序列；消息切分错了，军师的判断也会错。这是 Jarvis 采集层要保证的。
- 索引是构建产物：改了 `corpus/` 必须重跑 `build_kb.py` 并重新打包 assets，CI 里用 `check_kb.py` 卡住。
