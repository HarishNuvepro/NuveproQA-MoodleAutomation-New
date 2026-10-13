"""Tier 0: point Moodle_Tags Automation_Ref at scenario tags (@T1..), not runner @tags."""
from __future__ import annotations

from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Moodle_Tags"
FEATURE = "moodle_tags.feature"

# Row index (Excel) -> @T id per moodle_tags.feature header: @T<n> = sheet row n for creation batch.
# Rows 2–16 = T1–T15; row 28 = T27 duplicate; row 39 = T38 save without tags.
ROW_TAG: dict[int, str] = {
    2: "T1",
    3: "T2",
    4: "T3",
    5: "T4",
    6: "T5",
    7: "T6",
    8: "T7",
    9: "T8",
    10: "T9",
    11: "T10",
    12: "T11",
    13: "T12",
    14: "T13",
    15: "T14",
    16: "T15",
    28: "T27",
    39: "T38",
}

# Manage-lab batch: runner @tagmgmt rows that should cite concrete @T tags.
MANAGE_FEATURE = "moodle_tags_manage.feature"
MANAGE_ROW_TAG: dict[int, str] = {
    17: "T16",  # rename / edit tag
    19: "T18",  # delete tag
    20: "T19",  # standard tag suggested in activity field
    21: "T20",  # all standard tags suggested
    22: "T21",  # deleted tag not suggested
    23: "T22",  # standard mark removed — not suggested
    24: "T23",  # renamed tag suggested under new name
    25: "T26",  # after combine — surviving tag suggested
    26: "T25",  # combine more than two tags
    27: "T26",  # merged tag no longer suggested (same flow as combine)
    29: "T28",  # delete multiple tags
    30: "T29",  # combine with single selection unchanged
}


def main() -> None:
    book = openpyxl.load_workbook(WORKBOOK)
    sheet = book[SHEET]
    updated = 0
    for row, tag in ROW_TAG.items():
        ref = f"{FEATURE} @{tag}"
        old = str(sheet.cell(row, 12).value or "")
        if old != ref:
            sheet.cell(row, 12).value = ref
            updated += 1
            print(f"row {row}: {old!r} -> {ref}")
    for row, tag in MANAGE_ROW_TAG.items():
        auto = str(sheet.cell(row, 11).value or "").strip().lower()
        if auto != "yes":
            continue
        ref = f"{MANAGE_FEATURE} @{tag}"
        old = str(sheet.cell(row, 12).value or "")
        if "@tagmgmt" in old or old != ref:
            sheet.cell(row, 12).value = ref
            updated += 1
            print(f"row {row}: {old!r} -> {ref}")
    book.save(WORKBOOK)
    print(f"updated {updated} Automation_Ref cells on {SHEET}")


if __name__ == "__main__":
    main()
