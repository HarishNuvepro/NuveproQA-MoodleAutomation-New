"""Rebuild Regression_Dashboard module rows and restore formula caches.

Two jobs, both needed after any case change:

1. Enforce the module order QA asked for on `Regression_Dashboard`:
   Users, Upload users, All course page, Sandbox, Assessment. The assessment row
   is a single row covering BOTH automation suites (activity creation + user
   functionality) and takes its Total from the Release_Regression_Plan sheet.

2. Recompute every formula on `Regression_Dashboard` and
   `Release_Regression_Plan` and write the result back as the formula's cached
   value. Excel is not licensed on this machine, so a formula we touch would
   otherwise render BLANK in the viewers QA uses - openpyxl drops the cache of
   every formula in the workbook each time it saves.

Usage: python tools/refresh_kiwi_dashboard.py
"""
from __future__ import annotations

import os
import re
import shutil
import time
import zipfile
from copy import copy
from pathlib import Path
from xml.etree import ElementTree

import openpyxl
from openpyxl.utils import quote_sheetname

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
STAGING = Path("target/kiwi_dashboard_refresh.xlsx")
DASHBOARD = "Regression_Dashboard"
PLAN = "Release_Regression_Plan"

TITLE_COL, SUMMARY_COL, STATUS_COL, TIER_COL, AUTOMATED_COL = 1, 2, 7, 10, 11
# Dashboard "Executed" counts automated cases that have been run.
DASH_RUN = {"pass", "fail", "failed", "blocked"}
# The release plan also counts NA cases as executed.
PLAN_RUN = DASH_RUN | {"na", "n/a", "not applicable"}

FIRST_ROW = 5
# Module rows in the order QA wants them, as (sheet name, dashboard label).
MODULES = [
    ("Moodle_users", "Moodle_users"),
    ("Upload_Users", "Upload_Users"),
    ("All Course Page ", "All Course Page"),
    ("Moodle_Sandbox_Labs", "Moodle_Sandbox_Labs"),
]
ASSESSMENT_LABEL = "Assessment (Activity Creation + User Functionality)"
# The two assessment automation suites that share the merged row.
ASSESSMENT_SUITES = (47, 24)
ASSESSMENT_SHEET = "Moodle_Assessment_Labs"
# Modules QA wants listed after the merged assessment row (same order as the plan).
TRAILING_MODULES = [
    ("Practice_Project", "Practice_Project"),
    ("Moodle_Lab_Templates", "Moodle_Lab_Templates"),
    ("Moodle settings", "Moodle settings"),
]
PLAN_FIRST_ROW = 10
PLAN_LAST_COLUMN = 13
# Release_Regression_Plan leads with the same modules, then keeps QA's order.
PLAN_ORDER = ["Moodle_users", "Upload_Users", "All Course Page",
              "Moodle_Sandbox_Labs", ASSESSMENT_SHEET, "Practice_Project"]
# An ampersand in a sheet title gets re-escaped by openpyxl on every save, which
# permanently breaks any formula referring to it - so this tab avoids one.
LTI_TITLE = "LTI and SSO Integration"
# A module sheet that has no plan row yet, with the Automated state to give it.
PLAN_ADDITIONS = {"Upload_Users": "Yes"}


def norm_sheet(name: str) -> str:
    label = name.strip()
    while "&amp;" in label:
        label = label.replace("&amp;", "&")
    return label.lower()


def resolve(book, label: str) -> str | None:
    target = norm_sheet(label)
    for name in book.sheetnames:
        if norm_sheet(name) == target:
            return name
    return None


def cases(sheet, column: int) -> int:
    return sum(
        1
        for row in range(2, sheet.max_row + 1)
        if str(sheet.cell(row, column).value or "").strip()
    )


def cell_text(sheet, row: int, column: int) -> str:
    return str(sheet.cell(row, column).value or "").strip().lower()


def automated_run(sheet) -> int:
    """Automated=Yes AND Case_Status in Pass/Fail/Failed/Blocked."""
    return sum(
        1
        for row in range(2, sheet.max_row + 1)
        if cell_text(sheet, row, AUTOMATED_COL) == "yes"
        and cell_text(sheet, row, STATUS_COL) in DASH_RUN
    )


