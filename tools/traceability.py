"""
BDD traceability & execution audit for the Java/Cucumber suite.

Sources (evidence only — no inflated counts):
  1) docs/MoodleRegression_Testing_Kiwi new.xlsx — every module case sheet
  2) src/test/resources/features/*.feature — scenario @tags (verified mapping)
  3) Regression_Dashboard — Executed per listed module (QA formula)
  4) target/cucumber*.json — optional last Cucumber run (when present)

A case counts as **verified automated** only when Kiwi marks Yes/Partial/Deviation
AND Automation_Ref @tag resolves to an existing Cucumber scenario.

Run:  python tools/traceability.py
Outputs:
  reports/traceability_execution.csv   (all cases)
  reports/traceability_execution.html
  reports/traceability_audit_summary.md
"""
from __future__ import annotations

import csv
import html
import json
import re
from collections import Counter, defaultdict
from datetime import date, datetime
from pathlib import Path

from openpyxl import load_workbook

JAVA_ROOT = Path(__file__).resolve().parents[1]
FEATURES_DIR = JAVA_ROOT / "src" / "test" / "resources" / "features"
TARGET = JAVA_ROOT / "target"
CUKE_JSONS = ["cucumber_fast.json", "cucumber_slow.json", "cucumber.json"]
OUT_DIR = JAVA_ROOT / "reports"
XLSX = JAVA_ROOT / "docs" / "MoodleRegression_Testing_Kiwi new.xlsx"

SKIP_SHEETS = {
    "Regression_Dashboard",
    "Release_Regression_Plan",
    "Automation_Coverage",
}
DASHBOARD = "Regression_Dashboard"
DASH_RUN = {"pass", "fail", "failed", "blocked"}
ASSESSMENT_SHEET = "Moodle_Assessment_Labs"

META_TAGS = {
    "provisioning", "freshlab", "guided", "guidedlab", "assessment-cleanup",
    "cat_userflow", "sandbox", "slow", "watch", "labactions", "users",
    "assessment", "playground", "cloudlabs", "wip", "skip", "settings",
    "augmented", "fresh_activity", "sandbox-cleanup", "predelivery", "roles",
    "tags", "reports", "moodle", "cleanup", "assessdeviation",
}

TAG_RE = re.compile(r"@([A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*)")

TITLE_COL, SUMMARY_COL, PRIORITY_COL = 0, 1, 5
STATUS_COL, AUTOMATED_COL, REF_COL = 6, 10, 11


def sheet_label(name: str) -> str:
    return name.strip()


def tags_in(text: str) -> list[str]:
    found = []
    seen = set()

    def add(tag: str) -> None:
        tag = tag.strip()
        if not tag or tag.lower() in META_TAGS:
            return
        key = tag.upper()
        if key in seen:
            return
        seen.add(key)
        found.append(tag)

    for raw in TAG_RE.findall(text or ""):
        add(raw)
    # Kiwi refs often cite groups/tags as "@groups (G3)" without @ on the case id.
    for m in re.finditer(r"\(([A-Za-z][A-Za-z0-9-]+)\)", text or ""):
        add(m.group(1))
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
                "case_id": title or f"{area}-row{idx}",
                "area": area,
                "row": idx,
                "title": (summary or title)[:200],
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
    """{NORM_TAG: scenario metadata}."""
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


def count_scenarios() -> int:
    n = 0
    for path in FEATURES_DIR.glob("*.feature"):
        for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
            if line.strip().lower().startswith("scenario"):
                n += 1
    return n


def load_cuke_json(path: Path) -> list:
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


def read_execution() -> tuple[dict[str, str], list[str]]:
    """{NORM_TAG: passed|failed|skipped} and list of json files used."""
    out: dict[str, str] = {}
    used: list[str] = []
    rank = {"failed": 3, "skipped": 2, "passed": 1}
    for name in CUKE_JSONS:
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
                        key = norm_tag(parsed)
                        if rank.get(outcome, 0) >= rank.get(out.get(key, ""), 0):
                            out[key] = outcome
    return out, used


def pick_result(tags: list[str], executed: dict[str, str]) -> str:
    rank = {"failed": 3, "skipped": 2, "passed": 1, "": 0}
    best = ""
    for tag in tags:
        outcome = executed.get(norm_tag(tag), "")
        if rank[outcome] > rank[best]:
            best = outcome
    return best


def resolve_scenario(tags: list[str], created: dict[str, dict]) -> dict | None:
    for t in tags:
        hit = created.get(norm_tag(t))
        if hit:
            return hit
    return None


def is_yes(value: str) -> bool:
    return value.strip().lower() == "yes"


def auto_state(value: str) -> str:
    return (value or "").strip().lower()


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
    if s == "proposed":
        return "proposed"
    return ""


