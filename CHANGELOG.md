# Changelog

## 0.1.0 — 2026-10-21

第一个可运行版本：把 `shengjidaguai-china/goutoujunshi` 的策略层与
`jev-chat/jev-chat-jarvis` 的客户端契约接成一个独立仓库。

### 新增

- `corpus/`：43 篇原文逐字导入（20 篇知识 + 23 篇实战），保留 MIT 与来源。
- `tools/build_kb.py`：按 `##` 标题切块、长节按段落再切，产出
  `kb/index.jsonl`（256 段）、`kb/routes.json`（18 条场景 + 25 个行为路由）、
  `kb/manifest.json`（证据强度、tags、统计）。
- `tools/check_kb.py`：校验 chunk 唯一性、路由引用、字符上限、doc 覆盖。
- `core/kb`：中文 bigram 分词、BM25、场景路由、按预算整段装填。
- `core/brief`：五步军师链（情绪 → 事实/推测/未知 → 互惠 → 建议 → 行动），
  含安全短路（人身安全 / 操控 / 情绪过载）。
- `core/memory`：同意门控、scope 限额（30/15/10/20/5）、200 字上限、
  追加台账（add/update/forget/evict/purge）、原子写 JSON。
- `core/prompt`：中文系统提示词（含硬规则与 JSON 契约）、容错解析。
- `cli`：`brief` / `pack` / `prompt` / `kb` / `memory`，全离线。
- `adapter-jarvis`：`backgroundFor()` 注入 `ChatContext.background`，
  `hintFor()` 给行为卡加依据。
- 测试：分词、检索排序、预算、五步链、安全分支、记忆撤销、提示词解析、
  Jarvis 映射。

### 设计决定

- 检索用 BM25 + 场景路由，不引入向量模型：12 万字、离线、手机端、可解释。
- 策略用规则先算、LLM 只写措辞：判断可被用户用事实纠正，也可单测。
- 记忆用单文件 JSON 而非 SQLite：Android/JVM 共用，删目录即彻底删除。

### 已知限制

- `global/` 的英文场景尚未提供英文系统提示词。
- 索引是构建产物，改 `corpus/` 后必须重跑 `build_kb.py`。
- 互惠评分基于最近 12 条可见消息，样本不足时只给区间，不给结论。
