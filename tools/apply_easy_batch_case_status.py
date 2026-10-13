"""Set Case_Status=Pass on easy batch rows whose @tags passed in merged Cucumber JSON."""
from __future__ import annotations

import csv
import json
import re
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
BATCH = ROOT / "reports" / "easy_automation_batches.csv"
XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"
CUKE = ROOT / "target" / "cucumber_fast.json"
STATUS_COL = 7  # Case_Status (1-based); Automated=11, Ref=12

TAG_RE = re.compile(r"@([A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*)")


def load_outcomes() -> dict[str, str]:
    if not CUKE.exists():
        return {}
    data = json.loads(CUKE.read_text(encoding="utf-8"))
    out: dict[str, str] = {}
    rank = {"failed": 3, "skipped": 2, "passed": 1}
    for feat in data if isinstance(data, list) else [data]:
        for el in feat.get("elements", []):
            if el.get("type") != "scenario":
                continue
            statuses = [st.get("result", {}).get("status") for st in el.get("steps", [])]
            if any(s in ("failed", "undefined", "pending", "ambiguous") for s in statuses):
                oc = "failed"
            elif any(s == "skipped" for s in statuses):
                oc = "skipped"
            else:
                oc = "passed"
            for tag in el.get("tags", []):
                name = tag.get("name", "").lstrip("@")
                if not name:
                    continue
                prev = out.get(name.upper())
                if prev is None or rank[oc] > rank.get(prev, 0):
                    out[name.upper()] = oc
    return out


def tags_for_row(row: dict) -> list[str]:
    cid = (row.get("case_id") or "").strip()
    if cid.startswith("CGAP-"):
        return [cid]
    run = (row.get("run_tag") or "").strip()
    if run:
        return [run]
    return []


def main() -> None:
    outcomes = load_outcomes()
    wb = openpyxl.load_workbook(XLSX)
    updated = 0
    with BATCH.open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    for row in rows:
        tags = tags_for_row(row)
        if not tags:
            continue
        if any(outcomes.get(t.upper()) == "failed" for t in tags):
            status = "Fail"
        elif all(outcomes.get(t.upper()) == "passed" for t in tags if t.upper() in outcomes):
            if not any(t.upper() in outcomes for t in tags):
                continue
            status = "Pass"
        elif any(outcomes.get(t.upper()) == "skipped" for t in tags):
            continue
        else:
            continue
        area = row["area"]
        cid = row["case_id"]
        ws = wb[area]
        for r in range(2, ws.max_row + 1):
            sno = str(ws.cell(r, 1).value or "").strip()
            title = str(ws.cell(r, 2).value or "").strip()
            if cid.startswith("CGAP-"):
                matched = sno == cid
            else:
                matched = sno == cid or title == cid
            if not matched:
                continue
            auto = str(ws.cell(r, 11).value or "").strip()
            if auto in ("", "No", "Deferred"):
                continue
            ws.cell(r, STATUS_COL).value = status
            updated += 1
            break
    wb.save(XLSX)
    print(f"Updated Case_Status on {updated} easy-batch rows (from {CUKE.name})")


if __name__ == "__main__":
    main()
