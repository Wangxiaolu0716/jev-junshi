# jev-junshi · 军师内核

把两个项目接成一个：**Jarvis 的手** + **狗头军师的脑**。

- [`jev-chat/jev-chat-jarvis`](https://github.com/jev-chat/jev-chat-jarvis)：安卓/桌面聊天副驾。无障碍读屏 → 判断对方在做什么 → 悬浮窗给 3 条候选回复 → 用户自己按发送。
- [`shengjidaguai-china/goutoujunshi`](https://github.com/shengjidaguai-china/goutoujunshi)：Codex 恋爱军师。先接住情绪、再拆事实、再算利益、最后给一个能执行的动作，背后是 43 篇、约 12 万字的知识与话术库，以及一套可撤销的长期关系档案。

Jarvis 缺的是一套稳定、可解释、离线可用的「怎么读这段关系」；军师缺的是一只伸进聊天窗口的手。这个仓库补上前者，并给出接入后者的桥。

```
聊天窗口 ──读屏/OCR──▶ 消息序列
                          │
                          ├─▶ junshi-core
                          │     ├─ 场景路由（18 条，来自 SKILL.md 的「按需加载」）
                          │     ├─ BM25 检索（256 段，零模型、离线）
                          │     ├─ 五步军师链（情绪→事实→利益→建议→行动）
                          │     └─ 长期档案（同意门控 + 撤销台账）
                          │
                          └─▶ 结构化简报 StrategyBrief
                                ├─ 悬浮窗展示（为什么这么建议）
                                └─ 提示词前缀 ──▶ LLM ──▶ 3 条候选回复
                                                          └─ 用户自己按发送
```

## 这个仓库里有什么

| 路径 | 作用 |
| --- | --- |
| `corpus/` | 军师原文，逐字导入（43 篇 Markdown，未改写） |
| `kb/` | 构建产物：`index.jsonl`（256 段）、`routes.json`（18 场景 + 25 行为路由）、`manifest.json` |
| `core/` | 纯 JVM Kotlin：分词、BM25、路由、预算打包、五步简报、记忆、提示词编译 |
| `cli/` | `junshi brief / pack / prompt / kb / memory`，全离线可跑 |
| `adapter-jarvis/` | 给 Jarvis 用的桥：`backgroundFor()` 与 `hintFor()` |
| `tools/` | `build_kb.py`、`check_kb.py`（Python 3.10+，只用标准库） |
| `fixtures/` | 四段真实形状的对话：降温、修复、安全分支、互惠良好 |

## 快速开始

```bash
# 1. 生成索引（一次即可；改了 corpus 后重跑）
python tools/build_kb.py
python tools/check_kb.py
# OK  43 docs / 256 chunks / 120814 chars; 18 routes, 25 behaviours

# 2. 构建并测试
./gradlew build

# 3. 看一份简报
./gradlew :cli:run --args="brief --chat fixtures/cold-chat.txt --goal advance"
```

`brief` 会输出五步，不会输出「保证让她回你」这种话：

```
【军师简报】

① 情绪落地
  感受：冷淡（强度 5/10）
  触发：对方说「再说吧」
  冲突：你想往前走一步，但当前的信号还不支持直接加码
  对方的短回复可能是没空，也可能是降温；现在还不能下结论。

② 事实拆分
  已知事实：
    - 你提到时间：「这周末有空吗？想请你吃那家火锅」
    - 你提到时间：「那周五还是周六？」
  关键未知：
    - 你们现在的关系阶段（刚认识 / 暧昧 / 约会 / 稳定）
    - 对方最近有没有主动提出见面或具体安排
    - 对方在更长一段时间的回复节奏（现在样本太少）
    - 对方最近的现实压力（工作、家庭、健康），这会改变对短回复的解释

③ 利益判断
  可见投入比：23%
  · 最近 6 条里：你 4 条 / 对方 2 条
  · 平均长度：你 9.0 字 / 对方 2.0 字
  · 最后一条是你发的，球在对方那边
  ! 发起与追问明显偏向你，继续加码容易变成单方面投入
  ! 你的消息长度远高于对方，信息密度不对等
  ! 对方没有主动了解你，互惠证据不足
  机会成本：继续按现在的强度投入，代价是你的时间和情绪；先把投入降一级，用一次具体邀约测试回应

④ 明确建议
  首选：先做一次低强度试探：给一个可拒绝的小邀请，然后停下
  1. 对方的情绪读数是「冷淡」，强度 5/10；这一轮先处理关系温度，不是先讲道理。
  2. 可见投入比 23%：最近 6 条里：你 4 条 / 对方 2 条。
  3. 原文能证明的是：你提到时间：「这周末有空吗？想请你吃那家火锅」。
  4. 互惠证据还不足以支撑直接加码，先做一次低强度试探。
  — 稳健：“这周有件小事想跟你一起，这周五你方便吗？”
  — 会撩：“有个问题只能当面问，这周五出来一下？”
  — 边界：“我想更了解你一点，找个时间聊聊。”
  （以上是例句，按你自己的口吻改，别照抄）

⑤ 行动收束
  现在：这条可以不回；如果要回，只回一句，不追加第二个问题。
  窗口：观察 3–7 天：对方是否主动发起、是否给出具体时间、是否兑现
  停止：两次具体邀请都没有替代安排，就停止加码，降到普通朋友频率
  回访信号：对方主动发问、提出时间、或兑现了一个小承诺——这三个出现任意一个，才可以推进

依据：
  - 实战话术编排器：从一句回复到后续分支 · 常用话术库（经验策略）
  - 巧妙接话技巧：让沟通更流畅的实用指南（经验策略）
  - 场景感、松弛感与社交校准：从接话到关系推进（经验策略）

还需要你回答（会影响建议）：
  ? 你们现在算哪个阶段：刚认识、暧昧、约会中，还是已经稳定？
  ? 对方最近有没有明确的现实压力（加班、考试、家里的事）？

注意：这是一份判断，不是读心；所有「推测」都欢迎你用事实纠正。
```

其余命令：

```bash
# 检索：哪几段知识和这句话有关
./gradlew :cli:run --args="kb search 已读不回 还要不要主动"

# 场景路由：这句话会打开哪两个文件
./gradlew :cli:run --args="kb route 他动手打我了"

# Jarvis 的行为编号为什么这样建议
./gradlew :cli:run --args="kb behaviour R11"

# 可直接塞给模型的上下文（1500 字预算内，整段引用，不截半句）
./gradlew :cli:run --args="pack --chat fixtures/warm-chat.txt"

# 完整提示词（军师规则 + 简报 + 知识摘录 + 档案摘要）
./gradlew :cli:run --args="prompt --chat fixtures/repair-chat.txt --goal repair"

# 长期档案：默认关闭，必须显式同意
./gradlew :cli:run --args="memory init --dir .junshi --reason 用户要求记住关系背景"
./gradlew :cli:run --args="memory put --dir .junshi --scope object --subject A --field stage --value 暧昧"
./gradlew :cli:run --args="memory list --dir .junshi --subject A"
./gradlew :cli:run --args="memory forget --dir .junshi --id <recordId>"
```

## 接到 Jarvis 里

见 [`docs/INTEGRATION-JARVIS.md`](docs/INTEGRATION-JARVIS.md)：三步，不动「绝不自动发送」的契约。

```kotlin
val bridge = JunshiBridge.bundled()          // 或 JunshiBridge.fromDirectory(kbDir)
val result = bridge.backgroundFor(
    lines = snapshot.messages.map { JunshiBridge.Line(fromMe = it.side == "me", text = it.text) },
    scene = "romance",
    relationshipId = selection.relationship?.id,   // talking / dating / ...
    goal = "advance",
)
// result.background 直接作为 ChatContext.background 的正文
// result.citations 显示在面板底部：依据了哪两篇、证据强度
```

## 设计选择（以及为什么不是另一种）

- **BM25 + 场景路由，不是向量检索**：语料 12 万字、目标设备是手机、要求飞行模式可用。无模型下载、无网络、冷启动毫秒级；代价是同义改写不如 embedding，所以路由表兜底（SKILL.md 本来就规定「默认只读 1–3 份」）。
- **规则先算出简报，再让 LLM 写话术**：LLM 只负责措辞，不负责判断。这样「可见投入比 23%」「对方最后一条是单字」是算出来的，可被用户用事实纠正，也更好测试。
- **记忆是 JSON 单文件 + 追加台账**：上游 `memory_store.py` 用 SQLite；这里换成原子写 JSON，Android 与 JVM 共用一套，删除目录即彻底删除。字段、scope 限额、来源与置信度保持同构。
- **安全分支短路**：出现家暴、威胁、跟踪、自伤词，五步链在第 2 步后直接转向安全建议，不输出撩人话术。

## 边界

- 不 hook、不改目标 App、不读它的数据库；接入端只使用系统无障碍/截屏。
- 绝不自动发送，也绝不点发送按钮；所有草稿都要人确认。
- 不诊断心理疾病；MBTI/星座/冷读只作为对话框架，不作结论。
- 不提供操控方案（服从测试、假时间压力、冷暴力、煤气灯、孤立、跟踪、性施压）。
- 长期档案默认关闭，需要明确同意；可查看、暂停、撤销、删除。

## 许可

MIT。两个上游也都是 MIT，原文与版权声明保留在 [`NOTICE`](NOTICE) 与 [`LICENSES/`](LICENSES/)。本项目不是任一上游的官方版本。
