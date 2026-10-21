# jev-junshi · strategist core

Two projects, fused: **Jarvis' hands** and **the strategist's brain**.

- [`jev-chat/jev-chat-jarvis`](https://github.com/jev-chat/jev-chat-jarvis) — an Android/desktop chat copilot: read the screen through accessibility, judge what the other person is doing, show three candidate replies, and let the human press send.
- [`shengjidaguai-china/goutoujunshi`](https://github.com/shengjidaguai-china/goutoujunshi) — a relationship strategist skill: hold the feeling first, then separate facts, then weigh interests, then give one executable step. 43 documents, ~120k characters, and a consent-gated memory schema.

Jarvis lacked a stable, explainable, offline answer to "what is actually going on in this chat". The strategist lacked a hand inside the chat window. This repository provides the former and the bridge to the latter.

```
chat window ──a11y/OCR──▶ message sequence
                              │
                              ├─▶ junshi-core
                              │     ├─ 18 scene routes  (from SKILL.md's "load on demand")
                              │     ├─ BM25 over 256 chunks (no model, offline)
                              │     ├─ five-step chain: emotion → facts → interests → advice → action
                              │     └─ memory: consent gate + undo ledger
                              │
                              └─▶ StrategyBrief
                                    ├─ panel: why this advice
                                    └─ prompt prefix ──▶ LLM ──▶ 3 drafts
                                                                  └─ the human presses send
```

## What is here

| Path | Role |
| --- | --- |
| `corpus/` | the strategist's prose, imported verbatim (43 Markdown files) |
| `kb/` | build output: `index.jsonl` (256 chunks), `routes.json` (18 scenes + 25 behaviours), `manifest.json` |
| `core/` | pure JVM Kotlin: tokenizer, BM25, routing, budget packing, the brief, memory, prompt compiler |
| `cli/` | `junshi brief / pack / prompt / kb / memory`, fully offline |
| `adapter-jarvis/` | `backgroundFor()` and `hintFor()` for Jarvis |
| `tools/` | `build_kb.py`, `check_kb.py` (Python 3.10+, stdlib only) |
| `fixtures/` | four transcripts: cooling, repair, safety branch, healthy reciprocity |

## Quick start

```bash
python tools/build_kb.py && python tools/check_kb.py
./gradlew build
./gradlew :cli:run --args="brief --chat fixtures/cold-chat.txt --goal advance"
```

Other commands:

```bash
./gradlew :cli:run --args="kb search 已读不回 还要不要主动"
./gradlew :cli:run --args="kb route 他动手打我了"
./gradlew :cli:run --args="kb behaviour R11"
./gradlew :cli:run --args="pack --chat fixtures/warm-chat.txt"
./gradlew :cli:run --args="prompt --chat fixtures/repair-chat.txt --goal repair"
./gradlew :cli:run --args="memory init --dir .junshi --reason 用户要求记住关系背景"
```

## Wiring it into Jarvis

See [`docs/INTEGRATION-JARVIS.md`](docs/INTEGRATION-JARVIS.md). Three steps, and the "never send automatically" contract stays untouched:

```kotlin
val bridge = JunshiBridge.bundled()
val result = bridge.backgroundFor(
    lines = snapshot.messages.map { JunshiBridge.Line(fromMe = it.side == "me", text = it.text) },
    scene = "romance",
    relationshipId = selection.relationship?.id,
    goal = "advance",
)
// result.background becomes the body of ChatContext.background
```

## Design choices

- **BM25 + scene routing, not embeddings**: 120k characters, a phone, airplane mode. No model download, no network, millisecond cold start, and every hit can be explained by the words it matched. The routing table covers the semantic gap, which is what the upstream skill already mandated ("read one to three documents").
- **Rules compute the brief; the LLM only writes wording**: "reciprocity 38%" and "the last message was one character" are computed and testable, and the user can correct them with facts.
- **Memory is one JSON file with an append-only ledger**: the upstream `memory_store.py` uses SQLite; here it is atomic-write JSON so Android and JVM share one implementation, and deleting the directory deletes everything.
- **Safety short-circuits**: threats, stalking, violence or self-harm switch the chain to safety guidance after step two, and no flirty draft is produced.

## Boundaries

- No hooking, no Xposed, no target-app database access; clients use accessibility and screenshots only.
- Never sends or taps send. Every draft is confirmed by a human.
- No diagnosis; MBTI, astrology and cold reading are conversation frames, never conclusions.
- No manipulation playbook: no compliance tests, fake deadlines, silent treatment, gaslighting, isolation, stalking or sexual pressure.
- Memory is off by default, needs explicit consent, and can be viewed, paused, revoked and deleted.

## License

MIT. Both upstreams are MIT; their texts and copyright notices are kept in [`NOTICE`](NOTICE) and [`LICENSES/`](LICENSES/). This is not an official release of either project.