def automated_no(sheet) -> int:
    return sum(
        1
        for row in range(2, sheet.max_row + 1)
        if cell_text(sheet, row, AUTOMATED_COL) == "no"
    )


def tier_counts(sheet) -> dict[str, int]:
    counts = {"Smoke": 0, "Core": 0, "Extended": 0}
    run = {"Smoke": 0, "Core": 0}
    for row in range(2, sheet.max_row + 1):
        tier = str(sheet.cell(row, TIER_COL).value or "").strip()
        if tier in counts:
            counts[tier] += 1
        if tier in run and cell_text(sheet, row, STATUS_COL) in PLAN_RUN:
            run[tier] += 1
    counts["SmokeRun"] = run["Smoke"]
    counts["CoreRun"] = run["Core"]
    return counts


def executed_formula(quoted: str) -> str:
    return "+".join(
        f'COUNTIFS({quoted}!K2:K2000,"Yes",{quoted}!G2:G2000,"{status}")'
        for status in ("Pass", "Fail", "Failed", "Blocked")
    )


def plan_row_for(book, sheet_name: str) -> int:
    plan = book[PLAN]
    for row in range(PLAN_FIRST_ROW, plan.max_row + 1):
        if str(plan.cell(row, 2).value or "").strip() == sheet_name:
            return row
    raise RuntimeError(f"{PLAN} has no row for {sheet_name}")


def copy_row_style(sheet, source_row: int, target_row: int) -> None:
    for column in range(1, 9):
        sheet.cell(target_row, column)._style = copy(sheet.cell(source_row, column)._style)


def build_dashboard(book) -> dict[str, float]:
    sheet = book[DASHBOARD]
    cached: dict[str, float] = {}
    totals = {"total": 0, "executed": 0, "not_automatable": 0, "deferred": 0}
    old_total_row = next(
        (row for row in range(FIRST_ROW, sheet.max_row + 1)
         if str(sheet.cell(row, 2).value or "").strip().upper() == "TOTAL"),
        None,
    )
    blank_row = sheet.max_row

    def write_module(row: int, sheet_name: str, label: str) -> None:
        source = book[sheet_name]
        quoted = quote_sheetname(sheet_name)
        total = cases(source, TITLE_COL)
        executed = automated_run(source)
        not_automatable = automated_no(source)
        deferred = max(0, total - executed - not_automatable)
        in_scope = total - not_automatable
        completion = executed / in_scope if in_scope > 0 else 0

        sheet.cell(row, 1).value = row - FIRST_ROW + 1
        sheet.cell(row, 2).value = label
        # Typed values (not formulas). Cursor/xlsx viewers recalculate
        # COUNTIFS/SUMPRODUCT on open and swap or blank the row.
        sheet.cell(row, 3).value = total
        sheet.cell(row, 5).value = executed
        sheet.cell(row, 6).value = not_automatable
        sheet.cell(row, 7).value = deferred
        sheet.cell(row, 8).value = completion

        cached.update({f"C{row}": total, f"E{row}": executed, f"F{row}": not_automatable,
                       f"G{row}": deferred, f"H{row}": completion})
        totals["total"] += total
        totals["executed"] += executed
        totals["not_automatable"] += not_automatable
        totals["deferred"] += deferred

    row = FIRST_ROW
    for sheet_name, label in MODULES:
        write_module(row, sheet_name, label)
        row += 1

    # Merged assessment row: total comes from the release plan, executed is the
    # two automation suites added together.
    assessment_sheet = book[resolve(book, ASSESSMENT_SHEET)]
    total = cases(assessment_sheet, SUMMARY_COL)  # the plan counts column B
    executed = sum(ASSESSMENT_SUITES)
    not_automatable = 0
    deferred = max(0, total - executed - not_automatable)
    completion = executed / total if total else 0

    plan_row = plan_row_for(book, ASSESSMENT_SHEET)
    sheet.cell(row, 1).value = row - FIRST_ROW + 1
    sheet.cell(row, 2).value = ASSESSMENT_LABEL
    sheet.cell(row, 3).value = total
    sheet.cell(row, 5).value = executed
    sheet.cell(row, 6).value = not_automatable
    sheet.cell(row, 7).value = deferred
    sheet.cell(row, 8).value = completion
    cached.update({f"C{row}": total, f"E{row}": executed,
                   f"G{row}": deferred, f"H{row}": completion})
    totals["total"] += total
    totals["executed"] += executed
    totals["deferred"] += deferred
    row += 1

    for sheet_name, label in TRAILING_MODULES:
        write_module(row, sheet_name, label)
        row += 1
    last_module = row - 1

    total_row = last_module + 1
    if old_total_row is not None and old_total_row != total_row:
        copy_row_style(sheet, old_total_row, total_row)
        copy_row_style(sheet, blank_row, old_total_row)
    for column in (1, 4):
        sheet.cell(total_row, column).value = None
    in_scope = totals["total"] - totals["not_automatable"]
    completion = totals["executed"] / in_scope if in_scope > 0 else 0
    sheet.cell(total_row, 2).value = "TOTAL"
    sheet.cell(total_row, 3).value = totals["total"]
    sheet.cell(total_row, 5).value = totals["executed"]
    sheet.cell(total_row, 6).value = totals["not_automatable"]
    sheet.cell(total_row, 7).value = totals["deferred"]
    sheet.cell(total_row, 8).value = completion
    cached.update({
        f"C{total_row}": totals["total"],
        f"E{total_row}": totals["executed"],
        f"F{total_row}": totals["not_automatable"],
        f"G{total_row}": totals["deferred"],
        f"H{total_row}": completion,
    })

    # The old layout had one more module row; drop whatever is left below TOTAL.
    for stale in range(total_row + 1, sheet.max_row + 1):
        for column in range(1, 9):
            sheet.cell(stale, column).value = None
    sheet.auto_filter.ref = f"A4:H{last_module}"
    return cached


