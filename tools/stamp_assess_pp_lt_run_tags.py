"""
Rewrite Assessment / Practice Project / Lab Templates feature tags to one
runnable id each, then stamp those same ids onto the Kiwi sheets.

Primary Yes row: feature @TAG
Other Yes rows in that combined group: Covered by @TAG
"""
from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path

from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parents[1]
FEATURES = ROOT / "src/test/resources/features"
XLSX = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"

KEEP_ALWAYS = {
    "@provisioning",
    "@fresh_activity",
    "@fresh_activity_reuse_user",
    "@timelimit_wait",
    "@practice_project_override",
    "@guidedlab",
    "@assessment-cleanup",
}

# Surviving run-id patterns (first match on a scenario is the primary tag).
ID_RE = re.compile(
    r"^@("
    r"A-create-[\w-]+"
    r"|A-user-\d+"
    r"|A-userattempts"
    r"|A-override"
    r"|A\d+admin"
    r"|A\d+"
    r"|CGAP-A-\d+"
    r"|PP-\d+"
    r"|CGAP-PP-\d+"
    r"|CGAP-PGG-\d+"
    r"|LT-\d+"
    r"|LT-role"
    r"|CGAP-LT-\d+"
    r")$"
)

SHEETS = {
    "Moodle_Assessment_Labs": None,
    "Practice_Project": None,
    "Moodle_Lab_Templates": None,
}

FEATURE_FILES = [
    "assessment_create.feature",
    "assessment_user_functionality.feature",
    "assessment_lab_actions.feature",
    "assessment_cleanup.feature",
    "assessment_disabling_complete.feature",
    "assessment_show_hide_grades.feature",
    "assessment_hide_final_grade.feature",
    "assessment_retake_cooldown.feature",
    "assessment_reopening.feature",
    "assessment_test_case_marks.feature",
    "assessment_randomization_problem_statements.feature",
    "assessment_problem_statement_git_url.feature",
    "assessment_user_attempts_page.feature",
    "practice_project.feature",
    "practice_project_actions.feature",
    "practice_project_user_functionality.feature",
    "practice_project_reports.feature",
    "labtemplates.feature",
]


def is_tag_line(stripped: str) -> bool:
    return stripped.startswith("@") and not stripped.lower().startswith("feature")


def split_tags(line: str) -> list[str]:
    return [t for t in line.strip().split() if t.startswith("@")]


def classify(tags: list[str]) -> tuple[str | None, list[str]]:
    primary = None
    extras = []
    for tag in tags:
        if ID_RE.match(tag):
            if primary is None:
                primary = tag
            else:
                extras.append(tag)
        elif tag in KEEP_ALWAYS:
            extras.append(tag)
    return primary, extras


