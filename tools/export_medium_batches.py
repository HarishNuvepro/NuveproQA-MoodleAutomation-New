"""Export medium-tier backlog as batch CSVs (default batch 1 = 51 cases, High first)."""
from __future__ import annotations

import csv
from pathlib import Path

from automation_feasibility_plan import CSV, DEFAULT_TIER, TIER, norm_imp

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "reports" / "medium_automation_batch1.csv"
BATCH_SIZE = 51
IMP_ORDER = {"High": 0, "Medium": 1, "Low": 2, "Unknown": 3}


def load_medium() -> list[dict]:
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
            if TIER.get(area, DEFAULT_TIER) != "medium":
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
    medium = load_medium()
    batch = medium[:BATCH_SIZE]
    fields = [
        "batch", "case_id", "area", "importance", "automated", "automation_status",
        "run_tag", "feature", "gaps_remarks",
    ]
    with OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        for row in batch:
            w.writerow({"batch": 1, **{k: row.get(k, "") for k in fields if k != "batch"}})
    print(f"Wrote {OUT} ({len(batch)} of {len(medium)} medium backlog rows)")
    areas: dict[str, int] = {}
    for row in batch:
        areas[row.get("area", "?")] = areas.get(row.get("area", "?"), 0) + 1
    print("Batch 1 module mix:", ", ".join(f"{a}={n}" for a, n in sorted(areas.items(), key=lambda x: -x[1])))


if __name__ == "__main__":
    main()
