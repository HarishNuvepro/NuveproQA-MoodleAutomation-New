"""Export easy-tier backlog as batch1/batch2 (51 each) CSV + tag list."""
from __future__ import annotations

import csv
from pathlib import Path

from automation_feasibility_plan import CSV, TIER, DEFAULT_TIER, norm_imp

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "reports" / "easy_automation_batches.csv"
IMP_ORDER = {"High": 0, "Medium": 1, "Low": 2, "Unknown": 3}


def load_easy() -> list[dict]:
    rows: list[dict] = []
    with CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            auto_col = (row.get("automatable") or "").strip()
            if auto_col in ("No", "") or auto_col.lower().startswith("not"):
                continue
            if row.get("verified", "").lower() == "true":
                continue
            if (row.get("automated") or "").strip().lower() == "no":
                continue
            area = row.get("area", "")
            if TIER.get(area, DEFAULT_TIER) != "easy":
                continue
            rows.append(row)
    rows.sort(
        key=lambda r: (
            IMP_ORDER.get(norm_imp(r.get("importance", "")), 9),
            r.get("area", ""),
            str(r.get("case_id", "")),
        )
    )
    return rows


def main() -> None:
    easy = load_easy()
    if len(easy) != 102:
        print(f"WARN: expected 102 easy rows, got {len(easy)}")
    fields = [
        "batch", "case_id", "area", "importance", "automated", "automation_status",
        "run_tag", "feature", "gaps_remarks",
    ]
    with OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        for i, row in enumerate(easy):
            batch = 1 if i < 51 else 2
            w.writerow({"batch": batch, **{k: row.get(k, "") for k in fields if k != "batch"}})
    b1_tags = []
    for row in easy[:51]:
        t = (row.get("run_tag") or "").strip()
        if t and not t.startswith("CGAP") and row.get("feature"):
            b1_tags.append(t)
        else:
            # extract from ref or case_id
            cid = row.get("case_id", "")
            ref = row.get("ref") or row.get("automation_test") or ""
            if "@" in ref:
                b1_tags.append(ref.split("@")[-1].split(")")[0].strip())
            elif cid.startswith("CGAP-"):
                b1_tags.append(cid)
    print(f"Wrote {OUT} ({len(easy)} rows)")
    print("\n=== BATCH 1 (51) ===")
    for row in easy[:51]:
        print(
            f"{row.get('case_id')}\t{row.get('area')}\t{row.get('importance')}\t"
            f"{row.get('automated')}\t{row.get('run_tag') or '-'}\t{row.get('feature') or '-'}"
        )
    print("\n=== BATCH 1 cucumber tags (unique) ===")
    seen = set()
    for row in easy[:51]:
        tag = (row.get("run_tag") or "").strip()
        cid = row.get("case_id", "")
        if tag:
            key = tag.upper()
        elif cid.startswith("CGAP-"):
            key = cid
            tag = f"@{cid}"
        else:
            continue
        if key in seen:
            continue
        seen.add(key)
        print(tag if tag.startswith("@") else f"@{tag}")


if __name__ == "__main__":
    main()
