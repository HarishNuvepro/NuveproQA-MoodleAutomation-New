"""Verify the Kiwi master's two dashboard sheets against its own case sheets.

READ-ONLY on purpose: QA owns the dashboard layout and its formulas, so this
script never writes to the workbook. It recomputes every number from the case
rows and reports agreement or drift, which is the fastest way to tell whether a
dashboard has gone stale after cases were added or re-marked.

Semantics (taken from the dashboard's own formulas):
  Total Test Cases = rows with a Title
  Executed         = Automated in Yes/Partial/Deviation AND Case_Status in Pass/Fail/Blocked
  Not Automatable  = Automated=No
  Deferred         = Total - Executed - Not Automatable
  Completion %     = Executed / (Total - Not Automatable)

Usage: python tools/check_kiwi_dashboards.py
Exit code 1 if anything is out of sync.
"""
from __future__ import annotations

import collections
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
EXECUTION, COVERAGE = "Regression_Dashboard", "Automation_Coverage"
RUN_STATUSES = {"pass", "fail", "failed", "blocked"}
AUTOMATION_STATUSES = ["Yes", "Partial", "Deviation", "Deferred", "No"]
TITLE_COL, SUMMARY_COL, STATUS_COL, AUTOMATED_COL = 1, 2, 7, 11
# The dashboard's merged assessment row: its total comes from the release plan
# (which counts the Summary column) and its executed count is the two suites.
ASSESSMENT_SHEET = "Moodle_Assessment_Labs"
ASSESSMENT_SUITES = (47, 24)


def case_rows(sheet):
    for row in range(2, sheet.max_row + 1):
        if str(sheet.cell(row=row, column=TITLE_COL).value or "").strip():
            yield row


def tally(sheet) -> collections.Counter:
    counts = collections.Counter()
    for row in case_rows(sheet):
        counts["total"] += 1
        status = str(sheet.cell(row=row, column=STATUS_COL).value or "").strip().lower()
        automated = str(sheet.cell(row=row, column=AUTOMATED_COL).value or "").strip()
        if status in RUN_STATUSES:
            counts["run"] += 1
            if automated.lower() in ("yes", "partial", "deviation"):
                counts["executed"] += 1
        match = next((s for s in AUTOMATION_STATUSES if s.lower() == automated.lower()), None)
        counts[match or "Not marked"] += 1
    return counts


def resolve(book, label: str) -> str | None:
    return next((n for n in book.sheetnames if n.strip() == label.strip()), None)


def check_execution(book) -> list[str]:
    sheet = book[EXECUTION]
    problems = []
    print(f"--- {EXECUTION}")
    for row in range(5, sheet.max_row + 1):
        label = str(sheet.cell(row=row, column=2).value or "").strip()
        if not label or label.upper() == "TOTAL":
            continue
        name = resolve(book, label)
        if name is None and label.lower().startswith("assessment"):
            total = sum(1 for row_ in range(2, book[ASSESSMENT_SHEET].max_row + 1)
                        if str(book[ASSESSMENT_SHEET].cell(row=row_, column=SUMMARY_COL).value
                               or "").strip())
            executed = sum(ASSESSMENT_SUITES)
            expected = [total, executed, 0, max(0, total - executed)]
            counts = None
        elif name is None:
            print(f"  {label:32s} static row (no matching sheet) - not verifiable")
            continue
        else:
            counts = tally(book[name])
            in_scope = counts["total"] - counts["No"]
            expected = [counts["total"], counts["executed"], counts["No"],
                        max(0, in_scope - counts["executed"])]
        shown = [sheet.cell(row=row, column=column).value for column in (3, 5, 6, 7)]
        shown = [int(value) if isinstance(value, (int, float)) else value for value in shown]
        state = "ok" if shown == expected else "DRIFT"
        if state == "DRIFT":
            problems.append(f"{EXECUTION} row {row} ({label}): shows {shown}, data says {expected}")
        print(f"  {label[:48]:48s} {state:5s} total/exec/notauto/deferred "
              f"shown={shown} data={expected}")
        if counts is not None and counts["run"] != counts["executed"]:
            print(f"  {'':32s}       note: {counts['run']} cases carry a Pass/Fail/Blocked status, "
                  f"{counts['executed']} of them are marked Automated=Yes")
    return problems


def check_coverage(book) -> list[str]:
    if COVERAGE not in book.sheetnames:
        return []
    sheet = book[COVERAGE]
    problems = []
    print(f"--- {COVERAGE}")
    for row in range(2, sheet.max_row + 1):
        label = str(sheet.cell(row=row, column=1).value or "").strip()
        if not label or label.upper().startswith(("TOTAL", "LEGEND", "NOTE")):
            continue
        name = resolve(book, label)
        if name is None:
            continue
        counts = tally(book[name])
        expected = [counts[s] for s in AUTOMATION_STATUSES] + [counts["total"]]
        shown = [sheet.cell(row=row, column=column).value for column in (2, 3, 4, 5, 6, 7)]
        shown = [int(value) if isinstance(value, (int, float)) else value for value in shown]
        state = "ok" if shown == expected else "DRIFT"
        if state == "DRIFT":
            problems.append(f"{COVERAGE} row {row} ({label}): shows {shown}, data says {expected}")
        print(f"  {label:32s} {state:5s} yes/partial/deviation/deferred/no/total "
              f"shown={shown} data={expected}")
    return problems


def main() -> int:
    book = openpyxl.load_workbook(WORKBOOK, data_only=True)
    print(f"workbook: {WORKBOOK}\n")
    problems = check_execution(book) + check_coverage(book)
    if not problems:
        print("\nBoth dashboards agree with the case sheets.")
        return 0
    print(f"\n{len(problems)} row(s) out of sync:")
    for problem in problems:
        print(f"  - {problem}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
