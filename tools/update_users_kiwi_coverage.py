"""
Update Moodle_users Kiwi sheet:
- Mark requested cases Yes with @U-user / feature refs
- Mark all other non-Yes remaining cases as No (not automatable)
- Refresh Automation_Coverage for Moodle_users
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parents[1]
XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"
SHEET = "Moodle_users"

# Excel row -> (Automated, Automation_Ref)
# One runnable tag per combined scenario. Secondary combined rows say Covered by.
YES_UPDATES = {
    2: ("Yes", "moodle_users.feature @U-user-1"),
    3: ("Yes", "moodle_users.feature @U-user-2"),
    4: ("Yes", "moodle_users.feature @U-user-5"),
    5: ("Yes", "moodle_users.feature @U-user-3"),
    6: ("Yes", "moodle_users.feature @U-user-7"),
    7: ("Yes", "moodle_users.feature @U-user-8"),
    9: ("Yes", "moodle_users.feature @U-user-28"),
    11: ("Yes", "moodle_users.feature @U-user-18"),
    12: ("Yes", "Covered by @U-user-18"),
    13: ("Yes", "moodle_users.feature @U-user-19"),
    14: ("Yes", "moodle_users.feature @U-user-29"),
    22: ("Yes", "moodle_users.feature @U-user-9"),
    23: ("Yes", "moodle_users.feature @U-user-10"),
    25: ("Yes", "moodle_users.feature @U-user-14"),
    26: ("Yes", "moodle_users.feature @U-user-11"),
    27: ("Yes", "moodle_users.feature @U-user-12"),
    28: ("Yes", "Covered by @U-user-12"),
    29: ("Yes", "moodle_users.feature @U-user-23"),
    30: ("Yes", "moodle_users.feature @U-user-24"),
    31: ("Yes", "moodle_users.feature @U-user-13"),
    33: ("Yes", "Covered by @U-user-6"),
    37: ("Yes", "moodle_users.feature @U-user-4"),
    38: ("Yes", "moodle_users.feature @U-user-6"),
    39: ("Yes", "moodle_users.feature @U-user-26"),
    40: ("Yes", "moodle_users.feature @U-user-30"),
    41: ("Yes", "moodle_users.feature @U-user-31"),
    42: ("Yes", "moodle_users.feature @U-user-15"),
    43: ("Yes", "moodle_users.feature @U-user-20"),
    44: ("Yes", "moodle_users.feature @U-user-21"),
    45: ("Yes", "moodle_users.feature @U-user-25"),
    47: ("Yes", "moodle_users.feature @U-user-16"),
    48: ("Yes", "moodle_users.feature @U-user-17"),
}

NO_REASON = "No — not automatable (mail/config/paid-enrol/activity-overlap/bulk-CSV/auth-plugin)"

# Rows that stay No (not in the automate-now list and not already covered)
# Will overwrite Deferred/blank/old No for everything not in YES_UPDATES


def recount(ws):
    c = Counter()
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not any(row):
            continue
        auto = str(row[10]).strip() if len(row) > 10 and row[10] is not None else ""
        if auto.startswith("No"):
            key = "No"
        elif auto in ("Yes", "Partial", "Deviation", "Deferred"):
            key = auto
        else:
            key = "No"
        c[key] += 1
    return c


def update_dashboard(wb, counts):
    ws = wb["Automation_Coverage"]
    total = sum(counts.values())
    yes = counts.get("Yes", 0)
    pct = f"{round(100 * yes / total)}%" if total else "0%"
    for row in ws.iter_rows(min_row=2, max_row=60):
        if row[0].value == SHEET:
            row[1].value = yes
            row[2].value = counts.get("Partial", 0)
            row[3].value = counts.get("Deviation", 0)
            row[4].value = counts.get("Deferred", 0)
            row[5].value = counts.get("No", 0)
            row[6].value = total
            row[7].value = pct
            return
    raise RuntimeError(f"{SHEET} missing on Automation_Coverage")


def main():
    wb = load_workbook(XLSX)
    ws = wb[SHEET]

    for r in range(2, ws.max_row + 1):
        title = ws.cell(r, 1).value
        summary = ws.cell(r, 2).value
        if title is None and summary is None:
            continue
        if r in YES_UPDATES:
            status, ref = YES_UPDATES[r]
            ws.cell(r, 11).value = status
            ws.cell(r, 12).value = ref
            print(f"Yes row {r}: {ref}")
        else:
            ws.cell(r, 11).value = "No"
            ws.cell(r, 12).value = NO_REASON
            print(f"No  row {r}: {(title or summary or '')!s:.60}")

    counts = recount(ws)
    update_dashboard(wb, counts)
    wb.save(XLSX)
    print("Saved", XLSX)
    print("Counts", dict(counts))


if __name__ == "__main__":
    main()
