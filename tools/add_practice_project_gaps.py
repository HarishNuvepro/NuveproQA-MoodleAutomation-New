"""Append Practice Project gap rows and mark dormant Get Progress cases.

Does not mark newly scripted rows Yes until the suite is green.
"""
from __future__ import annotations

from copy import copy
from datetime import date
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Practice_Project"
COVERAGE = "Automation_Coverage"
NOTE = f"Automation gap (derived control inventory + state machine) {date.today().isoformat()}"

# Dormant console Get Progress (commented out in guided-controlpanel.mustache).
GET_PROGRESS_NO = {
    26: "No — cron-disabled auto-submit needs cron control (High)",
    132: "No — admin Submit Attempt Get Progress needs a live progress-check fixture",
    133: "No — Get Progress on console is dormant in guided-controlpanel.mustache",
    134: "No — Get Progress checkpoints UI is dormant",
    135: "No — manual Get Progress on console is dormant",
    155: "No — Get Progress button is commented out of the lab console",
    156: "No — Get Progress checkpoint status is dormant",
    157: "No — auto Get Progress via cron is not automatable without cron control",
    158: "No — Get Progress related console flow is dormant",
    161: "No — auto-complete when timeline ends needs cron/time control",
    174: "No — Get Progress flow is dormant on the console",
}

# Scenarios are authored and config-gated, but these cases cannot be marked automated until their
# required external fixture is available and the scenario passes.
CONVERTED_NO = {
    13: "No — Credentials/PDF/help need a running configured lab (instructions/duration already in @PP-13)",
    56: "No — Not Attempted/Started override status needs an in-progress fixture (@PP-56 covers Completed)",
    177: "No — exhaust attempts needs a provisioned lab and attempt ceiling",
    178: "No — Complete is dormant (same product gap as PP-31/32)",
    179: "No — restart-in-gap needs a provisioned lab and cooldown",
    190: "No — override-card start needs a running lab plus override fixture",
    191: "No — last-attempt Stop/Delete needs a provisioned lab",
    192: "No — outside date window needs a dedicated dated activity (must not mutate GUIDED_CMID)",
}
CONVERTED_NO.update({
    row: "guided_lab.feature @PP-%s — converted; CloudLabs admin login currently returns HTTP 401" % row
    for row in (11, 12, 20, 21, 27, 29, 33, 37, 42, 48, 139)
})
CONVERTED_NO.update({
    row: "practice_project_variants.feature @PP-%s — converted; required variant CMID is not configured" % row
    for row in (14, 19, 22, 23, 24, 25, 28, 30, 38, 45, 136, 140)
})
CONVERTED_NO.update({
    row: "practice_project.feature @PP-%s — converted; WS token lacks mod_cloudlabs_get_guided_project_result access" % row
    for row in range(59, 69)
})
CONVERTED_NO.update({
    row: "practice_project_reports.feature @PP-%s — converted; site configurable-report URL/artifact is unavailable" % row
    for row in (
        69, 70, 71, 72, *range(73, 93), *range(94, 113), 137, 138, 142
    )
})