def repair_sheet_names(book) -> None:
    """Fix the LTI row: external references and an unusable sheet title.

    The row was authored against a separate workbook (`'[1]LTI & SSO
    Integration'`), so it evaluated to #REF! and that error propagated into the
    plan's TOTAL row. Worse, openpyxl re-escapes an ampersand in a sheet title
    on every save, so the tab drifted to `LTI &amp;amp;...` and no formula could
    ever match it again. Renaming it without an ampersand ends that for good.
    """
    stale = next((name for name in book.sheetnames
                  if name.upper().startswith("LTI") and name != LTI_TITLE), None)
    if stale:
        book[stale].title = LTI_TITLE
        print(f"  renamed sheet {stale!r} -> {LTI_TITLE!r} (ampersand-free)")

    plan = book[PLAN]
    repointed = 0
    for row in plan.iter_rows():
        for cell in row:
            if isinstance(cell.value, str) and "'[1]" in cell.value:
                cell.value = cell.value.replace("'[1]", "'")
                repointed += 1
    if repointed:
        print(f"  repointed {repointed} external formula references to local sheets")

    for row in range(PLAN_FIRST_ROW, plan.max_row + 1):
        label = str(plan.cell(row, 2).value or "")
        if label.upper().startswith("LTI") and label != LTI_TITLE:
            plan.cell(row, 2).value = LTI_TITLE
            print(f"  relabelled plan row {row} to {LTI_TITLE!r}")


def plan_run_test(quoted: str) -> str:
    """The 'this case has been run' half of the plan's SUMPRODUCT formulas."""
    return "+".join(f'(LOWER({quoted}!G2:G2000)="{status}")'
                    for status in ("pass", "fail", "failed", "blocked", "na", "n/a",
                                   "not applicable"))


def write_plan_row(plan, row: int, index: int, label: str, sheet_name: str,
                   automated: str | None) -> None:
    quoted = quote_sheetname(sheet_name)
    run = plan_run_test(quoted)
    plan.cell(row, 1).value = index
    plan.cell(row, 2).value = label
    plan.cell(row, 3).value = f"=COUNTA({quoted}!B2:B2000)"
    for column, tier in ((4, "Smoke"), (5, "Core"), (6, "Extended")):
        plan.cell(row, column).value = f'=COUNTIF({quoted}!J2:J2000,"{tier}")'
    plan.cell(row, 9).value = f'=D{row}+IF(G{row}="Yes",E{row},0)'
    plan.cell(row, 10).value = (
        f'=SUMPRODUCT(({quoted}!J2:J2000="Smoke")*({run}))'
        f'+IF(G{row}="Yes",SUMPRODUCT(({quoted}!J2:J2000="Core")*({run})),0)'
    )
    plan.cell(row, 11).value = automated
    plan.cell(row, 12).value = f"=IF(I{row}=0,0,J{row}/I{row})"


