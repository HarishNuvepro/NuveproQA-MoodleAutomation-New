"""Append CGAP-PP-7…23 (sandbox parity) to Practice_Project + refresh coverage counts."""
from __future__ import annotations

from copy import copy
from datetime import date
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Practice_Project"
NOTE = f"Automation gap (sandbox parity) {date.today().isoformat()}"

ROWS = [
    ("CGAP-PP-7", "High",
     "Lab Usage / Get Cost on Practice Project console shows numeric cost values (sandbox SB-manage-27).",
     "practice_project_sandbox_parity.feature @CGAP-PP-7"),
    ("CGAP-PP-8", "Medium",
     "Student saves a lab note on Practice Project console and it persists after reopen (SB-user-6).",
     "practice_project_sandbox_parity.feature @CGAP-PP-8"),
    ("CGAP-PP-9", "Medium",
     "Refresh lab on Practice Project console keeps lab Running (SB-manage-31).",
     "practice_project_sandbox_parity.feature @CGAP-PP-9"),
    ("CGAP-PP-10", "Medium",
     "Support tab mailto on Practice Project console (SB-user-12).",
     "practice_project_sandbox_parity.feature @CGAP-PP-10"),
    ("CGAP-PP-11", "Medium",
     "Console Back returns to Practice Project landing (SB-user-13).",
     "practice_project_sandbox_parity.feature @CGAP-PP-11"),
    ("CGAP-PP-12", "Medium",
     "Instructions PDF download on Practice Project console (SB-user-8).",
     "Deferred — needs Export PDF Yes fixture on practice activity; mirror sandbox_create_yes"),
    ("CGAP-PP-13", "Medium",
     "Change lab password from Practice Project credentials popup (SB-manage-29).",
     "practice_project_sandbox_parity.feature @CGAP-PP-13"),
    ("CGAP-PP-14", "High",
     "Jump to Console / Access Lab opens cloud platform for Practice Project (SB-user-1).",
     "practice_project_sandbox_parity.feature @CGAP-PP-14"),
    ("CGAP-PP-15", "Medium",
     "Cancel Start Practice Project confirmation creates no lab (SB-manage-26).",
     "practice_project_sandbox_parity.feature @CGAP-PP-15"),
    ("CGAP-PP-16", "Medium",
     "Allotted/consumed/remaining duration on PP console vs CloudLabs (SB-user-18).",
     "practice_project_sandbox_parity.feature @CGAP-PP-16"),
    ("CGAP-PP-17", "High",
     "Manage Labs admin Sync/Stop after student starts Practice Project lab (SB-manage-1).",
     "practice_project_sandbox_parity.feature @CGAP-PP-17"),
    ("CGAP-PP-18", "High",
     "Create Practice Project lab from Manage Labs No labs tab (SB-manage-3).",
     "practice_project_sandbox_parity.feature @CGAP-PP-18"),
    ("CGAP-PP-19", "High",
     "Delete confirmation removes Practice Project lab from Manage Labs (SB-manage-8).",
     "practice_project_sandbox_parity.feature @CGAP-PP-19"),
    ("CGAP-PP-20", "High",
     "Stopped Practice Project lab can be Started again from Manage Labs (SB-manage-9).",
     "practice_project_sandbox_parity.feature @CGAP-PP-20"),
    ("CGAP-PP-21", "Medium",
     "Manage Labs Start/Stop/Delete/Sync gated until row selected for Practice Project (SB-manage-15).",
     "practice_project_sandbox_parity.feature @CGAP-PP-21"),
    ("CGAP-PP-22", "Medium",
     "Bulk Create from No labs for Practice Project (SB-manage-12).",
     "Deferred — port sandbox bulk Create scenario with practice project fixture"),
    ("CGAP-PP-23", "Medium",
     "Manage Labs Active/No labs page chrome for Practice Project (SB-manage-23).",
     "practice_project_sandbox_parity.feature @CGAP-PP-23"),
    ("CGAP-PP-24", "Medium",
     "Catalog Proceed + unlimited duration + invalid Start/Create for Practice Project (SB-user-19).",
     "Deferred — needs catalog/unlimited template fixture on practice suite course"),
    ("CGAP-PP-25", "Low",
     "Invalid-template Create error on Practice Project landing (SB-manage-34).",
     "Deferred — needs invalid-template practice activity fixture"),
]


def existing_snos(sheet) -> set[str]:
    out = set()
    for row in range(2, sheet.max_row + 1):
        v = str(sheet.cell(row, 1).value or "").strip()
        if v:
            out.add(v)
    return out


def main() -> None:
    wb = openpyxl.load_workbook(WORKBOOK)
    sh = wb[SHEET]
    snos = existing_snos(sh)
    styles = [copy(sh.cell(2, c)._style) for c in range(1, 13)]
    row = sh.max_row + 1
    added = []
    for sno, priority, summary, ref in ROWS:
        if sno in snos:
            print(f"  skip existing {sno}")
            continue
        auto = "Deferred" if ref.startswith("Deferred") else "Partial"
        status = "Blocked" if auto == "Partial" else "NA"
        sh.cell(row, 1).value = sno
        sh.cell(row, 2).value = summary
        sh.cell(row, 4).value = "Moodle"
        sh.cell(row, 5).value = "Practice_Project"
        sh.cell(row, 6).value = priority
        sh.cell(row, 9).value = summary.split("(")[0].strip()
        sh.cell(row, 10).value = "Core"
        sh.cell(row, 11).value = auto
        sh.cell(row, 12).value = ref
        sh.cell(row, 7).value = status
        for c in range(1, 13):
            sh.cell(row, c)._style = styles[c - 1]
        added.append((row, sno, ref))
        row += 1
    wb.save(WORKBOOK)
    print(f"Appended {len(added)} rows to {SHEET}")
    for r, sno, ref in added:
        print(f"  row {r}: {sno} -> {ref[:60]}")


if __name__ == "__main__":
    main()
