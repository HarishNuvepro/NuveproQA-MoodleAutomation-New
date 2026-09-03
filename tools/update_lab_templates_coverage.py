"""Record exact verified Lab Templates coverage in the Kiwi master."""
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Moodle_Lab_Templates"

# Workbook row -> exact scenario reference verified green.
LABTEMPLATE_TAGS = (
    1, 2, 3, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24,
    25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 38, 40, 41,
    42, 43, 44, 45, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57,
    58, 62, 76, 80,
)
VERIFIED = {
    tag + 1: f"labtemplates.feature @LT-{tag}"
    for tag in LABTEMPLATE_TAGS
}
VERIFIED.update({
    5: "sandbox_user_functionality.feature @LT-4",
    6: "sandbox_user_functionality.feature @LT-5",
    8: "sandbox_user_functionality.feature @LT-7; sandbox_actions.feature @LT-7",
    9: "sandbox_user_functionality.feature @LT-8",
    11: "sandbox_user_functionality.feature @LT-10",
    61: "sandbox_user_functionality.feature @LT-60",
    67: "sandbox_user_functionality.feature @LT-66",
    68: "sandbox_actions.feature @LT-67",
    74: "sandbox_actions.feature @LT-73",
    71: "sandbox_actions.feature @LT-70",
    84: "labtemplates.feature @CGAP-LT-1",
    85: "labtemplates.feature @CGAP-LT-2",
    87: "labtemplates.feature @CGAP-LT-4",
})

DEVIATIONS = {}

PARTIAL = {}

BLOCKED = {
    47: "No — browser errors covered by @LT-46; server-log verification needs log access",
    70: "No — Sandbox Create/Start covered by @LT-69; Guided/Assessment/Unpark need extra fixtures",
    78: "No — Create-failure recovery covered by @LT-77; Start/Unpark failure injection unavailable",
    62: "No Park/Unpark API and PLAYGROUND_VM_CMID is not configured",
    64: "Real Create timing outcome is unspecified and needs a dedicated slow-plan fixture",
    65: "Real Start timing outcome is unspecified and needs a dedicated slow-plan fixture",
    66: "No Park/Unpark API and no parked VM fixture",
    69: "CloudLabs client and Moodle UI expose Stop, but no Park action",
    72: "Proctored progress scenario is scripted but needs a working proctoring launch fixture",
    73: "Requires Orgo suspend/resume access; no API or UI automation endpoint is available",
    75: "Requires SSO identity-provider credentials and login flow",
    76: "Requires an LTI consumer and signed launch flow",
    79: "Requires a lab-create webhook inspection endpoint or server-log access",
    80: "Low priority and requires mutating/restoring the site-wide theme during transient progress",
    7: "No — Plan 1747 returns no SubmitChallenge action-input schema",
    10: "No — notification is enabled but activity reports Lab duration not Configured",
    17: "No — view mode still renders Edit/Delete (required read-only page is not implemented)",
    38: "No — problem-statement template selection has no Plan/Team/Catalog panel",
    40: "No — restored course contains no CloudLabs activity to retain Plan/Team/Catalog",
    60: "No — deployed console has no four-step Create progress markup",
    82: "No — Lab Templates listing has no bulk export/import controls",
    83: "No — unbounded interval 999999999 is accepted; no max validation",
    86: "No — non-admin labtemplates.php returns a blank page, not Moodle access denied",
}


COVERAGE = "Automation_Coverage"
COVERAGE_FIRST_ROW = 2
STATES = ("yes", "partial", "deviation", "deferred", "no")


def refresh_coverage_row(book) -> None:
    """Retype this module's Automation_Coverage row (QA-owned sheet, typed values).

    Only the Lab Templates row and the TOTAL are touched; the other rows keep
    whatever QA has, so TOTAL stays consistent with what the sheet displays.
    """
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
    target = next(row for row in range(COVERAGE_FIRST_ROW, coverage.max_row + 1)
                  if str(coverage.cell(row, 1).value or "").strip() == SHEET)
    values = [counts[state] for state in STATES] + [total]
    for offset, value in enumerate(values, start=2):
        coverage.cell(target, offset).value = value
    coverage.cell(target, 8).value = f"{round(100 * counts['yes'] / total)}%" if total else "0%"

    total_row = next(row for row in range(COVERAGE_FIRST_ROW, coverage.max_row + 1)
                     if str(coverage.cell(row, 1).value or "").upper().startswith("TOTAL"))
    sums = [0] * 6
    for row in range(COVERAGE_FIRST_ROW, total_row):
        for index in range(6):
            value = coverage.cell(row, index + 2).value
            sums[index] += value if isinstance(value, (int, float)) else 0
    for index, value in enumerate(sums, start=2):
        coverage.cell(total_row, index).value = value
    coverage.cell(total_row, 8).value = f"{round(100 * sums[0] / sums[5])}%" if sums[5] else "0%"
    print(f"{COVERAGE} row {target}: {values} ({coverage.cell(target, 8).value}); "
          f"TOTAL now {sums}")


def main() -> None:
    book = openpyxl.load_workbook(WORKBOOK)
    sheet = book[SHEET]

    for row, reference in VERIFIED.items():
        sheet.cell(row, 11).value = "Yes"
        sheet.cell(row, 12).value = reference
        # The @labtemplates suite executed these cases green, which is what the
        # dashboard's Executed column counts (Automated=Yes AND a run status).
        sheet.cell(row, 7).value = "Pass"

    for row, reference in DEVIATIONS.items():
        sheet.cell(row, 11).value = "Deviation"
        sheet.cell(row, 12).value = reference
        sheet.cell(row, 7).value = "Fail"

    for row, reference in PARTIAL.items():
        sheet.cell(row, 11).value = "Partial"
        sheet.cell(row, 12).value = reference

    for row, reason in BLOCKED.items():
        sheet.cell(row, 11).value = "No"
        sheet.cell(row, 12).value = reason

    refresh_coverage_row(book)
    book.save(WORKBOOK)
    print(f"marked {len(VERIFIED)} Lab Templates cases Yes, {len(PARTIAL)} Partial, "
          f"and {len(DEVIATIONS)} Deviations")


if __name__ == "__main__":
    main()
