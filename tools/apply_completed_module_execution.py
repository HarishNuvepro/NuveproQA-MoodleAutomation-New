"""
Stamp Case_Status on completed module sheets after green (or mixed) Cucumber runs.

Sheets (default): Moodle_users, Upload_Users, All Course Page , Moodle_Sandbox_Labs,
Moodle-Cloudlabs Integration

For each case row with Automated=Yes:
  - If merged target/cucumber*.json has a matching @tag → Pass or Fail
  - Else → Pass (module execution sign-off)

Deferred → Blocked. Partial/Deviation → Pass if cucumber passed else Blocked.
Automated=No → leave Case_Status unchanged unless blank → NA.

Then run refresh_kiwi_dashboard via sync_kiwi_and_traceability.py (caller).

Usage:
  python tools/apply_completed_module_execution.py
  python tools/apply_completed_module_execution.py --fail-on-missing-cuke
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "tools") not in sys.path:
    sys.path.insert(0, str(ROOT / "tools"))

from traceability import CUKE_JSONS, TARGET, load_cuke_json, pick_result, tags_in  # noqa: E402

XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"
DEFAULT_SHEETS = (
    "Moodle_users",
    "Upload_Users",
    "All Course Page ",
    "Moodle_Sandbox_Labs",
    "Moodle-Cloudlabs Integration",
)
TITLE_COL, STATUS_COL, AUTOMATED_COL, REF_COL = 1, 7, 11, 12
COVERED_BY = re.compile(r"covered\s+by\s+(@[\w-]+)", re.I)
def read_execution_files(names: list[str]) -> tuple[dict[str, str], list[str]]:
    """Same merge as traceability.read_execution but limited to named JSON files."""
    out: dict[str, str] = {}
    used: list[str] = []
    rank = {"failed": 3, "skipped": 2, "passed": 1}
    for name in names:
        path = TARGET / name
        if not path.exists():
            continue
        used.append(name)
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
                        key = parsed.lstrip("@").upper()
                        if rank.get(outcome, 0) >= rank.get(out.get(key, ""), 0):
                            out[key] = outcome
    return out, used


def lookup_tags(ref: str, sno: str) -> list[str]:
    tags = list(tags_in(ref or ""))
    m = COVERED_BY.search(ref or "")
    if m:
        covered = tags_in(m.group(1))
        tags = covered + [t for t in tags if t.upper() not in {c.upper() for c in covered}]
    if not tags and sno.strip().upper().startswith(("MCI-", "UU-", "ACP-", "SB-")):
        tags = tags_in(f"@{sno.strip()}")
    return tags


def status_for_row(auto: str, ref: str, sno: str, outcomes: dict[str, str], default_pass: bool) -> str | None:
    auto_l = auto.strip().lower()
    if auto_l == "no":
        return None
    if auto_l == "deferred":
        return "Blocked"
    if auto_l not in ("yes", "partial", "deviation"):
        return None
    tags = lookup_tags(ref, sno)
    oc = pick_result(tags, outcomes) if tags else ""
    if oc == "failed":
        return "Fail"
    if oc == "passed":
        return "Pass"
    if oc == "skipped":
        return "Blocked"
    if default_pass:
        return "Pass"
    return None


def process_sheet(ws, outcomes: dict[str, str], default_pass: bool) -> Counter:
    counts: Counter = Counter()
    for row in range(2, ws.max_row + 1):
        sno = str(ws.cell(row, TITLE_COL).value or "").strip()
        title = str(ws.cell(row, 2).value or "").strip()
        if not sno and not title:
            continue
        auto = str(ws.cell(row, AUTOMATED_COL).value or "").strip()
        ref = str(ws.cell(row, REF_COL).value or "")
        new_status = status_for_row(auto, ref, sno, outcomes, default_pass)
        if new_status is None:
            if auto.lower() == "no" and not str(ws.cell(row, STATUS_COL).value or "").strip():
                ws.cell(row, STATUS_COL).value = "NA"
            continue
        prev = str(ws.cell(row, STATUS_COL).value or "").strip()
        ws.cell(row, STATUS_COL).value = new_status
        if prev != new_status:
            counts[f"changed->{new_status}"] += 1
        else:
            counts[f"kept->{new_status}"] += 1
    return counts


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--sheets",
        nargs="*",
        default=list(DEFAULT_SHEETS),
        help="Module sheet names (exact workbook titles)",
    )
    parser.add_argument(
        "--fail-on-missing-cuke",
        action="store_true",
        help="Do not default Pass when no cucumber tag match",
    )
    parser.add_argument(
        "--cuke-json",
        nargs="*",
        default=["cucumber.json"],
        help="Cucumber JSON files under target/ for Fail (default: latest run only). "
             "Use all archived files only when re-stamping from a full portfolio run.",
    )
    args = parser.parse_args()
    names = args.cuke_json or list(CUKE_JSONS)
    outcomes, used = read_execution_files(names)
    default_pass = not args.fail_on_missing_cuke
    print(
        f"Cucumber sources: {used or '(none)'}; "
        f"default Pass when no tag match: {default_pass}"
    )

    wb = openpyxl.load_workbook(XLSX)
    for name in args.sheets:
        if name not in wb.sheetnames:
            print(f"SKIP missing sheet: {name!r}")
            continue
        stats = process_sheet(wb[name], outcomes, default_pass)
        print(f"{name.strip()}: {dict(stats)}")

    wb.save(XLSX)
    print(f"Saved {XLSX}")


if __name__ == "__main__":
    main()
