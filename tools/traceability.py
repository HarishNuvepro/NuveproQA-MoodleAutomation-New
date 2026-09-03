"""
BDD traceability & execution report for the Java/Cucumber suite.

Merges three sources into one per-case report:
  1) docs/MoodleRegression_Testing_Kiwi new.xlsx  -> ALL manual cases (every module sheet)
  2) src/test/resources/features/*.feature        -> scenario @tags
  3) Regression_Dashboard + Case_Status           -> Executed / Passed
     (same rule as the dashboard: Automated=Yes and Case_Status in
     Pass/Fail/Failed/Blocked). Cucumber json is optional last-run detail only.

The @tag in Automation_Ref is the link between a Kiwi row and its script.

Run:
    python tools/traceability.py
Outputs (in reports/):
    traceability_execution.csv
    traceability_execution.html
"""
from __future__ import annotations

import csv
import html
import json
import re
from collections import defaultdict
from datetime import date
from pathlib import Path

from openpyxl import load_workbook

JAVA_ROOT = Path(__file__).resolve().parents[1]
FEATURES_DIR = JAVA_ROOT / "src" / "test" / "resources" / "features"
TARGET = JAVA_ROOT / "target"
# Merge results from every saved run (fast + slow) so the report reflects all executions.
CUKE_JSONS = ["cucumber_fast.json", "cucumber_slow.json", "cucumber.json"]
OUT_DIR = JAVA_ROOT / "reports"
XLSX = JAVA_ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"

SKIP_SHEETS = {
    "Regression_Dashboard",
    "Release_Regression_Plan",
    "Automation_Coverage",
}
DASHBOARD = "Regression_Dashboard"
# Same statuses the dashboard COUNTIFS uses for Executed.
DASH_RUN = {"pass", "fail", "failed", "blocked"}
ASSESSMENT_SHEET = "Moodle_Assessment_Labs"

# Feature-level / runner tags — not a case id.
META_TAGS = {
    "provisioning", "freshlab", "guided", "guidedlab", "assessment-cleanup",
    "cat_userflow", "sandbox", "slow", "watch", "labactions", "users",
    "assessment", "playground", "cloudlabs", "wip", "skip",
}

# Current run tags: @U-user-1, @UU-S1, @ACP-S1, @SB-create-1, @A-create-full,
# @A55, @PP-2, @LT-1, @CGAP-A-USER-1, …
TAG_RE = re.compile(r"@([A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*)")

TITLE_COL, SUMMARY_COL, PRIORITY_COL = 0, 1, 5
STATUS_COL, AUTOMATED_COL, REF_COL = 6, 10, 11


def sheet_label(name: str) -> str:
    return name.strip()


def tags_in(text: str) -> list[str]:
    found = []
    seen = set()
    for raw in TAG_RE.findall(text or ""):
        tag = raw.strip()
        if not tag or tag.lower() in META_TAGS:
            continue
        key = tag.upper()
        if key in seen:
            continue
        seen.add(key)
        found.append(tag)
    return found


def norm_tag(tag: str) -> str:
    return tag.lstrip("@").upper()


def read_manual_cases() -> list[dict]:
    if not XLSX.exists():
        raise SystemExit(f"missing workbook: {XLSX}")
    wb = load_workbook(XLSX, read_only=True, data_only=True)
    cases = []
    for sheet in wb.sheetnames:
        if sheet in SKIP_SHEETS:
            continue
        rows = list(wb[sheet].iter_rows(values_only=True))
        if not rows:
            continue
        area = sheet_label(sheet)
        for idx, row in enumerate(rows[1:], start=2):
            title = str(row[TITLE_COL]).strip() if TITLE_COL < len(row) and row[TITLE_COL] else ""
            summary = str(row[SUMMARY_COL]).strip() if SUMMARY_COL < len(row) and row[SUMMARY_COL] else ""
            if not title and not summary:
                continue
            automated = str(row[AUTOMATED_COL]).strip() if AUTOMATED_COL < len(row) and row[AUTOMATED_COL] else ""
            ref = str(row[REF_COL]).strip() if REF_COL < len(row) and row[REF_COL] else ""
            cases.append({
                "id": title or f"{area}-row{idx}",
                "area": area,
                "row": idx,
                "title": (summary or title)[:160],
                "importance": (str(row[PRIORITY_COL]).strip()
                               if PRIORITY_COL < len(row) and row[PRIORITY_COL] else ""),
                "case_status": (str(row[STATUS_COL]).strip()
                                if STATUS_COL < len(row) and row[STATUS_COL] else ""),
                "automated": automated,
                "ref": ref,
                "tags": tags_in(ref),
            })
    wb.close()
    return cases


