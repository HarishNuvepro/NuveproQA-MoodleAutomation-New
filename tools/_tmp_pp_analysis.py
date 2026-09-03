import json
import openpyxl
from pathlib import Path

p = Path(r"c:\Users\HarishKumar\Documents\NuveproQA-MoodleAutomation\docs\MoodleRegression_Testing_Kiwi new.xlsx")
wb = openpyxl.load_workbook(p, read_only=True, data_only=True)
ws = wb["Practice_Project"]
rows = list(ws.iter_rows(values_only=True))
header = rows[0]
cols = {str(h).strip(): i for i, h in enumerate(header) if h}


def get(row, k):
    i = cols.get(k)
    return row[i] if i is not None and i < len(row) else None


all_rows = []
blank_rows = []
for i, row in enumerate(rows[1:], start=2):
    auto = get(row, "Automated")
    blank = auto is None or str(auto).strip() == ""
    rec = {
        "row": i,
        "title": get(row, "Title"),
        "summary": get(row, "Summary"),
        "preconditions": get(row, "Preconditions"),
        "priority": get(row, "Priority"),
        "steps": get(row, "Steps"),
        "expected": get(row, "Expected"),
        "automated": auto,
        "ref": get(row, "Automation_Ref"),
        "blank": blank,
    }
    all_rows.append(rec)
    if blank:
        blank_rows.append(rec)


def categorize(r):
    t = " ".join(
        str(r.get(k) or "")
        for k in ("title", "summary", "steps", "expected", "preconditions")
    ).lower()
    if r["title"] == "Practice project detailed report" or "detailed report" in t:
        return "detailed_report"
    if r["title"] == "Summary Report" or (
        "summary" in t and "report" in t and "summary report" in t
    ):
        return "summary_report"
    if "api" in t:
        return "api_response"
    if any(
        x in t
        for x in [
            "unenrol",
            "unenroll",
            "suspend",
            "reenrol",
            "re-enrol",
            "re-enroll",
            "re enrol",
        ]
    ):
        return "enrol_unenrol_suspend_reenrol"
    if any(
        x in t
        for x in [
            "hidden",
            "hide",
            "hidding",
            "deleted",
            "delete the project",
            "deleting the project",
            "not shown in course",
        ]
    ):
        return "hidden_deleted_activity"
    if any(
        x in t
        for x in [
            "cross course",
            "multiple course",
            "another course",
            "other course",
            "enrolled in another",
            "all course",
            "multiple courses",
        ]
    ):
        return "cross_course"
    return "other_blank"


cats = {}
for r in blank_rows:
    c = categorize(r)
    cats.setdefault(c, []).append(r)

out = Path("target/pp_blank_analysis.json")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps({"total": len(all_rows), "blank": len(blank_rows), "cats": cats}, indent=2, default=str), encoding="utf-8")

print("TOTAL", len(all_rows), "BLANK", len(blank_rows))
for k, v in sorted(cats.items()):
    print(f"\n{k}: rows {[x['row'] for x in v]}")
    for r in v:
        print(f"  R{r['row']} P={r['priority']} | {r['summary']}")
        if r.get("steps"):
            s = str(r["steps"]).replace("\n", " ")[:200]
            print(f"    STEPS: {s}")

print("\n--- NON-BLANK in scope ---")
for r in all_rows:
    if r["blank"]:
        continue
    c = categorize(r)
    if c != "other_blank":
        print(f"R{r['row']} {c} AUTO={r['automated']} REF={r['ref']}")
