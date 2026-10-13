"""Set Moodle_Sandbox_Labs Case_Status so Regression_Dashboard Executed counts.
Updates the workbook in place. Yes+ran = Pass; last-known-fail tags = Fail; Deferred unchanged.
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parents[1]
XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"
SHEET = "Moodle_Sandbox_Labs"

# Optional: tags to keep as Fail when re-stamping after a partial run (empty = all Yes → Pass).
FAIL_TAGS: tuple[str, ...] = ()


def recount_auto(ws):
    c = Counter()
    for r in range(2, ws.max_row + 1):
        if ws.cell(r, 1).value is None and ws.cell(r, 2).value is None:
            continue
        auto = str(ws.cell(r, 11).value or "").strip()
        if auto.startswith("No"):
            c["No"] += 1
        elif auto in ("Yes", "Partial", "Deviation", "Deferred"):
            c[auto] += 1
        else:
            c["No"] += 1
    return c


def update_automation_coverage(wb, counts):
    ws = wb["Automation_Coverage"]
    total = sum(counts.values())
    yes = counts.get("Yes", 0)
    pct = f"{round(100 * yes / total)}%" if total else "0%"
    sandbox_row = None
    total_row = None
    for row in ws.iter_rows(min_row=2, max_row=30):
        name = row[0].value
        if name == SHEET:
            sandbox_row = row
        elif name and str(name).startswith("TOTAL"):
            total_row = row
    if sandbox_row is None:
        raise RuntimeError(f"{SHEET} missing on Automation_Coverage")
    sandbox_row[1].value = yes
    sandbox_row[2].value = counts.get("Partial", 0)
    sandbox_row[3].value = counts.get("Deviation", 0)
    sandbox_row[4].value = counts.get("Deferred", 0)
    sandbox_row[5].value = counts.get("No", 0)
    sandbox_row[6].value = total
    sandbox_row[7].value = pct
    if total_row is not None:
        acc = Counter()
        for row in ws.iter_rows(min_row=2, max_row=17):
            if row[0].value is None or str(row[0].value).startswith("TOTAL"):
                continue
            acc["Yes"] += int(row[1].value or 0)
            acc["Partial"] += int(row[2].value or 0)
            acc["Deviation"] += int(row[3].value or 0)
            acc["Deferred"] += int(row[4].value or 0)
            acc["No"] += int(row[5].value or 0)
        t = sum(acc.values())
        total_row[1].value = acc["Yes"]
        total_row[2].value = acc["Partial"]
        total_row[3].value = acc["Deviation"]
        total_row[4].value = acc["Deferred"]
        total_row[5].value = acc["No"]
        total_row[6].value = t
        total_row[7].value = f"{round(100 * acc['Yes'] / t)}%" if t else "0%"


def ref_is_fail(ref: str) -> bool:
    r = ref or ""
    return any(tag in r for tag in FAIL_TAGS)


def main():
    wb = load_workbook(XLSX)
    ws = wb[SHEET]
    status_counts = Counter()
    for r in range(2, ws.max_row + 1):
        if ws.cell(r, 1).value is None and ws.cell(r, 2).value is None:
            continue
        auto = str(ws.cell(r, 11).value or "").strip()
        ref = str(ws.cell(r, 12).value or "")
        if auto == "Yes":
            status = "Fail" if ref_is_fail(ref) else "Pass"
            ws.cell(r, 7).value = status
            status_counts[status] += 1
        elif auto == "Deferred":
            # Not in Executed (formula requires Automated=Yes). Leave status as Blocked
            # so the sheet shows they were reviewed, not forgotten.
            ws.cell(r, 7).value = "Blocked"
            status_counts["Blocked"] += 1
        else:
            status_counts[f"skip:{auto or 'blank'}"] += 1

    plan = wb["Release_Regression_Plan"]
    for r in range(9, plan.max_row + 1):
        if plan.cell(r, 2).value == SHEET:
            plan.cell(r, 7).value = "Yes"
            print(f"Release_Regression_Plan row {r}: Impacted This Release? = Yes")
            break

    counts = recount_auto(ws)
    update_automation_coverage(wb, counts)
    tmp = XLSX.with_suffix(".xlsx.tmp")
    wb.save(tmp)
    wb.close()
    tmp.replace(XLSX)
    print("Saved", XLSX)
    print("Case_Status written:", dict(status_counts))
    print("Automation_Coverage Moodle_Sandbox_Labs:", dict(counts),
          "total", sum(counts.values()))


if __name__ == "__main__":
    main()