def scan_features() -> dict[str, dict]:
    """{NORM_TAG: {'scenario': name, 'feature': filename, 'tag': original}}."""
    created: dict[str, dict] = {}
    if not FEATURES_DIR.exists():
        return created
    for path in sorted(FEATURES_DIR.glob("*.feature")):
        pending: list[str] = []
        for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
            s = line.strip()
            if s.startswith("@"):
                pending.extend(tags_in(s))
            elif s.lower().startswith("scenario"):
                name = s.split(":", 1)[1].strip() if ":" in s else s
                for tag in pending:
                    created[norm_tag(tag)] = {
                        "scenario": name, "feature": path.name, "tag": tag,
                    }
                pending = []
            elif s.lower().startswith("feature"):
                pending = []
    return created


def load_cuke_json(path: Path) -> list:
    """Load Cucumber json; tolerate concatenated arrays from merged runs."""
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    try:
        data = json.loads(text)
        return data if isinstance(data, list) else [data]
    except json.JSONDecodeError:
        out: list = []
        dec = json.JSONDecoder()
        idx = 0
        while idx < len(text):
            while idx < len(text) and text[idx] in " \t\n\r":
                idx += 1
            if idx >= len(text):
                break
            try:
                chunk, idx = dec.raw_decode(text, idx)
            except json.JSONDecodeError:
                break
            if isinstance(chunk, list):
                out.extend(chunk)
            else:
                out.append(chunk)
        return out


def read_execution() -> dict[str, str]:
    """{NORM_TAG: passed|failed|skipped} merged across saved cucumber json runs."""
    out: dict[str, str] = {}
    rank = {"failed": 3, "skipped": 2, "passed": 1}
    for name in CUKE_JSONS:
        path = TARGET / name
        if not path.exists():
            continue
        data = load_cuke_json(path)
        for feat in data:
            for el in feat.get("elements", []):
                if el.get("type") != "scenario":
                    continue
                statuses = [st.get("result", {}).get("status") for st in el.get("steps", [])]
                if any(s in ("failed", "undefined", "pending", "ambiguous") for s in statuses):
                    outcome = "failed"
                elif any(s == "skipped" for s in statuses):
                    outcome = "skipped"
                else:
                    outcome = "passed"
                for tag in el.get("tags", []):
                    tname = tag.get("name", "")
                    for parsed in tags_in(tname if tname.startswith("@") else f"@{tname}"):
                        key = norm_tag(parsed)
                        if rank.get(outcome, 0) >= rank.get(out.get(key, ""), 0):
                            out[key] = outcome
    return out


def pick_result(tags: list[str], executed: dict[str, str]) -> str:
    rank = {"failed": 3, "skipped": 2, "passed": 1, "": 0}
    best = ""
    for tag in tags:
        outcome = executed.get(norm_tag(tag), "")
        if rank[outcome] > rank[best]:
            best = outcome
    return best


def is_yes(value: str) -> bool:
    return value.strip().lower() == "yes"


def is_marked_auto(value: str) -> bool:
    return value.strip().lower() in {"yes", "partial", "deviation"}


def status_key(value: str) -> str:
    s = (value or "").strip().lower()
    if s == "pass":
        return "passed"
    if s in {"fail", "failed"}:
        return "failed"
    if s == "blocked":
        return "blocked"
    if s in {"skip", "skipped"}:
        return "skipped"
    return ""