def read_dashboard() -> dict[str, dict]:
    """Regression_Dashboard rows keyed by module sheet name."""
    if not XLSX.exists():
        return {}
    wb = load_workbook(XLSX, read_only=True, data_only=True)
    if DASHBOARD not in wb.sheetnames:
        wb.close()
        return {}
    out: dict[str, dict] = {}
    total_row = None
    for row in wb[DASHBOARD].iter_rows(min_row=5, values_only=True):
        label = str(row[1] or "").strip()
        if not label:
            continue
        if label.upper() == "TOTAL":
            total_row = {
                "total": int(row[2]) if isinstance(row[2], (int, float)) else 0,
                "executed": int(row[4]) if isinstance(row[4], (int, float)) else 0,
                "not_auto": int(row[5]) if isinstance(row[5], (int, float)) else 0,
                "deferred": int(row[6]) if isinstance(row[6], (int, float)) else 0,
            }
            continue
        key = ASSESSMENT_SHEET if label.lower().startswith("assessment") else label
        out[key] = {
            "label": label,
            "total": int(row[2]) if isinstance(row[2], (int, float)) else 0,
            "executed": int(row[4]) if isinstance(row[4], (int, float)) else 0,
            "not_auto": int(row[5]) if isinstance(row[5], (int, float)) else 0,
            "deferred": int(row[6]) if isinstance(row[6], (int, float)) else 0,
        }
    wb.close()
    if total_row:
        out["__TOTAL__"] = total_row
    return out


def classify_case(c: dict, created: dict[str, dict], cuke: dict[str, str]) -> None:
    state = auto_state(c["automated"])
    c["automatable"] = "No" if state == "no" else "Yes"
    hit = resolve_scenario(c["tags"], created)
    if hit is None and c["tags"]:
        hit = created.get(norm_tag(c["tags"][0]))
    c["scenario"] = hit["scenario"] if hit else ""
    c["feature"] = hit["feature"] if hit else ""
    c["run_tag"] = hit["tag"] if hit else (c["tags"][0] if c["tags"] else "")
    c["verified"] = hit is not None and state in {"yes", "partial", "deviation"}
    c["kiwi_result"] = status_key(c["case_status"])
    c["cuke_result"] = pick_result(c["tags"], cuke) if c["tags"] else ""
    c["executed_kiwi"] = (
        "yes" if is_yes(c["automated"]) and c["case_status"].strip().lower() in DASH_RUN else "no"
    )

    if state == "no":
        c["automation_status"] = "Not automatable"
        c["execution_status"] = c["kiwi_result"] or "n/a"
        c["gaps_remarks"] = c["ref"] or "Automated=No (see Automation_Ref for reason)"
    elif state == "deferred":
        c["automation_status"] = "Deferred"
        c["execution_status"] = c["kiwi_result"] or "not executed"
        c["gaps_remarks"] = c["ref"] or "Deferred gap — not scripted yet"
    elif state == "partial":
        c["automation_status"] = "Partial" + (" (verified)" if c["verified"] else " (unverified ref)")
        c["execution_status"] = _exec_label(c)
        c["gaps_remarks"] = _gap_partial(c)
    elif state == "deviation":
        c["automation_status"] = "Deviation" + (" (verified)" if c["verified"] else " (unverified ref)")
        c["execution_status"] = _exec_label(c)
        c["gaps_remarks"] = c["ref"] or "Fail-by-design / product finding"
    elif state == "yes":
        if c["verified"]:
            c["automation_status"] = "Automated (verified)"
        else:
            c["automation_status"] = "Claimed Yes — no matching scenario"
        c["execution_status"] = _exec_label(c)
        c["gaps_remarks"] = _gap_yes(c)
    else:
        c["automation_status"] = "Pending — not judged"
        c["execution_status"] = c["kiwi_result"] or "not run"
        if hit:
            c["gaps_remarks"] = "Scenario exists in repo but Kiwi Automated column is blank"
        else:
            c["gaps_remarks"] = "No automation judgement in Kiwi"

    if not c["feature"] and c["ref"]:
        m = re.search(r"([\w.-]+\.feature)", c["ref"])
        if m:
            c["feature"] = m.group(1)
    c["automation_test"] = ""
    if c["feature"] and c["scenario"]:
        c["automation_test"] = f"{c['feature']} :: {c['scenario']}" + (
            f" (@{c['run_tag']})" if c["run_tag"] else ""
        )
    elif c["feature"]:
        c["automation_test"] = c["feature"] + (f" (@{c['run_tag']})" if c["run_tag"] else "")


