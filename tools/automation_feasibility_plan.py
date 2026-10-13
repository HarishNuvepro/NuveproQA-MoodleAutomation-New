"""
One-off: Easy / Medium / Hard automation backlog with Importance counts.
Reads reports/traceability_execution.csv (regenerate via sync if stale).
"""
from __future__ import annotations

import csv
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CSV = ROOT / "reports" / "traceability_execution.csv"

# Module → difficulty tier (evidence: existing features, env deps, provisioning)
TIER: dict[str, str] = {
    # Easy — feature + page objects in repo; mostly UI / admin; patterns exist
    "Moodle_Tags": "easy",
    "Difficulty_Level_Configuration": "easy",
    "Content_Integration_Git_URL": "easy",
    "My_Labs": "easy",
    "Moodle_Lab_Templates": "easy",
    "Pre_Delivery_Lab_Checklist": "easy",
    "Moodle-Cloudlabs Integration": "easy",
    "Sequential_Activity_Completion": "easy",
    "Moodle settings": "easy",
    "Upload_Users": "easy",
    "Moodle_users": "easy",
    "All Course Page": "easy",
    "Moodle_Sandbox_Labs": "easy",
    "Groups_Testcases": "easy",
    "Moodle_Roles": "easy",
    "Practice_Project": "easy",
    # Medium — large deferred CGAP, provisioning, or greenfield with partial infra
    "Moodle_Assessment_Labs": "medium",
    "Activities": "medium",
    "Moodle_Reports": "medium",
    "Randomization_Activity": "medium",
    "AWS_Marketplace_Integration": "medium",
    "NuveBulkMail": "medium",
    "Lab_Control_Panel": "medium",
    "Stop_Lab_Enhancement": "medium",
    "Course_Category_Lab_Provision": "medium",
    "Lab_Creation_Region_Based": "medium",
    "CloudLabs_Lab_Creation_Status": "medium",
    # Hard — external systems, cron, multi-tenant, Orgo, SSO, perf/load
    "User_Sync_Orgo": "hard",
    "Course_Sync_Multi_Tenant": "hard",
    "Moodle_cron": "hard",
    "Moodle_Logs": "hard",
    "LTI and SSO Integration": "hard",
    "LoadBalacer_Moodle": "hard",
    "Raven360_AWS_Content": "hard",
}

DEFAULT_TIER = "medium"


def norm_imp(s: str) -> str:
    s = (s or "").strip().lower()
    if s.startswith("high"):
        return "High"
    if s.startswith("med"):
        return "Medium"
    if s.startswith("low"):
        return "Low"
    return "Unknown"


def main() -> None:
    if not CSV.is_file():
        raise SystemExit(f"Missing {CSV}; run sync_kiwi_and_traceability.py first")

    backlog: list[dict] = []
    with CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            auto_col = (row.get("automatable") or "").strip()
            if auto_col in ("No", "") or auto_col.lower().startswith("not"):
                continue
            if row.get("verified", "").lower() == "true":
                continue
            if (row.get("automated") or "").strip().lower() == "no":
                continue
            imp = norm_imp(row.get("importance", ""))
            area = row.get("area", "")
            tier = TIER.get(area, DEFAULT_TIER)
            status = row.get("automation_status", "")
            auto = (row.get("automated") or "").strip()
            backlog.append(
                {
                    "tier": tier,
                    "importance": imp,
                    "area": area,
                    "case_id": row.get("case_id", ""),
                    "status": status,
                    "automated": auto,
                }
            )

    by_tier_imp: Counter[tuple[str, str]] = Counter()
    by_tier_mod: Counter[tuple[str, str]] = Counter()
    by_tier_kind: Counter[tuple[str, str]] = Counter()

    for c in backlog:
        by_tier_imp[(c["tier"], c["importance"])] += 1
        by_tier_mod[(c["tier"], c["area"])] += 1
        if c["automated"] == "Deferred" or "CGAP" in c["case_id"]:
            kind = "deferred_gap"
        elif c["automated"] in ("Partial", "Yes") and "Partial" in c["status"]:
            kind = "scripted_needs_run_or_finish"
        elif not c["automated"]:
            kind = "blank_kiwi"
        else:
            kind = "other"
        by_tier_kind[(c["tier"], kind)] += 1

    tiers = ["easy", "medium", "hard"]
    imps = ["High", "Medium", "Low", "Unknown"]

    print("=== Automatable backlog NOT YET verified (traceability CSV) ===")
    print(f"Total cases: {len(backlog)}\n")

    print("## By difficulty tier × Kiwi Importance\n")
    print("| Tier | High | Medium | Low | Unknown | **Total** |")
    print("|------|-----:|-------:|----:|--------:|----------:|")
    for t in tiers:
        counts = [by_tier_imp[(t, i)] for i in imps]
        print(f"| **{t.title()}** | {counts[0]} | {counts[1]} | {counts[2]} | {counts[3]} | **{sum(counts)}** |")
    grand = [sum(by_tier_imp[(t, i)] for t in tiers) for i in range(4)]
    print(f"| **All** | {grand[0]} | {grand[1]} | {grand[2]} | {grand[3]} | **{sum(grand)}** |")

    print("\n## By tier × backlog kind\n")
    print("| Tier | Deferred/CGAP | Partial/script exists | Blank Kiwi | Other |")
    print("|------|--------------:|----------------------:|-----------:|------:|")
    kinds = ["deferred_gap", "scripted_needs_run_or_finish", "blank_kiwi", "other"]
    for t in tiers:
        print(
            f"| {t.title()} | "
            + " | ".join(str(by_tier_kind[(t, k)]) for k in kinds)
            + " |"
        )

    print("\n## Top modules per tier (remaining not verified)\n")
    for t in tiers:
        mods = [(a, n) for (tt, a), n in by_tier_mod.items() if tt == t]
        mods.sort(key=lambda x: -x[1])
        print(f"### {t.title()} ({sum(n for _, n in mods)} cases)")
        for a, n in mods[:8]:
            print(f"  - {a}: {n}")
        if len(mods) > 8:
            print(f"  - … +{len(mods) - 8} more modules")
        print()


if __name__ == "__main__":
    main()
