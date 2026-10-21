#!/usr/bin/env python3
"""Validate the generated knowledge index. Exit code 1 on any failure."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KB = ROOT / "kb"
MAX_CHUNK_CHARS = 4800


def fail(msg: str, errors: list[str]) -> None:
    errors.append(msg)


def main() -> int:
    errors: list[str] = []
    for name in ("index.jsonl", "routes.json", "manifest.json"):
        if not (KB / name).is_file():
            fail(f"missing kb/{name} (run python tools/build_kb.py)", errors)
    if errors:
        print("\n".join(errors))
        return 1

    manifest = json.loads((KB / "manifest.json").read_text(encoding="utf-8"))
    routes = json.loads((KB / "routes.json").read_text(encoding="utf-8"))
    chunks = [json.loads(line) for line in (KB / "index.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]

    doc_ids = {d["id"] for d in manifest["docs"]}
    if len(doc_ids) != len(manifest["docs"]):
        fail("duplicate doc ids in manifest", errors)
    if manifest["totals"]["chunks"] != len(chunks):
        fail(f"manifest says {manifest['totals']['chunks']} chunks, index has {len(chunks)}", errors)

    seen: set[str] = set()
    per_doc: dict[str, int] = {}
    for c in chunks:
        cid = c["id"]
        if cid in seen:
            fail(f"duplicate chunk id {cid}", errors)
        seen.add(cid)
        if c["doc"] not in doc_ids:
            fail(f"chunk {cid} references unknown doc {c['doc']}", errors)
        if not c["text"].strip():
            fail(f"chunk {cid} is empty", errors)
        if len(c["text"]) > MAX_CHUNK_CHARS:
            fail(f"chunk {cid} is {len(c['text'])} chars (> {MAX_CHUNK_CHARS})", errors)
        per_doc[c["doc"]] = per_doc.get(c["doc"], 0) + 1

    for d in manifest["docs"]:
        if per_doc.get(d["id"], 0) != d["chunks"]:
            fail(f"doc {d['id']} declares {d['chunks']} chunks, index has {per_doc.get(d['id'], 0)}", errors)
        if d["strength"] not in {"policy", "strong", "framework", "popular", "experience"}:
            fail(f"doc {d['id']} has unknown strength {d['strength']}", errors)

    route_ids = {r["id"] for r in routes["routes"]}
    if len(route_ids) != len(routes["routes"]):
        fail("duplicate route ids", errors)
    for r in routes["routes"]:
        for doc in r["docs"]:
            if doc not in doc_ids:
                fail(f"route {r['id']} references unknown doc {doc}", errors)
    for bid, b in routes["behaviours"].items():
        for doc in b["docs"]:
            if doc not in doc_ids:
                fail(f"behaviour {bid} references unknown doc {doc}", errors)

    if not any(r["id"] == "general" for r in routes["routes"]):
        fail("routes must include a 'general' fallback", errors)

    if errors:
        print(f"FAIL ({len(errors)} problems)")
        print("\n".join(f"  - {e}" for e in errors))
        return 1

    t = manifest["totals"]
    print(
        f"OK  {t['docs']} docs / {t['chunks']} chunks / {t['chars']} chars; "
        f"{len(routes['routes'])} routes, {len(routes['behaviours'])} behaviours"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
