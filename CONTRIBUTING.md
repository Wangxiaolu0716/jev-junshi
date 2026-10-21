# 参与

## 本地循环

```bash
python tools/build_kb.py      # corpus/ → kb/
python tools/check_kb.py      # 结构校验，失败即退出 1
./gradlew build               # 编译 + 测试
```

改了 `corpus/` 或 `tools/kb_config.py` 之后，`kb/` 是构建产物，需要一起提交，否则 CI 的
`git diff --exit-code -- kb/` 会失败。

## 加一篇知识

1. 原文放进 `corpus/knowledge/` 或 `corpus/practical/`，**不要改写上游措辞**。
2. 在 `tools/kb_config.py` 的 `DOCS` 注册：id、路径、层级、证据强度、tags。
   证据强度必须诚实：没有元分析/纵向研究支撑的，写 `framework` 或 `experience`。
3. 如果需要它被某个场景打开，把 id 加进对应 `ROUTES[].docs`。
4. `python tools/build_kb.py && python tools/check_kb.py && ./gradlew build`。

## 加一个场景

`ROUTES` 加一条：`(id, label, keywords, docs, rationale)`。

- `keywords` 要覆盖用户真实会说的词，不要只写标签名；
- `docs` 1–3 篇，符合上游「默认只读 1–3 份」；
- `rationale` 用一句话说明为什么是这几篇，它会出现在面板上。

## 代码约定

- `core` 不依赖 Android，不联网，不加数据库驱动。
- 提示词里的硬规则（不读心、不操控、不替用户表态、安全优先）不允许为了「效果」删减。
- 新增判断必须能在没有 LLM 的情况下算出来，并补一条单测。
- 任何「推测」在输出里必须带「推测」二字。

## 安全审查清单（改提示词或话术时逐条过）

- [ ] 不会替用户表白、承诺或道歉
- [ ] 不提供服从测试、假时间压力、冷暴力、煤气灯、孤立、跟踪、性施压
- [ ] 家暴/威胁/跟踪/自伤词出现时，先安全后策略
- [ ] 对方明确拒绝时停止推进
- [ ] 不把 MBTI、星座、冷读当诊断
- [ ] 长期记忆仍需显式同意，撤销路径仍可用

## 提交

不要在 PR 里提交任何真实用户的聊天记录、姓名、手机号或长期档案。
`fixtures/` 只用自造样例。
