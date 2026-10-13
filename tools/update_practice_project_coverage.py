"""Sync Practice_Project Automated / Case_Status from verified automation (2026-09-21)."""
from __future__ import annotations

from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Practice_Project"
COVERAGE = "Automation_Coverage"

# Excel row -> Automation_Ref (green / authored scenarios).
VERIFIED: dict[int, str] = {
    2: "practice_project.feature @PP-2",
    3: "Covered by @PP-2",
    4: "Covered by @PP-2",
    5: "practice_project_user_functionality.feature @PP-5",
    6: "Covered by @PP-5",
    7: "practice_project_actions.feature @PP-7",
    8: "practice_project_user_functionality.feature @PP-11 (covered by lifecycle)",
    9: "Covered by @PP-11",
    10: "Covered by @PP-11",
    15: "practice_project.feature @PP-15",
    16: "Covered by @PP-15",
    17: "Covered by @PP-15",
    18: "Covered by @PP-15",
    45: "practice_project_user_functionality.feature @PP-45",
    49: "practice_project_actions.feature @PP-49",
    113: "practice_project_actions.feature @PP-113",
    123: "practice_project_actions.feature @PP-123",
    181: "practice_project_user_functionality.feature @CGAP-PGG-13",
    182: "practice_project_user_functionality.feature @CGAP-PGG-14",
    183: "practice_project.feature @CGAP-PGG-15",
    184: "Covered by @PP-2 (CGAP-PGG-17 git URL toggle)",
    186: "practice_project_user_functionality.feature @CGAP-PGG-20",
    187: "practice_project.feature @CGAP-PP-1 (covered by @PP-2 form)",
    188: "Covered by @PP-113",
    189: "practice_project_actions.feature @CGAP-PP-3",
}

PARTIAL: dict[int, str] = {
    14: "Partial — practice_project_user_functionality.feature @PP-14 @guidedlab; run mvn test -Pslow",
    19: "Partial — @PP-19 @guidedlab; run -Pslow",
    22: "Partial — @PP-22 @guidedlab timed; run -Pslow",
    11: "Partial — practice_project_user_functionality.feature @PP-11 @guidedlab; run -Pslow",
    12: "Partial — practice_project_user_functionality.feature @PP-12 @guidedattempts; run -Pslow",
    20: "Partial — @PP-20 @guidedattempts; run -Pslow",
    21: "Partial — @PP-21 @guidedattempts; run -Pslow",
    27: "Partial — @PP-27 @guidedlab; run -Pslow",
    29: "Partial — @PP-29 @guidedlab; run -Pslow",
    33: "Partial — @PP-33 @guidedlab; run -Pslow",
    37: "Partial — @PP-37 @guidedattempts; run -Pslow",
    42: "Partial — @PP-42 @guidedattempts; run -Pslow",
    48: "Partial — @PP-48 @guidedlab; run -Pslow",
    139: "Partial — @PP-139 @guidedlab; run -Pslow",
    23: "Partial — variant fixture @PP-23; extend PracticeProjectFixtures when case is scoped",
    24: "Partial — variant fixture @PP-24",
    25: "Partial — variant fixture @PP-25",
    28: "Partial — variant fixture @PP-28",
    30: "Partial — variant fixture @PP-30",
    38: "Partial — variant fixture @PP-38",
    136: "Partial — variant fixture @PP-136",
    140: "Partial — variant fixture @PP-140",
}

DEVIATIONS: dict[int, str] = {
    31: "Deviation — manual Complete absent from guided-landing.mustache (@PP-31)",
    32: "Deviation — same Complete gap as PP-31",
    52: "Deviation — override form has 1-10/Unlimited only, no No Attempts (@PP-52)",
    117: "Deviation — participants table allows multi-select (@PP-117)",
    131: "Deviation — Check Details does not open checkpoint modal (@PP-131)",
    185: "Deviation — bootstrap-table export dropdown not rendered (@CGAP-PGG-19)",
}

NOT_AUTOMATABLE_WS = {
    row: "No — Not automatable: WS token cannot call mod_cloudlabs_get_guided_project_result (@PP-%s)" % row
    for row in range(59, 69)
}

NOT_AUTOMATABLE_REPORTS = {
    row: "No — Not automatable: Moodle site configurable reports; deferred per QA"
    for row in (
        69, 70, 71, 72, *range(73, 93), *range(94, 113), 137, 138, 142
    )
}

GET_PROGRESS_NO = {
    26: "No — cron-disabled auto-submit needs cron control",
    132: "No — Get Progress dormant on console",
    133: "No — Get Progress dormant",
    134: "No — Get Progress checkpoints UI dormant",
    135: "No — Get Progress manual dormant",
    155: "No — Get Progress commented out of lab console",
    156: "No — Get Progress checkpoint status dormant",
    157: "No — auto Get Progress via cron not automatable",
    158: "No — Get Progress related console flow dormant",
    161: "No — auto-complete when timeline ends needs cron/time control",
    174: "No — Get Progress flow dormant",
}