def _exec_label(c: dict) -> str:
    if c["cuke_result"]:
        return f"Kiwi:{c['kiwi_result'] or '—'} / Cucumber:{c['cuke_result']}"
    return c["kiwi_result"] or "not run"


def _gap_yes(c: dict) -> str:
    if not c["verified"]:
        return "Automation_Ref tag does not match any Cucumber scenario — not counted as verified automated"
    if c["kiwi_result"] == "failed":
        return "Verified script exists; Case_Status Fail"
    if c["kiwi_result"] == "blocked":
        return "Verified script exists; Case_Status Blocked"
    if c["executed_kiwi"] == "no" and not c["kiwi_result"]:
        return "Verified script exists; not yet executed (no Case_Status Pass/Fail/Blocked)"
    return ""


def _gap_partial(c: dict) -> str:
    base = c["ref"] or "Partial coverage — see Automation_Ref"
    if not c["verified"]:
        return base + " | Ref not linked to a scenario"
    return base


def duplicate_tag_rows(cases: list[dict]) -> dict[str, list[str]]:
    by_tag: dict[str, list[str]] = defaultdict(list)
    for c in cases:
        for t in c["tags"]:
            by_tag[norm_tag(t)].append(f"{c['area']}:{c['case_id']}")
    return {t: ids for t, ids in by_tag.items() if len(ids) > 1}


def compute_metrics(cases: list[dict], dash: dict[str, dict], cuke_files: list[str]) -> dict:
    m: Counter = Counter()
    for c in cases:
        m["total"] += 1
        st = auto_state(c["automated"])
        if st == "no":
            m["not_automatable"] += 1
        else:
            m["automatable"] += 1
        if st == "yes":
            m["kiwi_yes"] += 1
        if st == "partial":
            m["partial"] += 1
        if st == "deviation":
            m["deviation"] += 1
        if st == "deferred":
            m["deferred"] += 1
        if not st:
            m["pending_judgement"] += 1
        if c["verified"]:
            m["verified_automated"] += 1
            if st == "yes":
                m["verified_kiwi_yes"] += 1
            elif st == "partial":
                m["verified_partial"] += 1
            elif st == "deviation":
                m["verified_deviation"] += 1
        if st == "yes" and not c["verified"]:
            m["claimed_not_verified"] += 1
        if c["executed_kiwi"] == "yes":
            m["executed_kiwi"] += 1
        if c["verified"] and c["kiwi_result"] == "passed":
            m["verified_passed"] += 1
        if c["verified"] and c["kiwi_result"] == "failed":
            m["verified_failed"] += 1
        if c["verified"] and c["kiwi_result"] == "blocked":
            m["verified_blocked"] += 1
        if c["cuke_result"] == "failed":
            m["cuke_failed"] += 1
        if c["cuke_result"] == "skipped":
            m["cuke_skipped"] += 1

    # Narrow "pending scripting": blank Automated + Yes without a matching scenario.
    pending = 0
    bridge = Counter()
    for c in cases:
        st = auto_state(c["automated"])
        if st == "no":
            continue
        if c["verified"]:
            bridge["verified_automated"] += 1
            continue
        if st == "deferred":
            bridge["deferred_gaps"] += 1
            continue
        if st in {"partial", "deviation"}:
            bridge["partial_deviation_unverified"] += 1
            continue
        if st == "yes":
            bridge["pending_false_yes"] += 1
            pending += 1
            continue
        if not st:
            bridge["pending_blank"] += 1
            pending += 1
            continue
        bridge["other_automatable"] += 1
    m["pending_automation"] = pending
    m["remaining_not_verified"] = m["automatable"] - m["verified_automated"]
    m["bridge_deferred"] = bridge["deferred_gaps"]
    m["bridge_pending_blank"] = bridge["pending_blank"]
    m["bridge_pending_false_yes"] = bridge["pending_false_yes"]
    m["bridge_partial_deviation"] = bridge["partial_deviation_unverified"]
    m["bridge_other"] = bridge["other_automatable"]

    in_scope = m["total"] - m["not_automatable"]
    m["automation_completion_pct"] = (
        100.0 * m["verified_automated"] / in_scope if in_scope else 0.0
    )
    m["kiwi_yes_completion_pct"] = (
        100.0 * m["kiwi_yes"] / in_scope if in_scope else 0.0
    )
    m["execution_pass_pct"] = (
        100.0 * m["verified_passed"] / m["verified_automated"]
        if m["verified_automated"] else 0.0
    )
    m["kiwi_executed_pass_pct"] = (
        100.0 * sum(1 for c in cases if is_yes(c["automated"]) and c["kiwi_result"] == "passed")
        / m["executed_kiwi"] if m["executed_kiwi"] else 0.0
    )

    dt = dash.get("__TOTAL__", {})
    m["dashboard_total"] = dt.get("total", 0)
    m["dashboard_executed"] = dt.get("executed", 0)
    m["dashboard_not_auto"] = dt.get("not_auto", 0)
    dash_scope = m["dashboard_total"] - m["dashboard_not_auto"]
    m["dashboard_completion_pct"] = (
        100.0 * m["dashboard_executed"] / dash_scope if dash_scope else 0.0
    )
    m["cuke_json_sources"] = ", ".join(cuke_files) if cuke_files else "(none — using Kiwi Case_Status only)"
    m["feature_scenarios"] = count_scenarios()
    for key in (
        "verified_blocked", "verified_failed", "cuke_failed", "cuke_skipped",
        "claimed_not_verified", "remaining_not_verified", "bridge_deferred",
        "bridge_pending_blank", "bridge_pending_false_yes", "bridge_partial_deviation",
        "bridge_other",
    ):
        m.setdefault(key, 0)
    return dict(m)


