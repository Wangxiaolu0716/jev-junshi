#!/usr/bin/env python3
"""Build the Junshi knowledge index from the verbatim corpus.

Input   knowledge/**.md + tools/kb_config.py
Output  kb/index.jsonl   one JSON object per retrievable chunk
        kb/routes.json   scene -> documents routing (SKILL.md "按需加载")
        kb/manifest.json doc inventory, strengths, tags and totals

The prose is never rewritten. Metadata (doc id, layer, evidence strength,
tags) comes from kb_config.py; chunking only splits on markdown headings.

Usage:
    python tools/build_kb.py            # build
    python tools/build_kb.py --stamp X  # record upstream commit X
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from kb_config import BEHAVIOURS, DOCS, ROUTES  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CORPUS = ROOT / "corpus"
OUT = ROOT / "kb"

SCHEMA_VERSION = 1
MAX_CHUNK_CHARS = 2400
DEFAULT_STAMP = "goutoujunshi@6db7354"


def clean_block(text: str) -> str:
    lines = [line.rstrip() for line in text.splitlines()]
    out: list[str] = []
    blank = 0
    for line in lines:
        if not line:
            blank += 1
            if blank > 1:
                continue
        else:
            blank = 0
        out.append(line)
    return "\n".join(out).strip()


def split_sections(text: str) -> tuple[str, list[tuple[str, str]]]:
    """Return (heading, body) pairs: the preamble plus every '## ' section."""
    lines = text.splitlines()
    title = ""
    body_start = 0
    for i, line in enumerate(lines):
        if line.startswith("# ") and not line.startswith("## "):
            title = line[2:].strip()
            body_start = i + 1
            break

    body = lines[body_start:]
    sections: list[tuple[str, str]] = []
    heading = ""
    buf: list[str] = []
    for line in body:
        if re.match(r"^##\s+", line) or line.startswith("## "):
            if "".join(buf).strip():
                sections.append((heading, clean_block("\n".join(buf))))
            heading = re.sub(r"^##\s+", "", line).strip()
            buf = []
        else:
            buf.append(line)
    if "".join(buf).strip():
        sections.append((heading, clean_block("\n".join(buf))))
    return title, sections


def split_long(heading: str, body: str) -> list[tuple[str, str]]:
    """Break an over-long section on paragraph boundaries, never mid-sentence."""
    if len(body) <= MAX_CHUNK_CHARS:
        return [(heading, body)]
    parts: list[tuple[str, str]] = []
    current: list[str] = []
    size = 0
    for para in body.split("\n\n"):
        if size and size + len(para) > MAX_CHUNK_CHARS:
            parts.append((heading if not parts else f"{heading}（续）", "\n\n".join(current).strip()))
            current, size = [], 0
        current.append(para)
        size += len(para) + 2
        # A single huge paragraph is cut at sentence ends as a last resort.
        while len(current) == 1 and len(current[0]) > MAX_CHUNK_CHARS * 2:
            block = current[0]
            cut = block.rfind("。", 0, MAX_CHUNK_CHARS)
            if cut <= 0:
                break
            parts.append((heading if not parts else f"{heading}（续）", block[: cut + 1].strip()))
            current = [block[cut + 1 :].strip()]
    if "".join(current).strip():
        parts.append((heading if not parts else f"{heading}（续）", "\n\n".join(current).strip()))
    return parts


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--stamp", default=DEFAULT_STAMP, help="upstream source stamp recorded in the manifest")
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    missing = [rel for rel, *_ in DOCS.values() if not (CORPUS / rel).exists()]
    if missing:
        print("missing corpus files:\n  " + "\n  ".join(missing), file=sys.stderr)
        return 1

    chunks: list[dict] = []
    docs: list[dict] = []
    for doc_id, (rel, layer, strength, tags) in DOCS.items():
        path = CORPUS / rel
        raw = path.read_text(encoding="utf-8")
        title, sections = split_sections(raw)
        if not title:
            title = path.stem
        doc_chunks: list[str] = []
        n = 0
        for heading, body in sections:
            for final_heading, final_body in split_long(heading, body):
                if not final_body:
                    continue
                n += 1
                chunk_id = f"{doc_id}#{n}"
                text = (final_heading + "\n\n" + final_body).strip() if final_heading else final_body
                chunks.append(
                    {
                        "id": chunk_id,
                        "doc": doc_id,
                        "layer": layer,
                        "title": title,
                        "heading": final_heading,
                        "text": text,
                        "chars": len(text),
                    }
                )
                doc_chunks.append(chunk_id)
        docs.append(
            {
                "id": doc_id,
                "path": f"corpus/{rel}",
                "title": title,
                "layer": layer,
                "strength": strength,
                "tags": tags,
                "chunks": len(doc_chunks),
                "chars": sum(len(c["text"]) for c in chunks if c["doc"] == doc_id),
            }
        )

    index_path = OUT / "index.jsonl"
    with index_path.open("w", encoding="utf-8", newline="\n") as fh:
        for chunk in chunks:
            fh.write(json.dumps(chunk, ensure_ascii=False, separators=(",", ":")) + "\n")

    routes_path = OUT / "routes.json"
    routes_payload = {
        "schema": SCHEMA_VERSION,
        "source": args.stamp,
        "routes": [
            {"id": route_id, "label": label, "keywords": keywords, "docs": doc_ids, "rationale": rationale}
            for route_id, label, keywords, doc_ids, rationale in ROUTES
        ],
        "behaviours": {
            key: {"label": label, "docs": doc_ids, "rationale": rationale}
            for key, (label, doc_ids, rationale) in BEHAVIOURS.items()
        },
    }
    routes_path.write_text(
        json.dumps(routes_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
    )

    manifest_path = OUT / "manifest.json"
    manifest = {
        "schema": SCHEMA_VERSION,
        "source": args.stamp,
        "upstreams": [
            {"repo": "shengjidaguai-china/goutoujunshi", "role": "corpus + strategist chain", "license": "MIT"},
            {"repo": "jev-chat/jev-chat-jarvis", "role": "capture / judge / draft client", "license": "MIT"},
        ],
        "maxChunkChars": MAX_CHUNK_CHARS,
        "docs": docs,
        "totals": {
            "docs": len(docs),
            "chunks": len(chunks),
            "chars": sum(d["chars"] for d in docs),
            "knowledgeDocs": sum(1 for d in docs if d["layer"] == "knowledge"),
            "practicalDocs": sum(1 for d in docs if d["layer"] == "practical"),
        },
    }
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
    )

    t = manifest["totals"]
    print(
        f"built {t['chunks']} chunks from {t['docs']} docs "
        f"({t['knowledgeDocs']} knowledge / {t['practicalDocs']} practical), {t['chars']} chars"
    )
    print(f"  {index_path.relative_to(ROOT)}")
    print(f"  {routes_path.relative_to(ROOT)}")
    print(f"  {manifest_path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