# Green 2026-08-29 @practice_project first batch.
VERIFIED = {
    2: "practice_project.feature @PP-2 @CGAP-PP-1",
    3: "practice_project.feature @PP-3",
    4: "practice_project.feature @PP-4",
    5: "practice_project.feature @PP-5",
    6: "practice_project.feature @PP-6",
    7: "practice_project.feature @PP-7",
    8: "practice_project.feature @PP-8",
    9: "practice_project_console.feature @PP-9",
    10: "practice_project_console.feature @PP-10",
    15: "practice_project.feature @PP-15",
    16: "practice_project.feature @PP-16",
    17: "practice_project.feature @PP-17",
    18: "practice_project.feature @PP-18",
    34: "practice_project_console.feature @PP-34",
    35: "practice_project.feature @PP-35",
    36: "practice_project.feature @PP-36",
    39: "practice_project.feature @PP-39",
    40: "practice_project.feature @PP-40",
    41: "practice_project.feature @PP-41",
    43: "practice_project.feature @PP-43",
    44: "practice_project_console.feature @PP-44",
    46: "practice_project.feature @PP-46",
    47: "practice_project.feature @PP-47",
    49: "practice_project.feature @PP-49",
    50: "practice_project.feature @PP-50",
    51: "practice_project.feature @PP-51",
    53: "practice_project.feature @PP-53",
    54: "practice_project.feature @PP-54",
    55: "practice_project.feature @PP-55",
    57: "practice_project.feature @PP-57",
    58: "practice_project.feature @PP-58",
    93: "practice_project.feature @PP-93",
    113: "practice_project.feature @PP-113",
    114: "practice_project.feature @PP-114",
    115: "practice_project.feature @PP-115",
    116: "practice_project.feature @PP-116",
    118: "practice_project.feature @PP-118",
    119: "practice_project.feature @PP-119",
    120: "practice_project.feature @PP-120",
    121: "practice_project.feature @PP-121",
    122: "practice_project.feature @PP-122",
    124: "practice_project.feature @PP-124",
    123: "practice_project.feature @PP-123",
    125: "practice_project.feature @PP-125",
    126: "practice_project.feature @PP-126",
    127: "practice_project.feature @PP-127",
    128: "practice_project.feature @PP-128",
    129: "practice_project.feature @PP-129",
    130: "practice_project.feature @PP-130",
    141: "practice_project.feature @PP-141",
    187: "practice_project.feature @CGAP-PP-1",
    188: "practice_project.feature @CGAP-PP-2",
    181: "guided_gaps.feature @CGAP-PGG-13",
    182: "guided_gaps.feature @CGAP-PGG-14",
    183: "guided_gaps.feature @CGAP-PGG-15",
    184: "guided_gaps.feature @CGAP-PGG-17",
    186: "practice_project.feature @CGAP-PGG-20",
    189: "practice_project.feature @CGAP-PP-3",
    152: "guided_lab.feature @guidedlab | control panel shown after start",
    175: "guided_lab.feature @CGAP-PGG-1 @guidedlab | Continue reopens the control panel",
    176: "guided_attempts.feature @CGAP-PGG-2 @guidedattempts | New Attempt after cooldown",
    180: "guided_lab.feature @CGAP-PGG-8 @guidedlab | start attempt reaches Running",
}

PARTIAL = {}

# Product gaps: intended behaviour is missing on the deployed plugin. Not automatable.
DEVIATIONS = {}
PRODUCT_NO = {
    31: "No — Complete is commented out of guided-landing.mustache",
    32: "No — Complete is commented out of guided-landing.mustache",
    52: "No — override form has 1-10 and Unlimited, no No Attempts option",
    117: "No — participants table permits multiple checkbox selections",
    131: "No — Check Details does not open checkpoint results",
    185: "No — export types are set but bootstrap-table does not render the export dropdown",
}
CONVERTED_NO.update(PRODUCT_NO)

GAPS = [
    ("CGAP-PP-1", "Medium",
     "Verify Practice Project / guided is selectable as the cloudlabs lab type on Add activity, and guided-only fields appear.",
     "Practice Project lab type radio (labtype=guided) is selected and guided-only fields render."),
    ("CGAP-PP-2", "High",
     "On User Practice Project admin page, open Reports then Download, choose a group, and confirm the CSV download completes with participant data.",
     "Consolidated practice project report file is downloaded from the participants page."),
    ("CGAP-PP-3", "Medium",
     "On User Practice Project admin page, open Reports then Send Email, enter a recipient, select a group, and confirm send succeeds.",
     "Practice project report email send completes with a success notification."),
    ("CGAP-PP-4", "High",
     "A student with an active guided-project override sees the override card and can start the override attempt from the landing.",
     "Override attempt starts and the control panel opens."),
    ("CGAP-PP-5", "Medium",
     "With actiontobeperformed configured (Stop or Delete), after the last allowed attempt is submitted the configured lab action runs.",
     "Lab reaches the configured Stopped or Deleted end state after the last submit."),
    ("CGAP-PP-6", "High",
     "An enrolled student cannot start a practice project before submissionfromdate or after submissionduedate.",
     "Start is blocked outside the availability window; complementary to admin form validation."),
]