def read_dashboard_executed() -> dict[str, int]:
    """Regression_Dashboard Executed, keyed by case-sheet label."""
    if not XLSX.exists():
        return {}
    wb = load_workbook(XLSX, read_only=True, data_only=True)
    if DASHBOARD not in wb.sheetnames:
        wb.close()
        return {}
    out: dict[str, int] = {}
    for row in wb[DASHBOARD].iter_rows(min_row=5, min_col=2, max_col=5, values_only=True):
        label = str(row[0] or "").strip()
        if not label or label.upper() == "TOTAL":
            continue
        executed = row[3]
        if not isinstance(executed, (int, float)):
            continue
        key = ASSESSMENT_SHEET if label.lower().startswith("assessment") else label
        out[key] = int(executed)
    wb.close()
    return out


def main() -> None:
    OUT_DIR.mkdir(exist_ok=True)
    cases = read_manual_cases()
    created = scan_features()
    cuke = read_execution()
    dash_exec = read_dashboard_executed()

    for c in cases:
        tags = c["tags"]
        hit = next((created.get(norm_tag(t)) for t in tags if norm_tag(t) in created), None)
        if hit is None and tags:
            # Feature exists under a Covered-by / Automation_Ref tag even if kiwi Automated is No.
            hit = created.get(norm_tag(tags[0]))
        c["scenario"] = hit["scenario"] if hit else ""
        c["feature"] = hit["feature"] if hit else ""
        c["run_tag"] = (hit["tag"] if hit else (tags[0] if tags else ""))
        c["result"] = status_key(c["case_status"])
        c["cuke_result"] = pick_result(tags, cuke)
        c["executed"] = "yes" if is_yes(c["automated"]) and c["case_status"].strip().lower() in DASH_RUN else "no"
        if not c["feature"] and c["ref"]:
            m = re.search(r"([\w.-]+\.feature)", c["ref"])
            if m:
                c["feature"] = m.group(1)

    fieldnames = ["id", "area", "importance", "automated", "case_status",
                  "executed", "result", "cuke_result", "run_tag", "scenario",
                  "feature", "title"]
    with (OUT_DIR / "traceability_execution.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        w.writeheader()
        for c in cases:
            w.writerow(c)

    per: dict[str, dict[str, int]] = defaultdict(
        lambda: {"total": 0, "auto": 0, "exec": 0, "pass": 0, "not_auto": 0})
    for c in cases:
        a = per[c["area"]]
        a["total"] += 1
        if is_yes(c["automated"]):
            a["auto"] += 1
        if c["automated"].strip().lower() == "no":
            a["not_auto"] += 1
        if c["executed"] == "yes":
            a["exec"] += 1
        if is_yes(c["automated"]) and c["case_status"].strip().lower() == "pass":
            a["pass"] += 1
    # Dashboard Executed is the source of truth for modules it lists
    # (Assessment uses the merged-suite count, not every Yes row).
    for area, executed in dash_exec.items():
        if area in per:
            per[area]["exec"] = executed

    tot = sum(a["total"] for a in per.values())
    auto = sum(a["auto"] for a in per.values())
    ex = sum(a["exec"] for a in per.values())
    ps = sum(a["pass"] for a in per.values())
    not_auto = sum(a["not_auto"] for a in per.values())
    dash_areas = [a for a in dash_exec if a in per]

    color = {
        "passed": "#1a7f37", "failed": "#cf222e", "blocked": "#8250df",
        "skipped": "#9a6700", "": "#8b949e",
    }
    label = {
        "passed": "PASS", "failed": "FAIL", "blocked": "BLOCKED",
        "skipped": "SKIP", "": "not run",
    }

    def esc(s):
        return html.escape(str(s))

    areas = dash_areas + [a for a in per if a not in dash_exec]
    summary = "".join(
        f"<tr data-area='{esc(a)}'><td>{esc(a)}</td>"
        f"<td class=r>{per[a]['auto']}</td>"
        f"<td class=r>{per[a]['exec']}</td><td class=r>{per[a]['pass']}</td>"
        f"<td class=r>{per[a]['not_auto']}</td>"
        f"<td class=r>{per[a]['total']}</td>"
        f"<td class=r>{(per[a]['auto']/per[a]['total']*100 if per[a]['total'] else 0):.0f}%</td></tr>"
        for a in areas)
    summary += (
        f"<tr><td><b>TOTAL</b></td>"
        f"<td class=r><b>{auto}</b></td><td class=r><b>{ex}</b></td>"
        f"<td class=r><b>{ps}</b></td><td class=r><b>{not_auto}</b></td>"
        f"<td class=r><b>{tot}</b></td>"
        f"<td class=r><b>{(auto/tot*100 if tot else 0):.0f}%</b></td></tr>"
    )

    detail_cases = [c for c in cases if is_marked_auto(c["automated"])]
    detail = "".join(
        f"<tr data-area='{esc(c['area'])}'><td>{esc(c['area'])}</td>"
        f"<td><b>{esc(c['id'])}</b></td><td>{esc(c['title'])}</td>"
        f"<td>{esc(c['automated'] or '—')}</td>"
        f"<td>{esc(c['run_tag'] or '—')}</td>"
        f"<td>{esc(c['scenario'])}</td>"
        f"<td>{esc(c['feature'])}</td>"
        f"<td><span class=badge style='background:{color[c['result']]}'>{label[c['result']]}</span></td></tr>"
        for c in detail_cases)

    options = "".join(f"<option value='{esc(a)}'>{esc(a)}</option>" for a in areas)
    generated = date.today().isoformat()

    doc = f"""<!doctype html><html><head><meta charset=utf-8>
<title>Traceability &amp; Execution</title>
<style>
body{{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2328}}
table{{border-collapse:collapse;width:100%;margin:12px 0;font-size:13px}}
th,td{{border:1px solid #d0d7de;padding:6px 10px;text-align:left;vertical-align:top}}
th{{background:#f6f8fa}} td.r{{text-align:right}}
.big{{font-size:20px;font-weight:700}}
.badge{{color:#fff;padding:2px 8px;border-radius:10px;font-size:12px;white-space:nowrap}}
select{{font:inherit;padding:4px 8px}}
</style>
</head><body>
<h1>Nuvepro Moodle — Traceability &amp; Execution</h1>
<div>Generated {generated} · all module sheets in MoodleRegression_Testing_Kiwi new.xlsx</div>
<p class=big>Automated: {auto}/{tot} &nbsp;|&nbsp; Executed: {ex}
 &nbsp;|&nbsp; Passed: {ps} &nbsp;|&nbsp; Not automatable: {not_auto}</p>
<p>Module filter:
 <select id=area>
  <option value="">All modules</option>
  {options}
 </select>
</p>
<h2>By module</h2>
<table id=summary><thead><tr><th>Module</th><th>Automated</th><th>Executed</th>
<th>Passed</th><th>Not automatable</th><th>Total cases</th><th>Coverage</th></tr></thead>
<tbody>{summary}</tbody></table>
<h2>Automated cases → scenario → Case_Status</h2>
<table id=detail><thead><tr><th>Module</th><th>Case</th><th>Manual title</th>
<th>Automated</th><th>Tag</th><th>Scenario</th><th>Feature</th><th>Case status</th>
</tr></thead><tbody>{detail}</tbody></table>
<script>
const sel = document.getElementById('area');
function apply() {{
  const v = sel.value;
  for (const table of [document.getElementById('summary'), document.getElementById('detail')]) {{
    for (const row of table.tBodies[0].rows) {{
      row.style.display = (!v || row.dataset.area === v) ? '' : 'none';
    }}
  }}
}}
sel.addEventListener('change', apply);
</script>
</body></html>"""
    (OUT_DIR / "traceability_execution.html").write_text(doc, encoding="utf-8")

    print(f"Automated {auto}/{tot} | Executed {ex} | Passed {ps} | Not automatable {not_auto}")
    print(f"Modules: {len(per)}")
    print(f"Wrote {OUT_DIR / 'traceability_execution.csv'}")
    print(f"Wrote {OUT_DIR / 'traceability_execution.html'}")


if __name__ == "__main__":
    main()
