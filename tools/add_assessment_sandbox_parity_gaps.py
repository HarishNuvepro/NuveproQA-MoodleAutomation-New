"""Append CGAP-A-SB-7…23 (sandbox parity on Assessment) to Moodle_Assessment_Labs."""
from __future__ import annotations

from copy import copy
from datetime import date
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Moodle_Assessment_Labs"
NOTE = f"Automation gap (sandbox→assessment parity) {date.today().isoformat()}"

ROWS = [
    ("CGAP-A-SB-7", "High",
     "Lab Usage / Get Cost on assessment console (sandbox SB-manage-27).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-7"),
    ("CGAP-A-SB-8", "Medium",
     "Lab note save/persist on assessment console (SB-user-6).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-8"),
    ("CGAP-A-SB-9", "Medium",
     "Refresh lab on assessment console keeps Running (SB-manage-31).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-9"),
    ("CGAP-A-SB-10", "Medium",
     "Support mailto on assessment console (SB-user-12).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-10"),
    ("CGAP-A-SB-11", "Medium",
     "Console Back returns to assessment landing (SB-user-13).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-11"),
    ("CGAP-A-SB-12", "Medium",
     "Instructions PDF on assessment console (SB-user-8).",
     "Deferred — needs Export PDF Yes on lifecycle assessment activity"),
    ("CGAP-A-SB-13", "Medium",
     "Change lab password from assessment credentials popup (SB-manage-29).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-13"),
    ("CGAP-A-SB-14", "High",
     "Jump to Console / Access Lab for assessment (SB-user-1).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-14"),
    ("CGAP-A-SB-15", "Medium",
     "Cancel Start Assessment creates no lab (SB-manage-26).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-15"),
    ("CGAP-A-SB-16", "Medium",
     "Assessment console duration vs CloudLabs (SB-user-18).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-16"),
    ("CGAP-A-SB-17", "High",
     "Manage Labs Sync/Stop after student starts assessment lab (SB-manage-1).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-17"),
    ("CGAP-A-SB-18", "High",
     "Create assessment lab from Manage Labs No labs (SB-manage-3).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-18"),
    ("CGAP-A-SB-19", "High",
     "Delete assessment lab from Manage Labs (SB-manage-8).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-19"),
    ("CGAP-A-SB-20", "High",
     "Restart stopped assessment lab from Manage Labs (SB-manage-9).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-20"),
    ("CGAP-A-SB-21", "Medium",
     "Manage Labs action gates for assessment (SB-manage-15).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-21"),
    ("CGAP-A-SB-22", "Medium",
     "Bulk Create from No labs for assessment (SB-manage-12).",
     "Deferred — port sandbox bulk Create with assessment fixture"),
    ("CGAP-A-SB-23", "Medium",
     "Manage Labs Active/No labs chrome for assessment (SB-manage-23).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-23"),
    ("CGAP-A-SB-24", "Medium",
     "Catalog/unlimited/invalid Start for assessment (SB-user-19).",
     "Deferred — needs catalog assessment activity on suite course"),
    ("CGAP-A-SB-25", "Low",
     "Invalid-template Create error on assessment landing (SB-manage-34).",
     "Deferred — needs invalid-template assessment activity"),
    ("CGAP-A-SB-26", "Medium",
     "Continue resumes assessment lab console (SB-user-4 / PP lifecycle).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-26"),
    ("CGAP-A-SB-27", "Medium",
     "Read More opens full assessment description (SB-user-11 / PP landing).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-27"),
    ("CGAP-A-SB-28", "Medium",
     "Fresh landing Start Assessment only without Continue (SB-user-7).",
     "assessment_sandbox_parity.feature @CGAP-A-SB-28"),
]


def existing_snos(sheet) -> set[str]:
    out = set()
    for row in range(2, sheet.max_row + 1):
        v = str(sheet.cell(row, 1).value or "").strip()
        if v:
            out.add(v)
    return out


def refresh_coverage(book) -> None:
    sh = book[SHEET]
    counts = dict.fromkeys(("yes", "partial", "deviation", "deferred", "no"), 0)
    total = 0
    for row in range(2, sh.max_row + 1):
        if not str(sh.cell(row, 1).value or "").strip():
            continue
        total += 1
        auto = str(sh.cell(row, 11).value or "").strip().lower()
        if auto.startswith("no"):
            counts["no"] += 1
        elif auto in counts:
            counts[auto] += 1
        else:
            counts["no"] += 1
    cov = book["Automation_Coverage"]
    for r in range(2, cov.max_row + 1):
        if str(cov.cell(r, 1).value or "").strip() == SHEET:
            cov.cell(r, 2).value = counts["yes"]
            cov.cell(r, 3).value = counts["partial"]
            cov.cell(r, 4).value = counts["deviation"]
            cov.cell(r, 5).value = counts["deferred"]
            cov.cell(r, 6).value = counts["no"]
            cov.cell(r, 7).value = total
            auto = total - counts["no"]
            cov.cell(r, 8).value = f"{round(100 * counts['yes'] / auto)}%" if auto else "0%"
            break


def main() -> None:
    wb = openpyxl.load_workbook(WORKBOOK)
    sh = wb[SHEET]
    snos = existing_snos(sh)
    styles = [copy(sh.cell(2, c)._style) for c in range(1, 13)]
    row = sh.max_row + 1
    added = 0
    for sno, priority, summary, ref in ROWS:
        if sno in snos:
            print(f"  skip existing {sno}")
            continue
        auto = "Deferred" if ref.startswith("Deferred") else "Partial"
        status = "Blocked" if auto == "Partial" else "NA"
        sh.cell(row, 1).value = sno
        sh.cell(row, 2).value = summary
        sh.cell(row, 3).value = NOTE
        sh.cell(row, 4).value = "Moodle"
        sh.cell(row, 5).value = "Assessment"
        sh.cell(row, 6).value = priority
        sh.cell(row, 9).value = summary
        sh.cell(row, 10).value = "Core"
        sh.cell(row, 11).value = auto
        sh.cell(row, 12).value = ref
        sh.cell(row, 7).value = status
        for c in range(1, 13):
            sh.cell(row, c)._style = styles[c - 1]
        row += 1
        added += 1
    refresh_coverage(wb)
    wb.save(WORKBOOK)
    print(f"Appended {added} CGAP-A-SB rows to {SHEET}")


if __name__ == "__main__":
    main()
