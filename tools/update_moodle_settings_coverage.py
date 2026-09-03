"""Mark Moodle settings cases Automated + Automation_Ref like the other modules.

Sheet row 3 = @SET-1 … row 80 = @SET-78. Appends CGAP-SET-1 for @SET-79.
Touches only the Moodle settings sheet, its Automation_Coverage row, and the
plan's Automated flag for that module.
"""
from __future__ import annotations

from copy import copy
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Moodle settings"
COVERAGE = "Automation_Coverage"
PLAN = "Release_Regression_Plan"
STATES = ("yes", "partial", "deviation", "deferred", "no")

# Manual expected 0-day duration to be rejected; this site stores 0.
DEVIATION_ROWS = {
    24: "Deviation — site accepts duration 0 (manual expected 365). moodle_settings.feature @SET-22",
}

NOTES = {
    7: "moodle_settings.feature @SET-5 (live default is Custom sections; sheet said Topics)",
    47: "moodle_settings.feature @SET-45 (footer offers No footer; site default is MoodleNet)",
}


def refresh_coverage_row(book) -> None:
    sheet = book[SHEET]
    counts = dict.fromkeys(STATES, 0)
    total = 0
    for row in range(2, sheet.max_row + 1):
        if not str(sheet.cell(row, 1).value or "").strip():
            continue
        total += 1
        state = str(sheet.cell(row, 11).value or "").strip().lower()
        if state in counts:
            counts[state] += 1

    coverage = book[COVERAGE]
    target = next((row for row in range(2, coverage.max_row + 1)
                   if str(coverage.cell(row, 1).value or "").strip() == SHEET), None)
    if target is None:
        total_row = next(row for row in range(2, coverage.max_row + 1)
                         if str(coverage.cell(row, 1).value or "").upper().startswith("TOTAL"))
        target = total_row
        coverage.insert_rows(target)
        template = target - 1
        for column in range(1, 9):
            coverage.cell(target, column)._style = copy(coverage.cell(template, column)._style)
        coverage.cell(target, 1).value = SHEET
        print(f"inserted {COVERAGE} row {target} for {SHEET}")

    values = [counts[state] for state in STATES] + [total]
    for offset, value in enumerate(values, start=2):
        coverage.cell(target, offset).value = value
    coverage.cell(target, 8).value = f"{round(100 * counts['yes'] / total)}%" if total else "0%"

    total_row = next(row for row in range(2, coverage.max_row + 1)
                     if str(coverage.cell(row, 1).value or "").upper().startswith("TOTAL"))
    sums = [0] * 6
    for row in range(2, total_row):
        for index in range(6):
            value = coverage.cell(row, index + 2).value
            sums[index] += value if isinstance(value, (int, float)) else 0
    for index, value in enumerate(sums, start=2):
        coverage.cell(total_row, index).value = value
    coverage.cell(total_row, 8).value = f"{round(100 * sums[0] / sums[5])}%" if sums[5] else "0%"
    print(f"{COVERAGE} {SHEET}: {values} ({coverage.cell(target, 8).value}); TOTAL {sums}")


def mark_plan_automated(book) -> None:
    plan = book[PLAN]
    for row in range(10, plan.max_row + 1):
        if str(plan.cell(row, 2).value or "").strip() == SHEET:
            plan.cell(row, 11).value = "Yes"
            print(f"{PLAN} row {row} Automated=Yes")
            return


def main() -> None:
    book = openpyxl.load_workbook(WORKBOOK)
    sheet = book[SHEET]
    sheet.cell(1, 11).value = "Automated"
    sheet.cell(1, 12).value = "Automation_Ref"

    for row in range(3, 81):
        set_id = row - 2
        if row in DEVIATION_ROWS:
            sheet.cell(row, 11).value = "Deviation"
            sheet.cell(row, 12).value = DEVIATION_ROWS[row]
            sheet.cell(row, 7).value = "Fail"
            continue
        sheet.cell(row, 11).value = "Yes"
        sheet.cell(row, 12).value = NOTES.get(row, f"moodle_settings.feature @SET-{set_id}")
        sheet.cell(row, 7).value = "Pass"

    last = 81
    if str(sheet.cell(last, 1).value or "").strip() != "CGAP-SET-1":
        sheet.cell(last, 1).value = "CGAP-SET-1"
        sheet.cell(last, 2).value = (
            "Verify a student cannot open Moodle admin settings pages directly")
        sheet.cell(last, 3).value = "Student account"
        sheet.cell(last, 4).value = "Moodle"
        sheet.cell(last, 5).value = "Moodle settings"
        sheet.cell(last, 6).value = "Medium"
        sheet.cell(last, 8).value = (
            "1) Log in as student 2) Open /admin/settings.php?section=coursesettings")
        sheet.cell(last, 9).value = "Access is denied; the settings form is not shown"
        sheet.cell(last, 10).value = "Extended"
        print("appended CGAP-SET-1")
    sheet.cell(last, 7).value = "Pass"
    sheet.cell(last, 11).value = "Yes"
    sheet.cell(last, 12).value = "moodle_settings.feature @SET-79"

    refresh_coverage_row(book)
    mark_plan_automated(book)
    book.save(WORKBOOK)
    print("saved", WORKBOOK)


if __name__ == "__main__":
    main()