def reorder_plan(book) -> None:
    """Put the plan's module rows in QA's order and rebuild the TOTAL row.

    Every formula is regenerated from the row's own sheet, so a module can move
    without dragging stale row references (I/J/L refer to their own row).
    """
    plan = book[PLAN]
    old_total_row = next(
        (row for row in range(PLAN_FIRST_ROW, plan.max_row + 1)
         if str(plan.cell(row, 2).value or "").strip().upper() == "TOTAL"),
        plan.max_row + 1,
    )

    modules = []
    for row in range(PLAN_FIRST_ROW, old_total_row):
        label = str(plan.cell(row, 2).value or "").strip()
        if not label:
            continue
        name = resolve(book, label)
        if name is None:
            print(f"  dropped plan row {row} ({label}) - its sheet no longer exists")
            continue
        modules.append({
            "label": plan.cell(row, 2).value,
            "sheet": name,
            "automated": plan.cell(row, 11).value,
            "styles": [copy(plan.cell(row, column)._style)
                       for column in range(1, PLAN_LAST_COLUMN + 1)],
        })

    template = modules[0]["styles"]
    known = {module["sheet"] for module in modules}
    for sheet_name, automated in PLAN_ADDITIONS.items():
        if sheet_name not in known:
            modules.append({"label": sheet_name, "sheet": sheet_name,
                            "automated": automated, "styles": [copy(s) for s in template]})
            print(f"  added missing plan row for {sheet_name}")

    priority = {name: position for position, name in enumerate(PLAN_ORDER)}
    modules.sort(key=lambda module: priority.get(module["sheet"].strip(), len(priority)))

    row = PLAN_FIRST_ROW
    for index, module in enumerate(modules, start=1):
        for column, style in enumerate(module["styles"], start=1):
            plan.cell(row, column)._style = style
        for column in range(1, PLAN_LAST_COLUMN + 1):
            plan.cell(row, column).value = None
        write_plan_row(plan, row, index, module["label"], module["sheet"],
                       module["automated"] or "NS")
        row += 1

    last_module = row - 1
    total_row = row
    old_total_styles = [copy(plan.cell(old_total_row, column)._style)
                        for column in range(1, PLAN_LAST_COLUMN + 1)]
    for column, style in enumerate(old_total_styles, start=1):
        plan.cell(total_row, column)._style = style
        plan.cell(total_row, column).value = None
    plan.cell(total_row, 2).value = "TOTAL"
    for column, letter in ((3, "C"), (4, "D"), (5, "E"), (6, "F"), (9, "I"), (10, "J")):
        plan.cell(total_row, column).value = (
            f"=SUM({letter}{PLAN_FIRST_ROW}:{letter}{last_module})"
        )
    plan.cell(total_row, 12).value = (
        f"=IF(I{total_row}=0,0,J{total_row}/I{total_row})"
    )

    for stale in range(total_row + 1, plan.max_row + 1):
        for column in range(1, PLAN_LAST_COLUMN + 1):
            plan.cell(stale, column).value = None
    print(f"  ordered {len(modules)} module rows, TOTAL now sums "
          f"C{PLAN_FIRST_ROW}:C{last_module}")