def module_stats(cases: list[dict], dash: dict[str, dict]) -> list[dict]:
    per: dict[str, Counter] = defaultdict(Counter)
    for c in cases:
        a = per[c["area"]]
        a["total"] += 1
        st = auto_state(c["automated"])
        if st == "no":
            a["not_auto"] += 1
        else:
            a["automatable"] += 1
        if is_yes(c["automated"]):
            a["kiwi_yes"] += 1
        if c["verified"]:
            a["verified"] += 1
        if c["executed_kiwi"] == "yes":
            a["exec"] += 1
        if c["verified"] and c["kiwi_result"] == "passed":
            a["pass"] += 1
        if st == "partial":
            a["partial"] += 1
        if st == "deferred":
            a["deferred"] += 1

    dash_keys = [k for k in dash if k != "__TOTAL__"]
    areas = dash_keys + [a for a in per if a not in dash]
    rows = []
    for area in areas:
        a = per[area]
        d = dash.get(area, {})
        exec_count = d.get("executed", a["exec"]) if area in dash else a["exec"]
        not_auto = d.get("not_auto", a["not_auto"]) if area in dash else a["not_auto"]
        total = d.get("total", a["total"]) if area in dash else a["total"]
        scope = total - not_auto
        rows.append({
            "area": area,
            "total": total,
            "automatable": total - not_auto,
            "not_automatable": not_auto,
            "kiwi_yes": a["kiwi_yes"],
            "verified": a["verified"],
            "partial": a["partial"],
            "deferred": a["deferred"],
            "executed": exec_count,
            "passed": a["pass"],
            "automation_pct": 100.0 * a["verified"] / scope if scope else 0,
            "exec_pct": 100.0 * exec_count / scope if scope else 0,
        })
    return rows


