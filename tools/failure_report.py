"""
Failed + skipped scenario report for the Java/Cucumber suite.

Turns target/cucumber.json into ONE scannable list of everything that did NOT pass, with the reason
and a pointer to the on-disk diagnostics — so a failure can be triaged without re-running the suite.

For each failed/skipped scenario it reports:
  - the @tag (manual case id) + scenario name
  - status (FAILED / SKIPPED / UNDEFINED / PENDING / AMBIGUOUS)
  - the failing/skipped step and its reason (SkipException message, assertion message, or stack head)
  - the artifact folder written by FailureCapture (target/failures/<scenario>/: screenshot.png,
    page.html, console.log, trace.zip) when it exists

Run (after a `mvn test` has produced target/cucumber.json):
    python tools/failure_report.py
Outputs (in reports/):
    failures.md
    failures.csv
"""
from __future__ import annotations
import csv
import json
import re
import sys
from pathlib import Path

# Windows consoles default to cp1252 and choke on non-latin chars in skip/assert messages.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "target"
FAILURES_DIR = TARGET / "failures"
REPORTS = ROOT / "reports"
# Merge every saved run so the report covers fast + slow executions, newest wins per scenario.
CUKE_JSONS = ["cucumber.json", "cucumber_fast.json", "cucumber_slow.json"]

NON_PASS = {"failed", "skipped", "undefined", "pending", "ambiguous"}


def sanitize(name: str) -> str:
    """Mirror of FailureCapture.sanitize so we can locate the artifact folder."""
    base = re.sub(r"[^a-zA-Z0-9._-]+", "_", name or "scenario")
    return base[:80]


def first_line(text: str) -> str:
    if not text:
        return ""
    return text.strip().splitlines()[0][:300]


def scenario_status(steps: list, hooks: list) -> tuple[str, str, str]:
    """Return (status, failing_step, reason) for a scenario from its steps + before/after hooks."""
    results = []
    for st in steps:
        res = st.get("result", {}) or {}
        label = (st.get("keyword", "") + st.get("name", "")).strip()
        results.append((res.get("status", "passed"), label, res.get("error_message", "")))
    for hk in hooks:
        res = hk.get("result", {}) or {}
        results.append((res.get("status", "passed"), "(hook)", res.get("error_message", "")))

    # A failure dominates; otherwise report the first non-pass (skip/undefined/pending).
    for status, label, msg in results:
        if status == "failed":
            return "FAILED", label, first_line(msg)
    for status, label, msg in results:
        if status in NON_PASS:
            return status.upper(), label, first_line(msg)
    return "PASSED", "", ""


def load_scenarios():
    seen = {}
    for fname in CUKE_JSONS:
        path = TARGET / fname
        if not path.exists():
            continue
        try:
            features = json.loads(path.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"  ! could not parse {path.name}: {e}")
            continue
        for feat in features:
            for el in feat.get("elements", []):
                if el.get("type") not in (None, "scenario"):
                    continue
                name = el.get("name", "(unnamed)")
                tags = [t.get("name", "") for t in el.get("tags", [])]
                status, step, reason = scenario_status(
                    el.get("steps", []), el.get("before", []) + el.get("after", []))
                seen[name] = {  # last run wins
                    "feature": feat.get("name", ""),
                    "name": name,
                    "tags": tags,
                    "status": status,
                    "step": step,
                    "reason": reason,
                }
    return list(seen.values())


def case_tag(tags: list) -> str:
    """The most specific tag = the manual case id (first non-group tag), else the first tag."""
    group = {"@settings", "@sac", "@assesscfg", "@assessgaps", "@assessadmin", "@provisioning",
             "@banner", "@coursedep", "@augmented", "@sacgrade", "@assesslab", "@assesscomplete",
             "@assessdeviation", "@assesspresence", "@labactions", "@freshlab"}
    specific = [t for t in tags if t.lower() not in group]
    return (specific or tags or ["(untagged)"])[0]


def artifact_dir(name: str) -> str:
    folder = FAILURES_DIR / sanitize(name)
    if folder.exists():
        rel = folder.relative_to(ROOT).as_posix()
        return rel
    return ""


def main():
    scenarios = load_scenarios()
    if not scenarios:
        print("No target/cucumber.json found — run `mvn test` first.")
        return
    bad = [s for s in scenarios if s["status"] != "PASSED"]
    bad.sort(key=lambda s: (s["status"] != "FAILED", s["status"], case_tag(s["tags"])))

    REPORTS.mkdir(exist_ok=True)
    passed = sum(1 for s in scenarios if s["status"] == "PASSED")
    failed = sum(1 for s in bad if s["status"] == "FAILED")
    skipped = len(bad) - failed

    # ---- Markdown ----
    md = [f"# Failed & skipped scenarios",
          "",
          f"**{len(scenarios)}** scenarios — {passed} passed, **{failed} failed**, {skipped} skipped/other.",
          ""]
    if not bad:
        md.append("All executed scenarios passed. 🎉")
    else:
        md.append("| Case | Scenario | Status | Step | Reason | Artifacts |")
        md.append("|------|----------|--------|------|--------|-----------|")
        for s in bad:
            art = artifact_dir(s["name"])
            art_cell = f"`{art}`" if art else "—"
            reason = (s["reason"] or "").replace("|", "\\|")
            step = (s["step"] or "").replace("|", "\\|")
            md.append(f"| {case_tag(s['tags'])} | {s['name'][:70]} | {s['status']} "
                      f"| {step[:50]} | {reason[:120]} | {art_cell} |")
    (REPORTS / "failures.md").write_text("\n".join(md) + "\n", encoding="utf-8")

    # ---- CSV ----
    with (REPORTS / "failures.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["case_tag", "scenario", "feature", "status", "step", "reason", "artifacts"])
        for s in bad:
            w.writerow([case_tag(s["tags"]), s["name"], s["feature"], s["status"],
                        s["step"], s["reason"], artifact_dir(s["name"])])

    print(f"Scenarios: {len(scenarios)}  passed={passed}  failed={failed}  skipped/other={skipped}")
    for s in bad:
        art = artifact_dir(s["name"])
        print(f"  [{s['status']:8}] {case_tag(s['tags']):14} {s['name'][:55]}")
        if s["reason"]:
            print(f"             reason: {s['reason'][:110]}")
        if art:
            print(f"             logs:   {art}")
    print(f"\nWrote reports/failures.md and reports/failures.csv")
    if bad:
        print("Re-run failed/skipped by module: rerun generate all   (or: python tools/generate_module_rerun.py --all)")


if __name__ == "__main__":
    main()