def build_plan(book) -> dict[str, float]:
    plan = book[PLAN]
    cached: dict[str, float] = {}
    totals = {key: 0 for key in ("C", "D", "E", "F", "I", "J")}
    last_row = PLAN_FIRST_ROW - 1

    for row in range(PLAN_FIRST_ROW, plan.max_row + 1):
        label = str(plan.cell(row, 2).value or "").strip()
        if not label or label.upper() == "TOTAL":
            continue
        name = resolve(book, label)
        if name is None:
            print(f"  ! plan row {row}: no sheet matches {label!r} - cache skipped")
            continue
        source = book[name]
        tiers = tier_counts(source)
        impacted = str(plan.cell(row, 7).value or "").strip().lower() == "yes"
        planned = tiers["Smoke"] + (tiers["Core"] if impacted else 0)
        executed = tiers["SmokeRun"] + (tiers["CoreRun"] if impacted else 0)
        values = {
            "C": cases(source, SUMMARY_COL),
            "D": tiers["Smoke"],
            "E": tiers["Core"],
            "F": tiers["Extended"],
            "I": planned,
            "J": executed,
        }
        for letter, value in values.items():
            cached[f"{letter}{row}"] = value
            totals[letter] += value
        cached[f"L{row}"] = executed / planned if planned else 0
        last_row = row

    total_row = last_row + 1
    if str(plan.cell(total_row, 2).value or "").strip().upper() == "TOTAL":
        for letter, value in totals.items():
            cached[f"{letter}{total_row}"] = value
        cached[f"L{total_row}"] = totals["J"] / totals["I"] if totals["I"] else 0
    return cached


def worksheet_paths(entries: dict[str, bytes]) -> dict[str, str]:
    relationships = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
    targets = {
        node.get("Id"): node.get("Target")
        for node in ElementTree.fromstring(entries["xl/_rels/workbook.xml.rels"])
    }
    paths = {}
    for node in ElementTree.fromstring(entries["xl/workbook.xml"]).iter():
        if not node.tag.endswith("}sheet"):
            continue
        target = targets[node.get(f"{relationships}id")].lstrip("/")
        paths[node.get("name")] = target if target.startswith("xl/") else f"xl/{target}"
    return paths


def write_caches(path: Path, caches: dict[str, dict[str, float]]) -> int:
    with zipfile.ZipFile(path, "r") as archive:
        entries = {name: archive.read(name) for name in archive.namelist()}
    paths = worksheet_paths(entries)
    written = 0

    for sheet_name, values in caches.items():
        worksheet = paths[sheet_name]
        xml = entries[worksheet].decode("utf-8")
        for coordinate, value in values.items():
            pattern = re.compile(
                rf'(<c\b[^>]*\br="{re.escape(coordinate)}"[^>]*>)(.*?)(</c>)', re.DOTALL
            )

            def replace(match: re.Match[str]) -> str:
                body = match.group(2)
                if "<f" not in body:  # a typed value, nothing to cache
                    return match.group(0)
                body = re.sub(r"<v(?:\s*/>|>.*?</v>)", "", body, flags=re.DOTALL)
                return f"{match.group(1)}{body}<v>{value}</v>{match.group(3)}"

            xml, hits = pattern.subn(replace, xml, count=1)
            written += hits
        entries[worksheet] = xml.encode("utf-8")

    staging = path.with_suffix(".cache.tmp.xlsx")
    with zipfile.ZipFile(staging, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    os.replace(staging, path)
    return written


def publish() -> None:
    openpyxl.load_workbook(STAGING)
    for attempt in range(10):
        try:
            os.replace(STAGING, WORKBOOK)
            return
        except PermissionError:
            if attempt == 9:
                raise
            time.sleep(1)


def main() -> None:
    STAGING.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(WORKBOOK, STAGING)
    book = openpyxl.load_workbook(STAGING)

    print(f"{PLAN}:")
    repair_sheet_names(book)
    reorder_plan(book)
    plan_cache = build_plan(book)
    dashboard_cache = build_dashboard(book)

    book.save(STAGING)
    written = write_caches(STAGING, {DASHBOARD: dashboard_cache, PLAN: plan_cache})
    publish()

    print(f"cached {written} formula cells "
          f"({len(dashboard_cache)} dashboard, {len(plan_cache)} plan)\n")
    check = openpyxl.load_workbook(WORKBOOK, data_only=True)[DASHBOARD]
    header = [check.cell(4, column).value for column in (1, 2, 3, 5, 6, 7, 8)]
    print(" | ".join(str(value) for value in header))
    for row in range(FIRST_ROW, check.max_row + 1):
        if not str(check.cell(row, 2).value or "").strip():
            continue
        values = [check.cell(row, column).value for column in (1, 2, 3, 5, 6, 7, 8)]
        pct = values[-1]
        values[-1] = f"{pct:.1%}" if isinstance(pct, (int, float)) else pct
        print(" | ".join("" if value is None else str(value) for value in values))


if __name__ == "__main__":
    main()
