# 架构

## 两份遗产，各拿一半

| 需要的能力 | 来自 | 在本仓库的落点 |
| --- | --- | --- |
| 读屏、消息归一化、行为判定、悬浮窗、一键填入 | Jarvis | `adapter-jarvis/` 只做映射；客户端代码不复制 |
| 场景/关系阶段词汇（R01..R23、talking/dating…） | Jarvis | `core/brief/Models.kt` 的 `Stage`、`kb/routes.json` 的 `behaviours` |
| 五步分析顺序、证据分级、安全边界 | 狗头军师 | `core/brief/BriefBuilder.kt`、`core/prompt/PromptCompiler.kt` |
| 43 篇知识/话术 | 狗头军师 | `corpus/`（原文）+ `kb/index.jsonl`（分块索引） |
| 长期档案的字段、限额、撤销 | 狗头军师 `memory_store.py` | `core/memory/JunshiMemory.kt` |
| 「不自动发送」「只读开放内容」 | Jarvis | 写进提示词硬规则与 `NOTICE` |

## 模块

```
core/
  kb/
    Models.kt         Manifest / DocMeta / Route / ChunkHit / PackedContext / Citation
    Tokenizer.kt      中文 bigram + 英文词 + 数字；停用词
    Bm25.kt           倒排索引 + Okapi BM25（k1=1.2, b=0.75）
    KnowledgeBase.kt  route() / search() / pack() / behaviourRoute()
  brief/
    Models.kt         Turn / Stage / Goal / StrategyBrief 等强类型
    Analyzers.kt      情绪、事实三分、互惠、安全
    BriefBuilder.kt   五步编排；安全短路
  memory/
    JunshiMemory.kt   同意门控、scope 限额、追加台账、原子写
  prompt/
    PromptCompiler.kt 中文系统提示词 + 输出契约 + 容错解析
  ChatText.kt         纯文本转录格式（`我: …` / `me\t…`）
  Junshi.kt           门面 + 行为提示
cli/                  brief / pack / prompt / kb / memory
adapter-jarvis/       Jarvis 词汇镜像 + backgroundFor() / hintFor()
```

`core` 只依赖 Kotlin 标准库与 `kotlinx-serialization-json`，没有 Android、没有网络、没有数据库驱动。Android 端可以直接把 `core` 作为纯 JVM/Kotlin 模块引入（`minSdk 30` 所需 API 都可用）。

## 数据流

```
BriefInput(turns, stage, goal, profiles, notes)
   │
   ├─ EmotionAnalyzer   → EmotionRead
   ├─ EvidenceAnalyzer  → EvidenceTriad（facts / inferences / unknowns）
   ├─ ReciprocityAnalyzer → InterestRead（0..1 + 信号 + 风险 + 机会成本）
   ├─ SafetyAnalyzer    → List<SafetyFlag>
   │
   ├─ kb.route(query)   → 1–3 个场景
   ├─ kb.pack(query, budget, sceneHint)
   │     先取路由文件的命中段，再取全局 BM25；整段装填，超预算在句号处收尾
   │
   └─ BriefBuilder → StrategyBrief
         ├─ CLI: Render.brief() 五步纯文本
         ├─ Jarvis: JunshiBridge.backgroundFor() → ChatContext.background
         └─ LLM: PromptCompiler.SYSTEM + userPrompt() → JSON replies
```

## 关键算法

### 分词

中文按连续 CJK 串切 bigram：「不回消息」→ 回消 / 消息。这样查询「消息回得很慢」也能命中。英文按 `[a-z0-9_-]+` 小写化；数字保留。停用词只过滤单字虚词与英文功能词，不误伤 bigram。

### 检索

- 每个 chunk 的 tf 里额外加一次标题与 tags 的词，让「依恋」这类查询能稳定召回整篇。
- `pack()` 先给路由命中的文档 1.3 倍加权，其余按 BM25；同一段只出现一次。
- 预算按「整段」装填；只有第一段允许在句号处截断并补省略号，保证不超预算，也不会把半句话当成原文引用。

### 互惠

最近 12 条：条数比（±0.2）、平均长度比（同量级 +0.1，过度失衡 −0.12）、对方主动提问（每次 +0.04）、最后一条是谁（是「我」则 −0.08），基准 0.5，夹到 `[0,1]`。输出同时给「为什么」，用户可以用事实反驳。

### 安全短路

命中人身安全词（家暴、威胁、跟踪、强迫、自伤、勒索…）或操控词（服从测试、煤气灯、贬低、查手机…）或自评情绪 ≥8 时：

- 仍是完整的 `StrategyBrief`，但 `recommendation.pressure = "safety"`；
- 不生成「会撩」版本；
- 行动建议换成「确认安全 → 联系可信支持/紧急服务」；
- 提示词里明确要求「这一轮不输出撩人话术」。

## 扩展点

- **加知识**：放进 `corpus/`，在 `tools/kb_config.py` 的 `DOCS` 注册 id/tags/strength，重跑 `build_kb.py`。
- **加场景**：`ROUTES` 加一条（keywords + 1–3 个 doc），重跑。
- **加行为**：`BEHAVIOURS` 加 `R24` 之类，Jarvis 侧出现新行为编号时直接可用。
- **换检索**：`KnowledgeBase.search()` 是唯一入口；换成向量检索只需保持 `List<ChunkHit>` 返回。
- **接别的客户端**：实现一个 `Bridge`，把消息序列映射成 `List<Turn>` 即可，`core` 不关心来源。

## 为什么不用向量

语料 12 万字，最大 4 个 chunk 进预算；目标环境是手机和飞行模式。BM25 的冷启动与内存成本都可以忽略，且结果可解释（能指出命中的词）。语义改写的短板由场景路由补偿——军师原文档本来就规定「每次只读 1–3 份」。