# Sandbox parity scenarios (practice_project_sandbox_parity.feature) — Partial until -Pslow green run.
SANDBOX_PARITY: dict[str, str] = {
    "CGAP-PP-7": "Partial — practice_project_sandbox_parity.feature @CGAP-PP-7 @guidedlab; run -Pslow",
    "CGAP-PP-8": "Partial — @CGAP-PP-8 @guidedlab; run -Pslow",
    "CGAP-PP-9": "Partial — @CGAP-PP-9 @guidedlab; run -Pslow",
    "CGAP-PP-10": "Partial — @CGAP-PP-10 @guidedlab; run -Pslow",
    "CGAP-PP-11": "Partial — @CGAP-PP-11 @guidedlab; run -Pslow",
    "CGAP-PP-13": "Partial — @CGAP-PP-13 @guidedlab; run -Pslow",
    "CGAP-PP-14": "Partial — @CGAP-PP-14 @guidedlab; run -Pslow",
    "CGAP-PP-15": "Partial — @CGAP-PP-15 @guidedgaps",
    "CGAP-PP-16": "Partial — @CGAP-PP-16 @guidedlab; run -Pslow",
    "CGAP-PP-17": "Partial — @CGAP-PP-17 @practice_project_manage; run -Pslow",
    "CGAP-PP-18": "Partial — @CGAP-PP-18 @practice_project_manage; run -Pslow",
    "CGAP-PP-19": "Partial — @CGAP-PP-19 @practice_project_manage; run -Pslow",
    "CGAP-PP-20": "Partial — @CGAP-PP-20 @practice_project_manage; run -Pslow",
    "CGAP-PP-21": "Partial — @CGAP-PP-21 @practice_project_manage",
    "CGAP-PP-23": "Partial — @CGAP-PP-23 @practice_project_manage",
    "CGAP-PP-12": "Deferred — Export PDF on PP console; needs PDF Yes practice fixture",
    "CGAP-PP-22": "Deferred — bulk Create from No labs; port from @SB-manage-12",
    "CGAP-PP-24": "Deferred — catalog/unlimited template fixture for practice project",
    "CGAP-PP-25": "Deferred — invalid-template practice activity fixture",
}

PROVISION_BLOCKED = {
    13: "No — @PP-13 needs running lab console (credentials/PDF); run with @guidedlab",
    56: "No — in-progress override status fixture not built (@PP-56)",
    177: "No — exhaust attempts needs @guidedlab provisioning",
    178: "No — Complete dormant (PP-31/32)",
    179: "No — restart-in-gap needs @guidedlab",
    190: "No — override-card start needs running lab",
    191: "No — last-attempt Stop/Delete needs @guidedlab",
    192: "No — outside date window needs throwaway dated activity in suite course",
}


def refresh_coverage_row(book) -> None:
    sheet = book[SHEET]
    states = ("yes", "partial", "deviation", "deferred", "no")
    counts = dict.fromkeys(states, 0)
    total = 0
    for row in range(2, sheet.max_row + 1):
        if not str(sheet.cell(row, 1).value or "").strip():
            continue
        total += 1
        state = str(sheet.cell(row, 11).value or "").strip().lower()
        if state in counts:
            counts[state] += 1
    if COVERAGE not in book.sheetnames:
        return
    coverage = book[COVERAGE]
    target = None
    for row in range(2, coverage.max_row + 1):
        if str(coverage.cell(row, 1).value or "").strip() == SHEET:
            target = row
            break
    if target is None:
        return
    values = [counts[s] for s in states] + [total]
    for offset, value in enumerate(values, start=2):
        coverage.cell(target, offset).value = value
    automatable = total - counts["no"]
    pct = round(100 * counts["yes"] / automatable) if automatable else 0
    coverage.cell(target, 8).value = f"{pct}%"


def apply() -> None:
    book = openpyxl.load_workbook(WORKBOOK)
    sheet = book[SHEET]

    def set_row(row: int, automated: str, ref: str, status: str) -> None:
        if row > sheet.max_row:
            return
        if not str(sheet.cell(row, 1).value or "").strip():
            return
        sheet.cell(row, 11).value = automated
        sheet.cell(row, 12).value = ref
        sheet.cell(row, 7).value = status

    for row, ref in VERIFIED.items():
        set_row(row, "Yes", ref, "Pass")

    for row, ref in PARTIAL.items():
        set_row(row, "Partial", ref, "Blocked")

    for row in range(2, sheet.max_row + 1):
        sno = str(sheet.cell(row, 1).value or "").strip()
        if sno in SANDBOX_PARITY:
            ref = SANDBOX_PARITY[sno]
            auto = "Deferred" if ref.startswith("Deferred") else "Partial"
            set_row(row, auto, ref, "Blocked" if auto == "Partial" else "NA")

    for row, ref in DEVIATIONS.items():
        set_row(row, "Deviation", ref, "Fail")

    for mapping in (NOT_AUTOMATABLE_WS, NOT_AUTOMATABLE_REPORTS, GET_PROGRESS_NO, PROVISION_BLOCKED):
        for row, ref in mapping.items():
            set_row(row, "No", ref, "NA")

    refresh_coverage_row(book)
    book.save(WORKBOOK)
    print(f"updated {WORKBOOK} — Practice_Project coverage synced")


if __name__ == "__main__":
    apply()
