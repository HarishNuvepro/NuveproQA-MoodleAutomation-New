"""Pick 82 automatable cases to close the 918→1000 verified-automation gap."""
from __future__ import annotations

import csv
from pathlib import Path

from automation_feasibility_plan import CSV, TIER, DEFAULT_TIER

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "reports" / "to_1000_batch82.csv"

SKIP_AREAS = {
    "NuveBulkMail",
    "Moodle_Logs",
    "Course_Sync_Multi_Tenant",
    "User_Sync_Orgo",
    "Moodle_cron",
    "LTI and SSO Integration",
    "LoadBalacer_Moodle",
    "Raven360_AWS_Content",
    "Moodle_Assessment_Labs",  # defer mass CGAP — too large for one batch
}

IMP = {"High": 0, "Medium": 1, "Low": 2, "Unknown": 3}


def score(row: dict) -> tuple:
    area = row.get("area", "")
    tier = TIER.get(area, DEFAULT_TIER)
    ast = (row.get("automated") or "").strip().lower()
    status = row.get("automation_status") or ""
    imp = IMP.get((row.get("importance") or "Unknown"), 9)
    s = 0
    if "partial" in ast:
        s += 10
    if "deviation" in ast:
        s += 8
    if row.get("feature") or row.get("run_tag"):
        s += 5
    if tier == "easy":
        s += 4
    if ast in ("", "blank"):
        s += 1
    if "deferred" in ast or "Deferred" in status:
        s -= 6
    if area in SKIP_AREAS:
        s -= 100
    if area == "Activities":
        s += 2  # user asked for volume; still medium tier
    return (-s, imp, area, str(row.get("case_id", "")))


def main() -> None:
    rows: list[dict] = []
    with CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if (row.get("automatable") or "").strip().lower() not in ("yes",):
                continue
            if row.get("verified", "").lower() == "true":
                continue
            if (row.get("automated") or "").strip().lower() == "no":
                continue
            if TIER.get(row.get("area", ""), DEFAULT_TIER) == "hard":
                continue
            rows.append(row)
    rows.sort(key=score)
    pick = rows[:82]
    fields = [
        "case_id", "area", "importance", "automated", "automation_status",
        "run_tag", "feature", "gaps_remarks",
    ]
    with OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        for r in pick:
            w.writerow({k: r.get(k, "") for k in fields})
    from collections import Counter

    print(f"Wrote {OUT} ({len(pick)} rows)")
    print("By area:", Counter(r["area"] for r in pick))


if __name__ == "__main__":
    main()
