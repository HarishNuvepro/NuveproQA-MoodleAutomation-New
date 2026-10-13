"""Read-only audit of Practice_Project Automated=No rows for conversion planning."""
from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
WORKBOOK = ROOT / "docs/MoodleRegression_Testing_Kiwi new.xlsx"
FEAT = ROOT / "src/test/resources/features"


def feature_tags() -> dict[str, str]:
    out: dict[str, str] = {}
    for f in FEAT.glob("*.feature"):
        text = f.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r"@(PP-\d+)", text):
            out.setdefault(m.group(1), f.name)
    return out


def bucket(ref: str) -> str:
    r = ref.lower()
    if any(
        k in r
        for k in (
            "dormant",
            "get progress",
            "commented out",
            "cron-disabled",
            "cron control",
            "timeline ends",
            "complete is dormant",
            "complete is commented",
        )
    ):
        return "A_STAY_NO_dormant_cron"
    if "401" in r or "cloudlabs admin login" in r:
        return "B_lab_cloudlabs_auth"
    if "ws token" in r or "mod_cloudlabs_get_guided" in r:
        return "C_ws_api_token"
    if "variant cmid" in r or "variants.feature" in r or "required variant" in r:
        return "D_variant_cmids"
    if "configurable-report" in r or "practice_project_reports" in r or (
        "report" in r and ("url" in r or "artifact" in r)
    ):
        return "E_moodle_reports"
    if any(
        k in r
        for k in (
            "provisioned lab",
            "running configured lab",
            "running lab plus",
            "exhaust attempts",
            "restart-in-gap",
            "last-attempt",
            "outside date window",
            "dated activity",
            "in-progress fixture",
        )
    ):
        return "F_lab_provision_fixtures"
    if any(
        k in r
        for k in (
            "check details",
            "checkpoint",
            "multiple participant",
            "multiple checkbox",
            "export types",
            "no attempts",
            "override form has",
        )
    ):
        return "G_product_or_scenario_gap"
    if "not automatable" in r:
        return "A_STAY_NO_other"
    return "H_needs_manual_review"


def main() -> None:
    tags = feature_tags()
    wb = openpyxl.load_workbook(WORKBOOK, read_only=True, data_only=True)
    ws = wb["Practice_Project"]
    headers = [c.value for c in next(ws.iter_rows(min_row=1, max_row=1))]
    auto_i = headers.index("Automated")
    ref_i = headers.index("Automation_Ref")
    title_i = 0

    no_rows: list[dict] = []
    for row_idx, r in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
        if not r:
            continue
        auto = (str(r[auto_i]).strip() if r[auto_i] else "").lower()
        if auto != "no":
            continue
        ref = str(r[ref_i] or "").strip()
        title = str(r[title_i] or "").strip()
        m = re.search(r"@PP-(\d+)", ref)
        pp = int(m.group(1)) if m else None
        tag = f"PP-{pp}" if pp else None
        no_rows.append(
            {
                "row": row_idx,
                "pp": pp,
                "title": title[:70],
                "ref": ref[:120],
                "bucket": bucket(ref),
                "scripted": tag in tags if tag else False,
                "feature": tags.get(tag or "", ""),
            }
        )
    wb.close()

    by_bucket: dict[str, list] = defaultdict(list)
    for x in no_rows:
        by_bucket[x["bucket"]].append(x)

    order = sorted(by_bucket.keys())
    doable = 0
    stay = 0
    print("Practice_Project Automated=No audit\n")
    for b in order:
        rows = by_bucket[b]
        scripted = sum(1 for x in rows if x["scripted"])
        print(f"{b}: {len(rows)} rows ({scripted} already have @PP-n in a .feature)")
        pps = sorted({x["pp"] for x in rows if x["pp"]})
        if pps:
            print(f"  PP rows: {pps}")
        if b.startswith("A_STAY"):
            stay += len(rows)
        else:
            doable += len(rows)
        print()

    print(f"Total No: {len(no_rows)}")
    print(f"Planned conversion (env/lab/report/ws): ~{doable}")
    print(f"Keep No (dormant/cron/other): ~{stay}")
    print(f"Scripted but still No in Kiwi (flip to Yes after green run): {sum(1 for x in no_rows if x['scripted'])}")


if __name__ == "__main__":
    main()