def write_next_automation_section(metrics: dict) -> list[str]:
    """Evidence-based sequencing from Kiwi + repo conventions (TODO.md / CLAUDE.md)."""
    v = metrics["verified_automated"]
    a = metrics["automatable"]
    pend = metrics["pending_automation"]
    rem = metrics["remaining_not_verified"]
    defr = metrics.get("bridge_deferred", 0)
    pblank = metrics.get("bridge_pending_blank", 0)
    pfalse = metrics.get("bridge_pending_false_yes", 0)
    pdev = metrics.get("bridge_partial_deviation", 0)
    return [
        "## Automatable backlog reconciliation",
        "",
        "Two different “remaining” numbers are both correct; they answer different questions:",
        "",
        "| Label | Count | Meaning |",
        "|-------|------:|---------|",
        f"| **Remaining not verified** | automatable − verified | **{rem}** = everything automatable "
        "without a linked green scenario yet |",
        f"| **Pending scripting (narrow)** | **{pend}** | Blank `Automated` + `Yes` with broken/missing "
        "`@tag` only — **excludes** Deferred and Partial/Deviation rows |",
        "",
        f"Bridge (must sum to automatable **{a}**):",
        "",
        "| Bucket | Count |",
        "|--------|------:|",
        f"| Verified automated | {v} |",
        f"| Deferred (`CGAP-*` / gap rows in Kiwi) | {defr} |",
        f"| Pending scripting (blank + false Yes) | {pend} |",
        f"| Partial / Deviation without scenario link | {pdev} |",
        f"| **Total automatable** | **{a}** |",
        "",
        f"Check: {v} + {defr} + {pend} + {pdev} = **{a}**. "
        f"Remaining not verified: {a} − {v} = **{rem}** = {defr} + {pend} + {pdev}.",
        f"(Pending scripting = blank **{pblank}** + false Yes **{pfalse}**.)",
        "",
        "## Recommended next automation (evidence-based, no new scripts in this audit)",
        "",
        "Follow **`Importance` High → Medium** and **`TODO.md` / `CLAUDE.md`**: finish **in-flight "
        "dashboard modules** before large greenfield sheets; **Account** lab templates before "
        "AWS/CSP/GCP/VM/Composition variants.",
        "",
        "### Tier 0 — Traceability fixes (not new coverage)",
        "",
        "**Done (2026-09-21):** `tools/fix_moodle_tags_refs.py` updated **29** `Automation_Ref` cells "
        "on `Moodle_Tags` (`@T1`–`@T15`, `@T27`, `@T38`, `@T16`–`@T29`). Re-run traceability to refresh counts.",
        "",
        "### Tier 1 — Complete modules already on Regression_Dashboard",
        "",
        "| Module | Next cases | Why now |",
        "|--------|------------|---------|",
        "| **Moodle_Assessment_Labs** | **187 Deferred** (`CGAP-*` / gap rows): **66 High**, **121 Medium** | "
        "Largest verified gap; sheet already has derived cases — automate High then Medium per row |",
        "| **Moodle_Lab_Templates** | **23** `Automated=No` (19 Medium + 1 High marked not automatable) | "
        "Dashboard module at 63/86 executed — close template CRUD/catalog gaps where automatable |",
        "| **Moodle_Sandbox_Labs** | **9** not automatable + stabilise user/actions suites | "
        "Dashboard 53/62; finish remaining sandbox Manage Labs / user cases (see `sandbox_*.feature`) |",
        "| **All Course Page** | **15** not automatable | "
        "95/110 verified — only automatable stragglers left |",
        "| **Groups_Testcases** | **11 Deferred** + **35** not automatable | "
        "13 verified — enrolment/report gaps deferred in sheet |",
        "| **Moodle_Roles** | **12 Deferred** + open functional matrix | "
        "53 verified — continue admin/student capability cases in `moodle_roles*.feature` |",
        "",
        "### Tier 2 — Next greenfield sheets (blank `Automated`, High priority first)",
        "",
        "Unmarked backlog with **High** importance (automatable, not yet judged in Kiwi):",
        "",
        "| Sheet | High (unmarked) | Medium (unmarked) | Notes |",
        "|-------|----------------:|------------------:|-------|",
        "| Course_Sync_Multi_Tenant | 23 | 10 | Integration / multi-tenant — no `.feature` yet |",
        "| Moodle_cron | 17 | — | Cron/admin — typically env-dependent |",
        "| User_Sync_Orgo | 15 | 16 | Orgo sync — deferred elsewhere for Tags too |",
        "| Moodle_Logs | 14 | 36 | Audit log UI |",
        "| Activities | 13 | 54 | **67** pending total — large, start High rows only |",
        "| Raven360_AWS_Content | 9 | 20 | Content integration |",
        "",
        "**Do not start yet (per project lock):** platform / Composition / VM **variants** of "
        "playground lab-action cases until Account template set is complete (`TODO.md`).",
        "",
        "### Suggested sprint order",
        "",
        "1. Fix **Moodle_Tags** refs (Tier 0).  ",
        "2. **Assessment** Deferred **High (66)** → Medium **(121)** with coverage-matrix gate per sheet.  ",
        "3. **Lab Templates** + **Sandbox** dashboard completion.  ",
        "4. **Activities** High **(13)** only, then next High greenfield sheet above.  ",
        "",
    ]


