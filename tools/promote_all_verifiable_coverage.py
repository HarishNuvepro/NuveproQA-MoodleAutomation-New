"""
Promote every Kiwi row that can link to an existing Cucumber @tag (conversion without test runs).

- Implemented scenario (no stub Then) -> Automated=Yes
- Stub / @provisioning scenario (tag exists) -> Automated=Partial
- Automated=No -> unchanged (QA)
- Does not edit QA-owned dashboard sheet layouts

Run before execution; then: python tools/sync_kiwi_and_traceability.py
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
WORKBOOK = ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"
FEATURES = ROOT / "src" / "test" / "resources" / "features"
COVERAGE = "Automation_Coverage"
SKIP_SHEETS = {"Regression_Dashboard", "Release_Regression_Plan", "Automation_Coverage"}

STUB_MARKERS = (
    "needs a live",
    "needs this reopen",
    "lifecycle case needs",
    "cooldown case needs",
    "needs attempt",
    "needs external",
    "fixture deferred",
    "deferred to sandbox",
)

META_TAGS = {
    "provisioning", "freshlab", "guided", "guidedlab", "assessment-cleanup",
    "cat_userflow", "sandbox", "slow", "watch", "labactions", "users",
    "assessment", "playground", "cloudlabs", "wip", "skip", "settings",
    "augmented", "fresh_activity", "sandbox-cleanup", "predelivery", "roles",
    "tags", "reports", "moodle", "cleanup", "assessdeviation", "deviation",
    "easybatch1", "easybatch2", "mediumbatch1", "contentsource", "difficulty",
    "randomactivity", "mci", "mylabs", "mylabstheme", "activities_parity",
    "assessment_manage", "assessment_sandbox_parity", "cloudlabsconfig",
    "difficultyrestore", "rptdeviation", "predelivery-mutate", "skip",
}

TAG_RE = re.compile(r"@([A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*)")

PRIOR_SCRIPTS = (
    "update_cloudlabs_config_coverage.py",
    "update_randomization_activity_coverage.py",
    "update_assessment_sandbox_parity_coverage.py",
    "update_assessment_medium_batch1_coverage.py",
    "update_assessment_medium_batch1c_coverage.py",
    "update_moodle_cloudlabs_integration_coverage.py",
    "update_sequential_completion_coverage.py",
    "update_moodle_settings_coverage.py",
)


def norm(tag: str) -> str:
    return tag.lstrip("@").upper()


def parse_scenarios() -> dict[str, dict]:
    """NORM_TAG -> {tag, feature, stub, provisioning, deviation}."""
    catalog: dict[str, dict] = {}
    for path in sorted(FEATURES.glob("*.feature")):
        lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            if not line.lower().startswith("scenario"):
                i += 1
                continue
            # Collect @tags from lines above until Feature/Scenario/Background
            tags: list[str] = []
            j = i - 1
            while j >= 0:
                prev = lines[j].strip()
                if prev.lower().startswith(("feature", "scenario", "background", "examples")):
                    break
                if prev.startswith("@"):
                    for m in TAG_RE.finditer(prev):
                        t = m.group(1)
                        if t.lower() not in META_TAGS:
                            tags.append(t)
                j -= 1
            body: list[str] = []
            i += 1
            while i < len(lines):
                s = lines[i].strip()
                if s.startswith("@") or s.lower().startswith("scenario"):
                    break
                body.append(s)
                i += 1
            body_text = "\n".join(body).lower()
            stub = any(m in body_text for m in STUB_MARKERS)
            prov = any(t.lower() == "provisioning" for t in tags) or "@provisioning" in " ".join(lines[j:i])
            dev = any(t.lower() == "deviation" for t in tags)
            name = line.split(":", 1)[1].strip() if ":" in line else line
            for t in tags:
                key = norm(t)
                catalog[key] = {
                    "tag": t,
                    "feature": path.name,
                    "stub": stub,
                    "provisioning": prov,
                    "deviation": dev,
                    "scenario": name,
                }
    return catalog


def tags_in_text(text: str) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for m in TAG_RE.finditer(text or ""):
        t = m.group(1)
        if t.lower() in META_TAGS:
            continue
        k = norm(t)
        if k not in seen:
            seen.add(k)
            out.append(t)
    for m in re.finditer(r"\b(CGAP-[A-Za-z0-9-]+)\b", text or ""):
        t = m.group(1)
        k = norm(t)
        if k not in seen:
            seen.add(k)
            out.append(t)
    return out


def pick_tag(ref: str, sno: str, catalog: dict[str, dict]) -> str | None:
    # Prefer explicit case id (CGAP-*) then ref tags.
    for src in (sno, ref):
        for t in tags_in_text(src):
            if norm(t) in catalog:
                return t
    m = re.search(r"\b(A\d+[a-z]*)\b", ref, re.I)
    if m:
        t = m.group(1)
        if norm(t) in catalog:
            return t
    return None


def refresh_coverage_row(book, sheet_name: str) -> None:
    sh = book[sheet_name]
    counts = dict.fromkeys(("yes", "partial", "deviation", "deferred", "no"), 0)
    total = 0
    for row in range(2, sh.max_row + 1):
        if not str(sh.cell(row, 1).value or "").strip():
            continue
        total += 1
        st = str(sh.cell(row, 11).value or "").strip().lower()
        if st.startswith("no"):
            counts["no"] += 1
        elif st in counts:
            counts[st] += 1
        else:
            counts["no"] += 1
    cov = book[COVERAGE]
    for r in range(2, cov.max_row + 1):
        if str(cov.cell(r, 1).value or "").strip() == sheet_name:
            for i, k in enumerate(("yes", "partial", "deviation", "deferred", "no"), start=2):
                cov.cell(r, i).value = counts[k]
            cov.cell(r, 7).value = total
            auto = total - counts["no"]
            cov.cell(r, 8).value = f"{round(100 * counts['yes'] / auto)}%" if auto else "0%"
            break


def run_prior_scripts() -> None:
    for name in PRIOR_SCRIPTS:
        p = ROOT / "tools" / name
        if p.is_file():
            print(f">>> {name}")
            subprocess.run([sys.executable, str(p)], cwd=ROOT, check=False)


def main() -> None:
    run_prior_scripts()
    catalog = parse_scenarios()
    print(f"Cucumber case tags catalogued: {len(catalog)}")

    book = openpyxl.load_workbook(WORKBOOK)
    touched: set[str] = set()
    n = 0
    for sheet_name in book.sheetnames:
        if sheet_name in SKIP_SHEETS:
            continue
        sh = book[sheet_name]
        if sh.max_column < 12:
            continue
        for row in range(2, sh.max_row + 1):
            sno = str(sh.cell(row, 1).value or "").strip()
            if not sno:
                continue
            auto = str(sh.cell(row, 11).value or "").strip()
            ref = str(sh.cell(row, 12).value or "")
            if auto.lower() == "no":
                continue

            tag = pick_tag(ref, sno, catalog)
            if not tag:
                continue
            meta = catalog[norm(tag)]
            new_ref = f"{meta['feature']} @{meta['tag']}"
            if meta["deviation"]:
                new_auto = "Deviation"
            elif meta["stub"] or meta["provisioning"]:
                new_auto = "Partial"
            else:
                new_auto = "Yes"

            cur = auto.lower()
            if cur in ("yes", "partial", "deviation") and f"@{meta['tag']}" in ref and meta["feature"] in ref:
                continue
            if cur == "yes" and new_auto == "partial":
                continue
            if cur == "deviation" and new_auto == "deviation" and meta["feature"] in ref:
                continue

            sh.cell(row, 11).value = new_auto
            sh.cell(row, 12).value = new_ref
            status = str(sh.cell(row, 7).value or "").strip().lower()
            if status in ("proposed", "confirmed", "pass"):
                sh.cell(row, 7).value = "Blocked" if new_auto == "Partial" else "Pass"
            touched.add(sheet_name)
            n += 1

    for sheet_name in touched:
        refresh_coverage_row(book, sheet_name)

    book.save(WORKBOOK)
    print(f"Promoted {n} rows across {len(touched)} sheet(s)")
    print(f"saved {WORKBOOK}")


if __name__ == "__main__":
    main()