def rewrite_features() -> tuple[dict[str, str], dict[str, str]]:
    """Return tag->feature and leftover-alias->primary."""
    tag_feature: dict[str, str] = {}
    combined_into: dict[str, str] = {}
    for name in FEATURE_FILES:
        path = FEATURES / name
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines(keepends=True)
        out = []
        pending: list[str] = []
        pending_indent = ""
        last_primary: str | None = None

        def flush_pending():
            nonlocal pending, pending_indent, last_primary
            if not pending:
                return
            primary, extras = classify(pending)
            kept = []
            if primary:
                kept.append(primary)
                tag_feature[primary] = name
                last_primary = primary
                for alias in extras:
                    if (ID_RE.match(alias) or re.match(r"^@TC-\d+$", alias)) and alias != primary:
                        combined_into.setdefault(alias, primary)
                    elif alias in KEEP_ALWAYS and alias not in kept:
                        kept.append(alias)
            else:
                for alias in extras:
                    if alias not in kept:
                        kept.append(alias)
                if name == "assessment_cleanup.feature" and "@assessment-cleanup" not in kept:
                    kept.append("@assessment-cleanup")
            if kept:
                out.append(pending_indent + " ".join(kept) + "\n")
            pending = []

        for line in lines:
            stripped = line.strip()
            if is_tag_line(stripped):
                if not pending:
                    pending_indent = re.match(r"^(\s*)", line).group(1)
                pending.extend(split_tags(line))
                continue
            if pending and (stripped.startswith("Scenario") or stripped.startswith("Examples")):
                # Example-only aliases (@TC-4) are dropped; outline keeps @A-create-difficulty.
                if stripped.startswith("Examples"):
                    if last_primary:
                        for alias in pending:
                            if re.match(r"^@TC-\d+$", alias) or ID_RE.match(alias):
                                combined_into.setdefault(alias, last_primary)
                    pending = []
                    out.append(line)
                    continue
                flush_pending()
                if name == "assessment_create.feature":
                    line = re.sub(r"(Scenario Outline:|Scenario:)\s*TC-\d+\s*", r"\1 ", line)
                    line = re.sub(r"(Scenario Outline:|Scenario:)\s*TC-<tc>\s*", r"\1 ", line)
                out.append(line)
                continue
            if pending:
                # comment or blank between tag lines
                if stripped.startswith("#") or stripped == "":
                    flush_pending()
                    out.append(line)
                    continue
                flush_pending()
            # Drop feature-level suite tags already consumed; Feature: line stays.
            if stripped.startswith("Feature:"):
                out.append(line)
                continue
            out.append(line)
        flush_pending()

        # Strip leftover feature-level suite tags sitting above Feature:
        cleaned = []
        for line in out:
            st = line.strip()
            if is_tag_line(st) and "Feature:" not in "".join(cleaned[-3:]):
                # keep only leftover KEEP / id tags that belong to a following Feature? already flushed
                pass
            cleaned.append(line)
        path.write_text("".join(out), encoding="utf-8")
        print("rewrote", name)
    # Never remap a tag that still has its own scenario.
    for primary in list(tag_feature):
        combined_into.pop(primary, None)
    return tag_feature, combined_into


REF_TAG_RE = re.compile(
    r"@(?:A-create-[\w-]+|A-user-\d+|A-userattempts|A-override|A\d+admin|A\d+"
    r"|CGAP-A-\d+|PP-\d+|CGAP-PP-\d+|CGAP-PGG-\d+|LT-\d+|LT-role|CGAP-LT-\d+|TC-\d+)"
)


def stamp_sheet(ws, tag_feature: dict[str, str], combined_into: dict[str, str]) -> int:
    groups: dict[str, list[int]] = defaultdict(list)
    skipped = []
    for r in range(2, ws.max_row + 1):
        auto = str(ws.cell(r, 11).value or "").strip()
        if auto.lower() != "yes":
            continue
        ref = str(ws.cell(r, 12).value or "")
        found = REF_TAG_RE.findall(ref)
        if not found:
            skipped.append((r, ref))
            continue
        mapped = []
        for raw in found:
            tag = raw if raw.startswith("@") else "@" + raw
            tag = combined_into.get(tag, tag)
            if tag not in mapped:
                mapped.append(tag)
        primary = next((t for t in mapped if t in tag_feature), mapped[0])
        groups[primary].append(r)
    changed = 0
    for tag, rows in groups.items():
        rows.sort()
        feature = tag_feature.get(tag, "")
        primary_ref = f"{feature} {tag}".strip() if feature else tag
        covered = f"Covered by {tag}"
        for i, r in enumerate(rows):
            new = primary_ref if i == 0 else covered
            if str(ws.cell(r, 12).value or "") != new:
                ws.cell(r, 12).value = new
                changed += 1
            print(f"  {r:3} {new}")
    for r, ref in skipped:
        print(f"  SKIP {r}: {ref[:100]}")
    return changed


def main():
    tag_feature, combined_into = rewrite_features()
    print("primaries", len(tag_feature), "aliases", len(combined_into))
    wb = load_workbook(XLSX)
    print("sheets", [n for n in wb.sheetnames if "Assess" in n or "Practice" in n or "Lab_Temp" in n or "Lab Temp" in n])
    total = 0
    for name in wb.sheetnames:
        key = name.strip()
        if key in ("Moodle_Assessment_Labs", "Practice_Project", "Moodle_Lab_Templates"):
            print("====", name, "====")
            total += stamp_sheet(wb[name], tag_feature, combined_into)
    tmp = XLSX.with_suffix(".xlsx.tmp")
    wb.save(tmp)
    wb.close()
    tmp.replace(XLSX)
    print("Saved", XLSX, "changed", total)


if __name__ == "__main__":
    main()
