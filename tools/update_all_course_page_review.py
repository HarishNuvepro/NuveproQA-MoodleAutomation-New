"""
All Course Page sheet review (gate before scripting).

- Update the Kiwi workbook in place (no backup copies by request)
- Add Automated + Automation_Ref
- Mark CONVERT (same-flow combine groups), Deferred, and not-automatable
- Append CGAP-ACP-* rows (duplicate-guard)
- Recalculate Automation_Coverage including this sheet
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from openpyxl import load_workbook
from openpyxl.styles import Alignment, Font

ROOT = Path(__file__).resolve().parents[1]
XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"
SHEET = "All Course Page "
# Dashboard position requested by QA: directly below Moodle_Sandbox_Labs.
DASHBOARD_AFTER = "Moodle_Sandbox_Labs"

# Excel row → (Automated, Automation_Ref)
# ACP-n = row-1 (ACP-1 is the first case).
S = {}

def put(rows: list[int], auto: str, scenario: str, note: str) -> None:
    # Yes groups: first row is the runnable feature tag; later rows say Covered by.
    if auto == "Yes":
        tags = [part for part in scenario.split() if part.startswith("@ACP-S") or part.startswith("@CGAP-ACP-")]
        tag = tags[0] if tags else scenario.split()[-1]
        feature = "all_courses.feature"
        S[rows[0]] = (auto, f"{feature} {tag}")
        for r in rows[1:]:
            S[r] = (auto, f"Covered by {tag}")
        return
    ids = " ".join(f"ACP-{r-1}" for r in rows)
    ref = f"{scenario} | covers {ids} | {note}"
    for r in rows:
        S[r] = (auto, ref)


# --- Automated (green 2026-08-25/26) ---
put([2, 3, 19, 20], "No",
    "Not converting @ACP-1 @ACP-2 @ACP-18 @ACP-19",
    "Live All Courses has no Manage courses / Add a new course chrome; @ACP-S1 asserts those links are absent")
put([4], "Yes",
    "all_courses.feature @ACP-S1 @ACP-3",
    "Green 2026-08-26: admin sees courses, categories filter, and search; Manage/Add are not on the page")
put([5, 6, 7, 8, 9, 54, 55, 56], "Yes",
    "all_courses.feature @ACP-S2 @ACP-4 @ACP-5 @ACP-6 @ACP-7 @ACP-8 @ACP-53 @ACP-54 @ACP-55",
    "Green 2026-08-26: search character classes return matching courses in alphabetical order; no-match is empty")
put([10, 11, 13, 29, 96], "Yes",
    "all_courses.feature @ACP-S3 @ACP-9 @ACP-10 @ACP-12 @ACP-28 @ACP-95",
    "Green 2026-08-26: course cards match name, category, image, and description across batches")
put([12], "No",
    "all_courses.feature @ACP-S3 @ACP-11",
    "Not automatable: Duration is not rendered as a separate field on the live course card")
put([14, 23], "Yes",
    "all_courses.feature @ACP-S4 @ACP-13 @ACP-22",
    "Green 2026-08-26: hidden course stays listed; opening it shows unavailable to students")
put([15, 16], "No",
    "Not automatable @ACP-S5",
    "Needs a live in-progress course-creation job for pending-approval enable/disable")
put([17], "Yes",
    "all_courses.feature @ACP-S6 @ACP-16",
    "Green 2026-08-26: student two completed course is listed at 100 percent")
put([18], "Yes",
    "all_courses.feature @ACP-S7 @ACP-17",
    "Green 2026-08-25: created course is deleted through Moodle UI and absent from All Courses")
put([21], "Yes",
    "all_courses.feature @ACP-S8 @ACP-20",
    "Green 2026-08-25: valid course save completes and the new course is listed in All Courses")
put([22], "Yes",
    "all_courses.feature @ACP-S9 @ACP-21",
    "Green 2026-08-25: missing required course name is rejected and no invalid course is listed")
put([24], "Yes",
    "all_courses.feature @ACP-S10 @ACP-23",
    "Green 2026-08-26: duplicated course is listed on All Courses")
put([25, 26], "Yes",
    "all_courses.feature @ACP-S11 @ACP-24 @ACP-25",
    "Green 2026-08-26: original and duplicated courses move to another category and are listed there")
put([27, 28], "Yes",
    "all_courses.feature @ACP-S12 @ACP-26 @ACP-27",
    "Green 2026-08-26: backup downloads an .mbz and restore lists the course under the destination category")
put(list(range(37, 46)), "Yes",
    "all_courses.feature @ACP-S14 @ACP-36 @ACP-37 @ACP-38 @ACP-39 @ACP-40 @ACP-41 @ACP-42 @ACP-43 @ACP-44",
    "Green 2026-08-26: nested parent/sub categories and their courses are listed correctly")
put([46, 47, 48, 49, 50, 51, 52, 53, 57, 58, 59], "Yes",
    "all_courses.feature @ACP-S15 @ACP-45 @ACP-46 @ACP-47 @ACP-48 @ACP-49 @ACP-50 @ACP-51 @ACP-52 @ACP-56 @ACP-57 @ACP-58",
    "Green 2026-08-26: alphabetical order survives refresh, add, delete, hide/show, and sub-filter")
put([60], "Yes",
    "all_courses.feature @ACP-S16 @ACP-59",
    "Green 2026-08-26: imported course from downloaded backup appears in alphabetical order")
put([61, 62, 63, 64, 65, 66, 68, 69, 70, 71, 72, 83, 84], "Yes",
    "all_courses.feature @ACP-S17 @ACP-60 @ACP-61 @ACP-62 @ACP-63 @ACP-64 @ACP-65 @ACP-67 @ACP-68 @ACP-69 @ACP-70 @ACP-71 @ACP-82 @ACP-83",
    "Green 2026-08-26: hide category on Manage courses and categories; admin still sees it, student does not; show restores both")
put([73, 74], "Yes",
    "all_courses.feature @ACP-S18 @ACP-72 @ACP-73",
    "Green 2026-08-26: unenrolled user still sees All Courses and the categories filter")
put([75, 79, 80, 81, 82, 85], "Yes",
    "all_courses.feature @ACP-S19 @ACP-74 @ACP-78 @ACP-79 @ACP-80 @ACP-81 @ACP-84",
    "Green 2026-08-26: self-enrol Enrol me follows allow-new Yes/No and is hidden once already enrolled")
put([86, 87, 88, 89, 90, 91, 92, 93, 101, 104], "Yes",
    "all_courses.feature @ACP-S20 @ACP-85 @ACP-86 @ACP-87 @ACP-88 @ACP-89 @ACP-90 @ACP-91 @ACP-92 @ACP-100 @ACP-103",
    "Green 2026-08-26: infinite scroll loads unique batches with a loader, no pager, and no extra request after the end")
put([95], "Yes",
    "all_courses.feature @ACP-S21 @ACP-94",
    "Green 2026-08-26: refresh after infinite scroll resets All Courses to the first batch")
put([98], "No",
    "Not automatable @ACP-S22",
    "Empty catalog needs a zero-course site; lived-in setups always have at least one course — cannot isolate on the current environment")
put([99, 100], "Yes",
    "all_courses.feature @ACP-S23 @ACP-98 @ACP-99",
    "Green 2026-08-26: scrolling to the last card shows No more courses")
put([102], "Yes",
    "all_courses.feature @ACP-S24 @ACP-101 @provisioning",
    "Green 2026-08-26: student card 0%, Start Sandbox then Start Lab on the console, card progress updates")
put([103], "Yes",
    "all_courses.feature @ACP-S25 @ACP-102",
    "Green 2026-08-26: search and category filters reset the list; infinite scroll stays inside the filter")
put([105], "Yes",
    "all_courses.feature @ACP-S26 @ACP-104",
    "Green 2026-08-25: SQL/script payloads treated as text; no script execution or DB/page error")
put([106], "Yes",
    "all_courses.feature @ACP-S27 @ACP-105",
    "Green 2026-08-26: a long course name stays readable without breaking the card")

put(list(range(30, 36)), "Yes",
    "all_courses.feature @ACP-S13 @ACP-29 @ACP-30 @ACP-31 @ACP-32 @ACP-33 @ACP-34",
    "Long description Read More popup; short description has no Read More; both cards show an image")
put([67], "No",
    "Not automatable — role scope",
    "Manager/teacher hidden-category visibility. Standing: admin + student only")
put([94], "No",
    "Not automatable @ACP-S28",
    "Slow-3G infinite-scroll soak is too flaky for a stable first-batch assertion")
put([107], "Yes",
    "all_courses.feature @ACP-S29 @ACP-106",
    "Mobile viewport still shows courses, categories, and search")

# --- Not automatable / wrong sheet ---
put([36], "No",
    "Not automatable",
    "Visual overlap of bold/special-character description — subjective layout, no stable outcome assert")
put([76, 77, 78], "No",
    "Not converting on this sheet",
    "Define-role CRUD (archetype student, missing names, duplicate names) belongs on Moodle_Roles — not All Courses page")
put([97], "No",
    "Not automatable",
    "Subjective scroll smoothness / no-stutter / no-lag — not a deterministic UI outcome")

CGAP_ROWS = [
    ("CGAP-ACP-1",
     "Student cannot see Manage courses or Add a new course on All Courses",
     "Automation gap (derived control inventory, admin vs student chrome) 2026-08-25",
     "High",
     "Yes",
     "all_courses.feature @CGAP-ACP-1"),
    ("CGAP-ACP-2",
     "Clearing All Courses search restores the full catalog",
     "Automation gap (derived search state machine re-entry) 2026-08-25",
     "Medium",
     "Yes",
     "all_courses.feature @CGAP-ACP-2"),
    ("CGAP-ACP-3",
     "Unauthenticated visitor can browse the public All Courses catalog without login",
     "Automation gap (corrected from assumed auth gate after live theme inspection) 2026-08-25",
     "High",
     "Yes",
     "all_courses.feature @CGAP-ACP-3"),
    ("CGAP-ACP-4",
     "Hiding then showing a course lists it again on All Courses",
     "Automation gap (derived hide/show re-entry; hide-only was in ACP-13/ACP-22) 2026-08-25",
     "Medium",
     "Yes",
     "all_courses.feature @CGAP-ACP-4"),
]


def recount(ws) -> dict[str, int]:
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


def find_row(ws, label: str) -> int | None:
    for r in range(2, ws.max_row + 1):
        name = ws.cell(r, 1).value
        if name and str(name).strip() == label:
            return r
    return None


def update_coverage_dashboard(wb, counts: dict[str, int]) -> None:
    ws = wb["Automation_Coverage"]
    total = sum(counts.values())
    yes = counts.get("Yes", 0)
    pct = f"{round(100 * yes / total)}%" if total else "0%"
    values = (
        yes,
        counts.get("Partial", 0),
        counts.get("Deviation", 0),
        counts.get("Deferred", 0),
        counts.get("No", 0),
        total,
        pct,
    )

    # The sheet's row belongs directly under Moodle_Sandbox_Labs; drop any stale copy elsewhere
    # so the dashboard never lists All Course Page twice.
    while True:
        stale = find_row(ws, SHEET.strip())
        anchor = find_row(ws, DASHBOARD_AFTER)
        if stale is None or (anchor is not None and stale == anchor + 1):
            break
        ws.delete_rows(stale)

    target = find_row(ws, SHEET.strip())
    if target is None:
        anchor = find_row(ws, DASHBOARD_AFTER)
        target = (anchor + 1) if anchor else (find_row(ws, "TOTAL (worked sheets)") or ws.max_row)
        ws.insert_rows(target)
        ws.cell(target, 1).value = SHEET.strip()
    for i, v in enumerate(values, start=2):
        ws.cell(target, i).value = v

    total_row_idx = None
    for r in range(2, ws.max_row + 1):
        name = ws.cell(r, 1).value
        if name and str(name).startswith("TOTAL"):
            total_row_idx = r
    if total_row_idx is None:
        return
    acc = Counter()
    for r in range(2, total_row_idx):
        name = ws.cell(r, 1).value
        if not name or str(name).startswith("TOTAL"):
            continue
        acc["Yes"] += int(ws.cell(r, 2).value or 0)
        acc["Partial"] += int(ws.cell(r, 3).value or 0)
        acc["Deviation"] += int(ws.cell(r, 4).value or 0)
        acc["Deferred"] += int(ws.cell(r, 5).value or 0)
        acc["No"] += int(ws.cell(r, 6).value or 0)
    t = sum(acc.values())
    ws.cell(total_row_idx, 2).value = acc["Yes"]
    ws.cell(total_row_idx, 3).value = acc["Partial"]
    ws.cell(total_row_idx, 4).value = acc["Deviation"]
    ws.cell(total_row_idx, 5).value = acc["Deferred"]
    ws.cell(total_row_idx, 6).value = acc["No"]
    ws.cell(total_row_idx, 7).value = t
    ws.cell(total_row_idx, 8).value = f"{round(100 * acc['Yes'] / t)}%" if t else "0%"


def has_title(ws, title: str) -> bool:
    for row in ws.iter_rows(min_row=2, max_col=1, values_only=True):
        if row[0] == title:
            return True
    return False


def main() -> None:
    missing = [r for r in range(2, 108) if r not in S]
    if missing:
        raise SystemExit(f"unmapped excel rows: {missing}")
    wb = load_workbook(XLSX)
    ws = wb[SHEET]
    ws.cell(1, 11).value = "Automated"
    ws.cell(1, 12).value = "Automation_Ref"
    ws.cell(1, 11).font = Font(bold=True)
    ws.cell(1, 12).font = Font(bold=True)
    wrap = Alignment(wrap_text=True, vertical="top")
    for r, (auto, ref) in S.items():
        ws.cell(r, 11).value = auto
        cell = ws.cell(r, 12)
        cell.value = ref
        cell.alignment = wrap
        if auto == "No" and str(ws.cell(r, 7).value or "").strip().lower() not in (
                "pass", "fail", "failed", "blocked"):
            ws.cell(r, 7).value = "NA"
    next_row = ws.max_row + 1
    appended = 0
    for title, summary, comment, pri, auto, ref in CGAP_ROWS:
        existing = find_row(ws, title)
        if existing:
            ws.cell(existing, 11).value = auto
            ws.cell(existing, 12).value = ref
            print("updated existing", title, auto)
            continue
        ws.cell(next_row, 1).value = title
        ws.cell(next_row, 2).value = summary
        ws.cell(next_row, 3).value = comment
        ws.cell(next_row, 4).value = "Moodle"
        ws.cell(next_row, 5).value = "All Course Page"
        ws.cell(next_row, 6).value = pri
        ws.cell(next_row, 7).value = "PROPOSED"
        ws.cell(next_row, 8).value = comment
        ws.cell(next_row, 9).value = summary
        ws.cell(next_row, 10).value = "Core" if pri == "High" else "Extended"
        ws.cell(next_row, 11).value = auto
        ws.cell(next_row, 12).value = ref
        ws.cell(next_row, 12).alignment = wrap
        next_row += 1
        appended += 1
    counts = recount(ws)
    update_coverage_dashboard(wb, counts)
    tmp = XLSX.with_suffix(".xlsx.tmp")
    wb.save(tmp)
    wb.close()
    tmp.replace(XLSX)
    print("Saved", XLSX)
    print("appended CGAP:", appended)
    print("All Course Page (110):", dict(counts), "total", sum(counts.values()),
          "done(Yes)", counts.get("Yes", 0))
    print("Deferred", counts.get("Deferred", 0),
          "Partial", counts.get("Partial", 0),
          "No", counts.get("No", 0))


if __name__ == "__main__":
    main()
