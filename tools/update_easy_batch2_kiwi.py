"""Mark batch-2 easy cases Yes/Partial with Automation_Ref."""
from __future__ import annotations

import csv
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
BATCH = ROOT / "reports" / "easy_automation_batches.csv"
XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"

REF = {
    "CGAP-R-4": ("Yes", "easy_automation_batch2.feature @CGAP-R-4"),
    "CGAP-R-5": ("Yes", "easy_automation_batch2.feature @CGAP-R-5"),
    "CGAP-R-8": ("Yes", "easy_automation_batch2.feature @CGAP-R-8"),
    "CGAP-R-9": ("Yes", "easy_automation_batch2.feature @CGAP-R-9"),
    "CGAP-R-11": ("Yes", "easy_automation_batch2.feature @CGAP-R-11"),
    "CGAP-T-3": ("Yes", "easy_automation_batch2.feature @CGAP-T-3"),
    "CGAP-T-6": ("Yes", "easy_automation_batch2.feature @CGAP-T-6"),
    "CGAP-T-8": ("Yes", "easy_automation_batch2.feature @CGAP-T-8"),
    "CGAP-T-9": ("Partial", "easy_automation_batch2.feature @CGAP-T-9 — flag UI varies"),
    "CGAP-T-11": ("Yes", "easy_automation_batch2.feature @CGAP-T-11"),
    "CGAP-ML-1": ("Yes", "mylabs.feature @ML-10 @CGAP-ML-1 @easybatch2"),
    "CGAP-ML-3": ("Yes", "mylabs.feature @ML-6 @ML-45 @CGAP-ML-3 @easybatch2"),
    "CGAP-PP-12": ("Deferred", "easy_automation_batch2.feature @CGAP-PP-12 @skip"),
    "CGAP-PP-22": ("Deferred", "easy_automation_batch2.feature @CGAP-PP-22 @skip"),
    "CGAP-PP-24": ("Deferred", "easy_automation_batch2.feature @CGAP-PP-24 @skip"),
    "CGAP-PP-25": ("Deferred", "easy_automation_batch2.feature @CGAP-PP-25 @skip"),
    "Verify All Activity Types Are Displayed When All Are Selected": (
        "Partial",
        "mylabs.feature @ML-24 @easybatch2",
    ),
    "Verify Default Behavior When No Activity Type Is Selected": (
        "Partial",
        "mylabs.feature @ML-25 @easybatch2",
    ),
    "Verify Display for User with No Labs Assigned": (
        "Partial",
        "mylabs.feature @ML-10 @CGAP-ML-1 @easybatch2",
    ),
    "Verify Only Assessment Activities Are Displayed": (
        "Partial",
        "mylabs.feature @ML-23 @easybatch2",
    ),
    "Verify Only Practice Project Activities Are Displayed": (
        "Partial",
        "mylabs.feature @ML-26 @easybatch2",
    ),
    "Verify Only Sandbox Activities Are Displayed": (
        "Partial",
        "mylabs.feature @ML-21 @ML-22 @easybatch2",
    ),
}


def main() -> None:
    rows = []
    with BATCH.open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row.get("batch") == "2":
                rows.append(row)
    wb = openpyxl.load_workbook(XLSX)
    updated = 0
    for row in rows:
        cid = row["case_id"]
        area = row["area"]
        if cid in REF:
            auto, ref = REF[cid]
        elif row.get("run_tag"):
            tag = row["run_tag"]
            feat = row.get("feature") or "labtemplates.feature"
            auto, ref = ("Partial", f"{feat} @{tag} @easybatch2")
        elif area == "Pre_Delivery_Lab_Checklist" or "Pre-Delivery" in cid:
            if "Dasshboaard" in cid or "Dashboard" in cid:
                auto, ref = ("Partial", "predelivery_dashboard.feature @easybatch2 @predelivery-dashboard")
            else:
                auto, ref = ("Partial", "predelivery_mutate.feature @easybatch2 @predelivery-mutate")
        elif area == "Practice_Project" and "deviation" in (row.get("automation_status") or "").lower():
            auto, ref = (
                "Deviation",
                "practice_project_user_functionality.feature @PP-31 @PP-30 @deviation @easybatch2",
            )
        elif area == "My_Labs":
            auto, ref = ("Partial", "mylabs.feature @easybatch2")
        elif area == "Practice_Project" and row.get("run_tag"):
            auto, ref = ("Partial", f"practice_project.feature @{row['run_tag']} @easybatch2")
        elif area == "Practice_Project":
            auto, ref = ("Partial", "practice_project.feature @easybatch2")
        elif area == "Difficulty_Level_Configuration":
            auto, ref = ("Yes", "difficulty_level_configuration.feature @DLC-10 @easybatch2")
        else:
            continue
        ws = wb[area]
        for r in range(2, ws.max_row + 1):
            title = str(ws.cell(r, 2).value or "").strip()
            sno = str(ws.cell(r, 1).value or "").strip()
            if cid.startswith("CGAP-"):
                matched = sno == cid
            else:
                matched = sno == cid or title == cid
            if not matched:
                continue
            ws.cell(r, 11).value = auto
            ws.cell(r, 12).value = ref
            updated += 1
            break
    wb.save(XLSX)
    print(f"Updated {updated} Kiwi rows for easy batch 2")


if __name__ == "__main__":
    main()
