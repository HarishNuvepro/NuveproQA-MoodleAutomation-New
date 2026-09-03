"""
Update MoodleRegression_Testing_Kiwi new.xlsx for assessment user-functionality coverage.
- Update the workbook in place (no backup copies by request)
- Mark exact-match rows Yes / refresh Automation_Ref
- Append CGAP-A-USER-* rows for green @A-user scenarios with no exact Kiwi case
- Recalculate Automation_Coverage for Moodle_Assessment_Labs
- Write reports/userflow_traceability.html
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parents[1]
XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"
REPORT = ROOT / "reports" / "userflow_traceability.html"
SHEET = "Moodle_Assessment_Labs"

# Exact Kiwi row → status/ref updates (1-based Excel row numbers)
EXACT_UPDATES = {
    # Duration auto-complete
    4: ("Yes", "assessment_user_functionality.feature @A-user-13 @A-user-14 @A-user-24"),
    # Lab settings: duration/cost on console
    18: ("Yes", "assessment_user_functionality.feature @A-user-19"),
    # Get cost on console
    20: ("Yes", "assessment_user_functionality.feature @A-user-19"),
    # Future start date blocks start
    102: ("Yes", "assessment_user_functionality.feature @A-user-10"),
    # Jump to Console → Access Lab
    113: ("Yes", "assessment_user_functionality.feature @A-user-17"),
}

# Append A-user tags onto already-Yes rows without wiping prior refs
APPEND_REFS = {
    7: "@A-user-1",
    8: "@A-user-5 @A-user-6 @A-user-11",
    109: "@A-user-21",
    114: "@A-user-18",
    115: "@A-user-20",
    128: "@A-user-6 @A-user-11",
}

# CGAP rows for green userflow scenarios with no exact manual case
CGAP_ROWS = [
    ("CGAP-A-USER-1", "Student can open an already visible auto-evaluation assessment",
     "assessment_user_functionality.feature @A-user-2", "High"),
    ("CGAP-A-USER-2", "Student sees assessment title/details before starting",
     "assessment_user_functionality.feature @A-user-3", "High"),
    ("CGAP-A-USER-3", "Student starts assessment and reaches the lab console (cp.php)",
     "assessment_user_functionality.feature @A-user-4", "High"),
    ("CGAP-A-USER-4", "A second fresh user can use the same assessment independently",
     "assessment_user_functionality.feature @A-user-7", "Medium"),
    ("CGAP-A-USER-5", "A reused enrolled user can access a fresh assessment activity",
     "assessment_user_functionality.feature @A-user-8", "Medium"),
    ("CGAP-A-USER-6", "Student cannot see a hidden assessment on the course page",
     "assessment_user_functionality.feature @A-user-9", "High"),
    ("CGAP-A-USER-7", "Student sees New Submission / retake control when multiple attempts are allowed",
     "assessment_user_functionality.feature @A-user-12", "High"),
    ("CGAP-A-USER-8",
     "Student landing Details shows configured difficulty, dates, time limit, attempts, submissions, proctored",
     "assessment_user_functionality.feature @A-user-15", "High"),
    ("CGAP-A-USER-9",
     "Student sees rich description (landing) and instructions with images (lab console Instructions FAB)",
     "assessment_user_functionality.feature @A-user-16", "High"),
    ("CGAP-A-USER-10",
     "Student downloads instructions PDF from lab console Instructions panel",
     "assessment_user_functionality.feature @A-user-22", "High"),
    ("CGAP-A-USER-11",
     "Student opens Need help Contact our support team and reaches mailto mail form",
     "assessment_user_functionality.feature @A-user-23", "Medium"),
    ("CGAP-A-USER-12",
     "3-min time limit auto-submit while student stays on assessment landing; eval+complete within 1+1 min",
     "assessment_user_functionality.feature @A-user-24", "High"),
]


def recount(ws) -> dict[str, int]:
    c = Counter()
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not any(row):
            continue
        auto = str(row[10]).strip() if len(row) > 10 and row[10] is not None else ""
        key = auto if auto else "No"
        if key not in ("Yes", "Partial", "Deviation", "Deferred", "No"):
            # normalize unknowns into No bucket for dashboard
            key = "No"
        c[key] += 1
    return c


def update_coverage_dashboard(wb, counts: dict[str, int]) -> None:
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
    raise RuntimeError(f"{SHEET} not found on Automation_Coverage")


def has_cgap(ws, title: str) -> bool:
    for row in ws.iter_rows(min_row=2, max_col=1, values_only=True):
        if row[0] == title:
            return True
    return False


def write_report(exact_done: list, cgap_done: list, counts: dict[str, int], mapping: list[dict]) -> None:
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    total = sum(counts.values())
    yes = counts.get("Yes", 0)
    pending = counts.get("Deferred", 0) + counts.get("No", 0)
    rows_html = []
    for m in mapping:
        rows_html.append(
            "<tr>"
            f"<td>{m['tag']}</td>"
            f"<td>{m['scenario']}</td>"
            f"<td>{m['kiwi']}</td>"
            f"<td>{m['status']}</td>"
            f"<td>{m['ref']}</td>"
            "</tr>"
        )
    doc = f"""<!doctype html>