def existing_titles(sheet) -> set[str]:
    found = set()
    for row in range(2, sheet.max_row + 1):
        title = str(sheet.cell(row, 1).value or "").strip()
        if title:
            found.add(title)
    return found


def append_gaps(sheet) -> int:
    titles = existing_titles(sheet)
    styles = [copy(sheet.cell(2, column)._style) for column in range(1, 13)]
    added = 0
    row = sheet.max_row + 1
    for title, priority, summary, expected in GAPS:
        if title in titles:
            continue
        sheet.cell(row, 1).value = title
        sheet.cell(row, 2).value = summary
        sheet.cell(row, 4).value = "Moodle"
        sheet.cell(row, 5).value = "Practice_Project"
        sheet.cell(row, 6).value = priority
        sheet.cell(row, 9).value = expected
        sheet.cell(row, 10).value = "Core"
        sheet.cell(row, 11).value = "Deferred"
        sheet.cell(row, 12).value = NOTE
        for column in range(1, 13):
            sheet.cell(row, column)._style = styles[column - 1]
        print(f"  appended {title} at row {row}")
        row += 1
        added += 1
    return added


def mark_blocked(sheet) -> int:
    marked = 0
    for row, reason in GET_PROGRESS_NO.items():
        current = str(sheet.cell(row, 11).value or "").strip()
        if current.lower() == "yes":
            continue
        sheet.cell(row, 11).value = "No"
        sheet.cell(row, 12).value = reason
        sheet.cell(row, 7).value = "Blocked"
        marked += 1
    for row, reason in CONVERTED_NO.items():
        sheet.cell(row, 11).value = "No"
        sheet.cell(row, 12).value = reason
        sheet.cell(row, 7).value = "Blocked"
        marked += 1
    return marked


def mark_verified(sheet) -> None:
    for row, reference in VERIFIED.items():
        sheet.cell(row, 11).value = "Yes"
        sheet.cell(row, 12).value = reference
        sheet.cell(row, 7).value = "Pass"
    for row, reference in PARTIAL.items():
        sheet.cell(row, 11).value = "Partial"
        sheet.cell(row, 12).value = reference
    for row, reference in DEVIATIONS.items():
        sheet.cell(row, 7).value = "Fail"
        sheet.cell(row, 11).value = "Deviation"
        sheet.cell(row, 12).value = reference


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
        print("no Automation_Coverage sheet")
        return
    coverage = book[COVERAGE]
    target = None
    for row in range(2, coverage.max_row + 1):
        if str(coverage.cell(row, 1).value or "").strip() == SHEET:
            target = row
            break
    if target is None:
        print("Practice_Project row not on Automation_Coverage — skipped")
        return
    values = [counts[state] for state in states] + [total]
    for offset, value in enumerate(values, start=2):
        coverage.cell(target, offset).value = value
    coverage.cell(target, 8).value = f"{round(100 * counts['yes'] / total)}%" if total else "0%"
    print(f"{COVERAGE} {SHEET}: {values} ({coverage.cell(target, 8).value})")


def main() -> None:
    book = openpyxl.load_workbook(WORKBOOK)
    sheet = book[SHEET]
    added = append_gaps(sheet)
    blocked = mark_blocked(sheet)
    mark_verified(sheet)
    refresh_coverage_row(book)
    book.save(WORKBOOK)
    print(f"appended {added} CGAP-PP rows; marked {blocked} Get Progress / cron cases No; "
          f"verified {len(VERIFIED)} Yes")


if __name__ == "__main__":
    main()