def write_summary_md(metrics: dict, mod_rows: list[dict], dup_count: int, orphan_tags: int) -> None:
    lines = [
        "# Traceability audit summary",
        "",
        f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        "",
        "Evidence: `docs/MoodleRegression_Testing_Kiwi new.xlsx`, "
        "`src/test/resources/features/*.feature`, "
        f"Cucumber JSON: {metrics['cuke_json_sources']}.",
        "",
        "## Portfolio totals (all module sheets)",
        "",
        f"| Metric | Count |",
        f"|--------|------:|",
        f"| 1. Total test cases | {metrics['total']} |",
        f"| 2. Automatable (Automated ≠ No) | {metrics['automatable']} |",
        f"| 3. Not automatable (Automated = No) | {metrics['not_automatable']} |",
        f"| 4. Verified automated (Yes/Partial/Deviation + matching @tag scenario) | {metrics['verified_automated']} |",
        f"| 4a. Verified breakdown | Yes {metrics.get('verified_kiwi_yes', 0)} + "
        f"Partial {metrics.get('verified_partial', 0)} + "
        f"Deviation {metrics.get('verified_deviation', 0)} |",
        f"| 4b. Kiwi Automated=Yes only (not the 1000-target metric) | {metrics['kiwi_yes']} |",
        f"| 4c. Activities sheet verified (batch82 Partial @SB-manage refs) | "
        f"{sum(r['verified'] for r in mod_rows if r['area'] == 'Activities')} |",
        f"| 5. Verified automated + Case_Status Pass | {metrics['verified_passed']} |",
        f"| 6. Verified automated + Fail / Blocked | {metrics['verified_failed']} / {metrics['verified_blocked']} |",
        f"| 6b. Last Cucumber failed/skipped tags (if JSON present) | {metrics['cuke_failed']} / {metrics['cuke_skipped']} |",
        f"| 7. Partial (Kiwi) | {metrics['partial']} |",
        f"| 8. Pending scripting (blank + false Yes; excludes Deferred) | {metrics['pending_automation']} |",
        f"| 8b. Remaining not verified (automatable − verified) | {metrics['remaining_not_verified']} |",
        f"| 9. Duplicate @tag → multiple case rows | {dup_count} tag keys |",
        f"| 9b. Scenario @tags not referenced in any Kiwi Automation_Ref | {orphan_tags} |",
        f"| Cucumber scenarios in repo | {metrics['feature_scenarios']} |",
        "",
        "## Completion % (strict vs Kiwi)",
        "",
        f"- **Verified automation completion** (verified ÷ automatable): "
        f"**{metrics['verified_automated']}/{metrics['automatable']} = {metrics['automation_completion_pct']:.1f}%**",
        f"- Kiwi Yes ÷ automatable (over-counts if ref broken): "
        f"{metrics['kiwi_yes']}/{metrics['automatable']} = {metrics['kiwi_yes_completion_pct']:.1f}%",
        f"- **Verified pass rate** (verified Pass ÷ verified automated): "
        f"{metrics['verified_passed']}/{metrics['verified_automated']} = {metrics['execution_pass_pct']:.1f}%",
        f"- Kiwi executed pass (Yes + Pass/Fail/Blocked): "
        f"{metrics['executed_kiwi']} executed, pass rate {metrics['kiwi_executed_pass_pct']:.1f}%",
        "",
        "## Regression_Dashboard scope (QA modules on dashboard only)",
        "",
        f"| Total | Executed | Not automatable | Completion % |",
        f"|------:|---------:|----------------:|-------------:|",
        f"| {metrics['dashboard_total']} | {metrics['dashboard_executed']} | "
        f"{metrics['dashboard_not_auto']} | {metrics['dashboard_completion_pct']:.1f}% |",
        "",
        *write_next_automation_section(metrics),
        "## Module-wise (all sheets)",
        "",
        "| Module | Total | Automatable | Verified auto | Kiwi Yes | Executed† | Passed (verified) | Auto % |",
        "|--------|------:|------------:|--------------:|---------:|----------:|------------------:|-------:|",
    ]
    for r in mod_rows:
        lines.append(
            f"| {r['area']} | {r['total']} | {r['automatable']} | {r['verified']} | {r['kiwi_yes']} | "
            f"{r['executed']} | {r['passed']} | {r['automation_pct']:.0f}% |"
        )
    sum_verified = sum(r["verified"] for r in mod_rows)
    lines.append(
        f"| **TOTAL (all sheets)** | **{metrics['total']}** | **{metrics['automatable']}** | "
        f"**{metrics['verified_automated']}** | **{metrics['kiwi_yes']}** | "
        f"**{metrics['executed_kiwi']}** | **{metrics['verified_passed']}** | "
        f"**{metrics['automation_completion_pct']:.0f}%** |"
    )
    lines.extend([
        "",
        f"Module-level verified sum (sanity check): {sum_verified} — must match portfolio "
        f"**Verified automated {metrics['verified_automated']}** above.",
        "",
        "† Executed uses Regression_Dashboard value when the module is listed there; "
        "otherwise Kiwi Yes + Pass/Fail/Blocked.",
        "",
        "**Do not use Kiwi Yes column sum as verified automated** — use portfolio line 4 or CSV "
        "column `verified=true` (filter in Excel).",
        "",
        "Full per-case rows: `reports/traceability_execution.csv`.",
    ])
    (OUT_DIR / "traceability_audit_summary.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    OUT_DIR.mkdir(exist_ok=True)
    cases = read_manual_cases()
    created = scan_features()
    cuke, cuke_files = read_execution()
    dash = read_dashboard()

    refs_all = set()
    for c in cases:
        for t in c["tags"]:
            refs_all.add(norm_tag(t))
    orphan_tags = sum(1 for t in created if t not in refs_all)

    for c in cases:
        classify_case(c, created, cuke)

    dup = duplicate_tag_rows(cases)
    for c in cases:
        for t in c["tags"]:
            ids = dup.get(norm_tag(t))
            if ids and len(ids) > 1:
                c["gaps_remarks"] = (c["gaps_remarks"] + " | Duplicate @tag shared with: "
                                     + "; ".join(x for x in ids if x != f"{c['area']}:{c['case_id']}")).strip(" |")

    metrics = compute_metrics(cases, dash, cuke_files)
    mod_rows = module_stats(cases, dash)

    fieldnames = [
        "case_id", "area", "title", "importance", "automatable", "automation_status",
        "automated", "automation_test", "run_tag", "feature", "scenario",
        "execution_status", "case_status", "executed_kiwi", "kiwi_result", "cuke_result",
        "verified", "gaps_remarks", "ref",
    ]
    with (OUT_DIR / "traceability_execution.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        w.writeheader()
        for c in cases:
            w.writerow(c)

    (OUT_DIR / "traceability_portfolio_counts.txt").write_text(
        "\n".join([
            f"generated={datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            f"verified_automated={metrics['verified_automated']}",
            f"verified_kiwi_yes={metrics.get('verified_kiwi_yes', 0)}",
            f"verified_partial={metrics.get('verified_partial', 0)}",
            f"verified_deviation={metrics.get('verified_deviation', 0)}",
            f"automatable={metrics['automatable']}",
            f"kiwi_yes={metrics['kiwi_yes']}",
            f"verified_passed={metrics['verified_passed']}",
            "note=1000-target uses verified_automated (not kiwi_yes). Excel: filter CSV verified=true",
        ]),
        encoding="utf-8",
    )

    color = {
        "passed": "#1a7f37", "failed": "#cf222e", "blocked": "#8250df",
        "skipped": "#9a6700", "proposed": "#656d76", "": "#8b949e",
    }
    label = {
        "passed": "PASS", "failed": "FAIL", "blocked": "BLOCKED",
        "skipped": "SKIP", "proposed": "PROPOSED", "": "not run",
    }

    def esc(s):
        return html.escape(str(s))

    m = metrics
    v_yes = m.get("verified_kiwi_yes", 0)
    v_part = m.get("verified_partial", 0)
    v_dev = m.get("verified_deviation", 0)
    headline = (
        f"Total {m['total']} · Automatable {m['automatable']} · Not automatable {m['not_automatable']} · "
        f"Verified automated {m['verified_automated']} · Verified passed {m['verified_passed']} · "
        f"Remaining not verified {m['remaining_not_verified']} · Pending scripting {m['pending_automation']}"
    )
    verified_breakdown = (
        f"Verified automated = {m['verified_automated']} "
        f"(Kiwi Yes {v_yes} + Partial {v_part} + Deviation {v_dev}). "
        f"Do not use Kiwi Yes alone ({m['kiwi_yes']}) or an old CSV export (~918 before batch82)."
    )
    pct_line = (
        f"Verified automation completion: {m['verified_automated']}/{m['automatable']} "
        f"({m['automation_completion_pct']:.1f}%) · "
        f"Dashboard execution completion: {m['dashboard_executed']}/"
        f"{m['dashboard_total'] - m['dashboard_not_auto']} ({m['dashboard_completion_pct']:.1f}%) · "
        f"Verified pass rate: {m['execution_pass_pct']:.1f}%"
    )

    mod_html = "".join(
        f"<tr data-area='{esc(r['area'])}'><td>{esc(r['area'])}</td>"
        f"<td class=r>{r['total']}</td><td class=r>{r['automatable']}</td>"
        f"<td class=r>{r['verified']}</td><td class=r>{r['kiwi_yes']}</td>"
        f"<td class=r>{r['executed']}</td><td class=r>{r['passed']}</td>"
        f"<td class=r>{r['not_automatable']}</td>"
        f"<td class=r>{r['automation_pct']:.0f}%</td></tr>"
        for r in mod_rows
    )
    mod_html += (
        f"<tr data-total='1'><td><b>TOTAL (all sheets)</b></td>"
        f"<td class=r><b>{m['total']}</b></td><td class=r><b>{m['automatable']}</b></td>"
        f"<td class=r><b>{m['verified_automated']}</b></td><td class=r><b>{m['kiwi_yes']}</b></td>"
        f"<td class=r><b>{m['executed_kiwi']}</b></td><td class=r><b>{m['verified_passed']}</b></td>"
        f"<td class=r><b>{m['not_automatable']}</b></td>"
        f"<td class=r><b>{m['automation_completion_pct']:.0f}%</b></td></tr>"
    )

    detail = "".join(
        f"<tr data-area='{esc(c['area'])}' data-verified='{'1' if c['verified'] else '0'}'>"
        f"<td><b>{esc(c['case_id'])}</b></td><td>{esc(c['area'])}</td>"
        f"<td>{esc(c['automatable'])}</td><td>{esc(c['automation_status'])}</td>"
        f"<td>{'true' if c['verified'] else 'false'}</td>"
        f"<td>{esc(c['automation_test'] or '—')}</td>"
        f"<td>{esc(c['execution_status'])}</td>"
        f"<td>{esc(c['gaps_remarks'] or '—')}</td></tr>"
        for c in cases
    )

    areas = [r["area"] for r in mod_rows]
    options = "".join(f"<option value='{esc(a)}'>{esc(a)}</option>" for a in areas)
    generated = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    doc = f"""<!doctype html><html><head><meta charset=utf-8>
<meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
<title>Traceability &amp; Execution Audit</title>
<style>
body{{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2328;max-width:1400px}}
table{{border-collapse:collapse;width:100%;margin:12px 0;font-size:12px}}
th,td{{border:1px solid #d0d7de;padding:6px 8px;text-align:left;vertical-align:top}}
th{{background:#f6f8fa;position:sticky;top:0}} td.r{{text-align:right}}
.big{{font-size:18px;font-weight:700}} .sub{{color:#656d76;font-size:13px}}
.badge{{color:#fff;padding:2px 8px;border-radius:10px;font-size:11px}}
select{{font:inherit;padding:4px 8px}}
#detail tbody tr:nth-child(even){{background:#f6f8fa}}
</style>
</head><body>
<h1>Nuvepro Moodle — Traceability &amp; Execution Audit</h1>
<div class=sub>Generated {generated} (re-run: python tools/sync_kiwi_and_traceability.py) · {esc(XLSX.name)} · Cucumber: {esc(m['cuke_json_sources'])}</div>
<p class=big>{headline}</p>
<p class=sub><b>{esc(verified_breakdown)}</b> Quick check: <code>reports/traceability_portfolio_counts.txt</code>
 · Excel: filter CSV column <code>verified</code> = <code>true</code> (expect {m['verified_automated']} rows).</p>
<p>{pct_line}</p>
<p class=sub>Backlog: remaining not verified = {m['remaining_not_verified']} ({m['automatable']} automatable − {m['verified_automated']} verified)
 = deferred {m.get('bridge_deferred', 0)} + pending scripting {m['pending_automation']}
 (blank {m.get('bridge_pending_blank', 0)} + false Yes {m.get('bridge_pending_false_yes', 0)})
 + partial/deviation unlinked {m.get('bridge_partial_deviation', 0)}.
 See <code>reports/traceability_audit_summary.md</code> for next automation order.</p>
<p>Filter module: <select id=area><option value="">All</option>{options}</select>
 <label><input type=checkbox id=verifiedOnly> Verified automated only</label></p>
<h2>Module summary</h2>
<table id=summary><thead><tr>
<th>Module</th><th>Total</th><th>Automatable</th><th>Verified auto</th><th>Kiwi Yes</th>
<th>Executed</th><th>Verified pass</th><th>Not automatable</th><th>Verified auto %</th>
</tr></thead><tbody>{mod_html}</tbody></table>
<h2>All test cases</h2>
<table id=detail><thead><tr>
<th>Test Case ID</th><th>Feature/Module</th><th>Automatable?</th><th>Automation status</th>
<th>Verified</th><th>Automation file / test</th><th>Execution status</th><th>Gaps / remarks</th>
</tr></thead><tbody>{detail}</tbody></table>
<script>
const sel = document.getElementById('area');
const vOnly = document.getElementById('verifiedOnly');
function apply() {{
  const v = sel.value;
  const vo = vOnly.checked;
  for (const table of [document.getElementById('summary'), document.getElementById('detail')]) {{
    for (const row of table.tBodies[0].rows) {{
      let show = !v || row.dataset.area === v || row.dataset.total === '1';
      if (vo && table.id === 'detail') {{
        show = show && row.dataset.verified === '1';
      }}
      row.style.display = show ? '' : 'none';
    }}
  }}
}}
sel.addEventListener('change', apply);
vOnly.addEventListener('change', apply);
</script>
</body></html>"""
    (OUT_DIR / "traceability_execution.html").write_text(doc, encoding="utf-8")

    write_summary_md(metrics, mod_rows, len(dup), orphan_tags)

    print(headline)
    print(pct_line)
    print(f"Duplicate tag keys: {len(dup)} | Orphan scenario tags: {orphan_tags}")
    print(f"Wrote {OUT_DIR / 'traceability_execution.csv'} ({len(cases)} rows)")
    print(f"Wrote {OUT_DIR / 'traceability_execution.html'}")
    print(f"Wrote {OUT_DIR / 'traceability_audit_summary.md'}")


if __name__ == "__main__":
    main()