<html><head><meta charset=utf-8><title>Assessment userflow traceability</title>
<style>
body{{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#222}}
h1{{font-size:22px}} .big{{font-size:16px;margin:12px 0}}
table{{border-collapse:collapse;width:100%;font-size:13px}}
th,td{{border:1px solid #ccc;padding:6px 8px;text-align:left;vertical-align:top}}
th{{background:#f3f3f3}} .yes{{color:#0a7}} .pending{{color:#a60}}
</style></head><body>
<h1>Assessment — user functionality (@cat_userflow) traceability</h1>
<p class=big>Workbook: <code>docs/MoodleRegression_Testing_Kiwi new.xlsx</code> · sheet <code>{SHEET}</code></p>
<p class=big>
  Assessment Labs overall —
  <span class=yes>Yes: {yes}</span> /
  Partial: {counts.get('Partial', 0)} /
  Deviation: {counts.get('Deviation', 0)} /
  Deferred: {counts.get('Deferred', 0)} /
  No: {counts.get('No', 0)} /
  Total: {total}
  &nbsp;|&nbsp;
  <span class=pending>Still pending (Deferred + No): {pending}</span>
  &nbsp;|&nbsp; Automated %: {round(100 * yes / total) if total else 0}%
</p>
<p>Userflow scenarios (@A-user-1 … @A-user-24): all 24 are automated and marked in Kiwi
(exact match or CGAP-A-USER). Nothing pending inside <b>@cat_userflow</b>.</p>
<h2>@A-user mapping</h2>
<table>
<tr><th>Tag</th><th>Scenario</th><th>Kiwi case</th><th>Automated</th><th>Automation_Ref</th></tr>
{''.join(rows_html)}
</table>
<h2>This update</h2>
<ul>
<li>Exact matches updated: {len(exact_done)} rows — {', '.join(str(x) for x in exact_done)}</li>
<li>CGAP-A-USER rows added/confirmed: {len(cgap_done)} — {', '.join(cgap_done)}</li>
<li>Notes: @A-user-14 keeps 2 attempts; @A-user-15 asserts only Details fields that exist
(difficulty, dates, time limit, attempts, submissions, proctored).</li>
</ul>
</body></html>
"""
    REPORT.write_text(doc, encoding="utf-8")


def main() -> None:
    if not XLSX.exists():
        raise SystemExit(f"missing {XLSX}")
    wb = load_workbook(XLSX)
    ws = wb[SHEET]

    exact_done = []
    for row_num, (status, ref) in EXACT_UPDATES.items():
        ws.cell(row_num, 11).value = status
        ws.cell(row_num, 12).value = ref
        exact_done.append(row_num)
        print(f"Updated row {row_num} -> {status} | {ref}")

    for row_num, tag in APPEND_REFS.items():
        cell = ws.cell(row_num, 12)
        cur = str(cell.value or "").strip()
        if tag.split()[0] in cur:
            print(f"Row {row_num} already has {tag}")
            continue
        cell.value = (cur + " ; " if cur else "") + f"assessment_user_functionality.feature {tag}"
        if not str(ws.cell(row_num, 11).value or "").strip():
            ws.cell(row_num, 11).value = "Yes"
        print(f"Appended ref on row {row_num}")

    cgap_done = []
    for title, summary, ref, priority in CGAP_ROWS:
        if has_cgap(ws, title):
            # refresh Automated/Ref if already present
            for r in range(2, ws.max_row + 1):
                if ws.cell(r, 1).value == title:
                    ws.cell(r, 11).value = "Yes"
                    ws.cell(r, 12).value = ref
                    break
            cgap_done.append(title)
            print(f"CGAP exists (refreshed): {title}")
            continue
        new_row = ws.max_row + 1
        ws.cell(new_row, 1).value = title
        ws.cell(new_row, 2).value = summary
        ws.cell(new_row, 5).value = "Assessment user functionality"
        ws.cell(new_row, 6).value = priority
        ws.cell(new_row, 7).value = "CONFIRMED"
        ws.cell(new_row, 8).value = "See automation scenario steps"
        ws.cell(new_row, 9).value = "Scenario passes green"
        ws.cell(new_row, 10).value = "Core"
        ws.cell(new_row, 11).value = "Yes"
        ws.cell(new_row, 12).value = (
            f"{ref} | Automation gap (derived userflow) {date.today().isoformat()}"
        )
        cgap_done.append(title)
        print(f"Appended {title} at row {new_row}")

    counts = recount(ws)
    update_coverage_dashboard(wb, counts)
    print("Automation_Coverage refreshed:", dict(counts))

    mapping = [
        {"tag": "@A-user-1", "scenario": "Start + submit auto-eval attempt",
         "kiwi": "Row 7 Moodle_Assessment_SubmitButtonCheck", "status": "Yes",
         "ref": "… + @A-user-1"},
        {"tag": "@A-user-2", "scenario": "Open visible assessment landing",
         "kiwi": "CGAP-A-USER-1", "status": "Yes", "ref": "@A-user-2"},
        {"tag": "@A-user-3", "scenario": "Details visible before start",
         "kiwi": "CGAP-A-USER-2", "status": "Yes", "ref": "@A-user-3"},
        {"tag": "@A-user-4", "scenario": "Start reaches lab console",
         "kiwi": "CGAP-A-USER-3", "status": "Yes", "ref": "@A-user-4"},
        {"tag": "@A-user-5", "scenario": "Submit moves to Evaluating",
         "kiwi": "Row 8 Moodle_Assessment_EvaluationCheck", "status": "Yes",
         "ref": "… + @A-user-5"},
        {"tag": "@A-user-6", "scenario": "Reopen evaluated result",
         "kiwi": "Row 8 / Row 128", "status": "Yes", "ref": "… + @A-user-6"},
        {"tag": "@A-user-7", "scenario": "Second fresh user independent access",
         "kiwi": "CGAP-A-USER-4", "status": "Yes", "ref": "@A-user-7"},
        {"tag": "@A-user-8", "scenario": "Reused enrolled user + fresh activity",
         "kiwi": "CGAP-A-USER-5", "status": "Yes", "ref": "@A-user-8"},
        {"tag": "@A-user-9", "scenario": "Hidden activity not on course page",
         "kiwi": "CGAP-A-USER-6", "status": "Yes", "ref": "@A-user-9"},
        {"tag": "@A-user-10", "scenario": "Future start date blocks start",
         "kiwi": "Row 102 Start Assessment", "status": "Yes", "ref": "@A-user-10"},
        {"tag": "@A-user-11", "scenario": "Evaluated result details",
         "kiwi": "Row 8 / Row 128", "status": "Yes", "ref": "… + @A-user-11"},
        {"tag": "@A-user-12", "scenario": "New attempt / retake control",
         "kiwi": "CGAP-A-USER-7", "status": "Yes", "ref": "@A-user-12"},
        {"tag": "@A-user-13", "scenario": "3 min time limit auto-submit (1 attempt)",
         "kiwi": "Row 4 Moodle_Assessment_DurationCheck", "status": "Yes",
         "ref": "@A-user-13"},
        {"tag": "@A-user-14", "scenario": "3 min auto-submit with 2 attempts",
         "kiwi": "Row 4 Moodle_Assessment_DurationCheck", "status": "Yes",
         "ref": "@A-user-14"},
        {"tag": "@A-user-15", "scenario": "Configured Details on landing",
         "kiwi": "CGAP-A-USER-8", "status": "Yes", "ref": "@A-user-15"},
        {"tag": "@A-user-16", "scenario": "Rich description + instructions/images",
         "kiwi": "CGAP-A-USER-9", "status": "Yes", "ref": "@A-user-16"},
        {"tag": "@A-user-17", "scenario": "Jump to Console → Access Lab",
         "kiwi": "Row 113 Moodle_Assessment_Accesslab", "status": "Yes",
         "ref": "@A-user-17"},
        {"tag": "@A-user-18", "scenario": "Lab credentials login/password",
         "kiwi": "Row 114 Moodle_Assessment_Assessment lab", "status": "Yes",
         "ref": "… + @A-user-18"},
        {"tag": "@A-user-19", "scenario": "Lab Usage duration + Get Cost",
         "kiwi": "Row 18 + Row 20", "status": "Yes", "ref": "@A-user-19"},
        {"tag": "@A-user-20", "scenario": "Change lab password on console",
         "kiwi": "Row 115 Moodle_Assessment_Assessment lab", "status": "Yes",
         "ref": "… + @A-user-20"},
        {"tag": "@A-user-21", "scenario": "Stop lab from console; admin Manage Labs Stopped",
         "kiwi": "Row 109 Moodle_Assessment_Stop lab", "status": "Yes",
         "ref": "… + @A-user-21"},
        {"tag": "@A-user-22", "scenario": "Download instructions PDF",
         "kiwi": "CGAP-A-USER-10", "status": "Yes", "ref": "@A-user-22"},
        {"tag": "@A-user-23", "scenario": "Need help → mailto support",
         "kiwi": "CGAP-A-USER-11", "status": "Yes", "ref": "@A-user-23"},
        {"tag": "@A-user-24", "scenario": "3 min auto-submit while on activity landing",
         "kiwi": "Row 4 + CGAP-A-USER-12", "status": "Yes", "ref": "@A-user-24"},
    ]
    write_report(exact_done, cgap_done, counts, mapping)

    wb.save(XLSX)
    print(f"Saved {XLSX}")
    print(f"Report {REPORT}")
    pending = counts.get("Deferred", 0) + counts.get("No", 0)
    print(f"Pending on Assessment Labs (Deferred+No): {pending}")


if __name__ == "__main__":
    main()
