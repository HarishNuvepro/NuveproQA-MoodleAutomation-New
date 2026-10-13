"""Merge Cucumber JSON reports under target/ into cucumber_fast.json (+ cucumber.json)."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "target"
OUT_FAST = TARGET / "cucumber_fast.json"
OUT_MAIN = TARGET / "cucumber.json"

RANK = {"failed": 4, "ambiguous": 4, "undefined": 4, "pending": 3, "skipped": 2, "passed": 1}


def load(path: Path) -> list:
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    data = json.loads(text)
    return data if isinstance(data, list) else [data]


def scenario_key(el: dict) -> str:
    tags = sorted(t.get("name", "") for t in el.get("tags", []))
    return "|".join(tags) + "|" + el.get("name", "")


def outcome(el: dict) -> str:
    statuses = [st.get("result", {}).get("status") for st in el.get("steps", [])]
    if any(s in ("failed", "undefined", "pending", "ambiguous") for s in statuses):
        return "failed"
    if any(s == "skipped" for s in statuses):
        return "skipped"
    return "passed"


def main() -> None:
    files = sorted(TARGET.rglob("cucumber.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not files:
        print("No cucumber.json under target/")
        return
    best: dict[str, tuple[str, dict, dict]] = {}
    for path in files:
        for feat in load(path):
            feat_shell = {"uri": feat.get("uri", ""), "name": feat.get("name", "")}
            for el in feat.get("elements", []):
                if el.get("type") != "scenario":
                    continue
                sk = scenario_key(el)
                oc = outcome(el)
                prev = best.get(sk)
                if prev is None or RANK[oc] >= RANK[prev[0]]:
                    best[sk] = (oc, el, feat_shell)
    buckets: dict[str, dict] = {}
    for sk, (_oc, el, feat_shell) in best.items():
        uri = feat_shell["uri"] or feat_shell["name"] or "unknown"
        if uri not in buckets:
            buckets[uri] = {"uri": feat_shell["uri"], "name": feat_shell["name"], "elements": []}
        buckets[uri]["elements"].append(el)
    out = list(buckets.values())
    payload = json.dumps(out, indent=2)
    OUT_FAST.write_text(payload, encoding="utf-8")
    OUT_MAIN.write_text(payload, encoding="utf-8")
    print(f"Merged {len(best)} scenarios from {len(files)} file(s) -> {OUT_FAST.name}")


if __name__ == "__main__":
    main()
