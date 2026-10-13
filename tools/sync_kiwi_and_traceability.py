"""
Refresh Kiwi dashboard caches + regenerate traceability reports.

Run after ANY of:
  - Saving docs/MoodleRegression_Testing_Kiwi new.xlsx (manual or openpyxl)
  - Running tools/update_*_coverage.py (or other Kiwi writers)
  - Changing Cucumber @tags / Automated_Ref / case coverage in features or steps
  - A test run whose results should appear in the traceability matrix
    (target/cucumber.json and/or cucumber_fast.json / cucumber_slow.json)

Usage:
  python tools/sync_kiwi_and_traceability.py
  python tools/sync_kiwi_and_traceability.py --strict   # exit 1 on dashboard DRIFT
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(script: str, *extra: str) -> int:
    cmd = [sys.executable, str(ROOT / "tools" / script), *extra]
    print(f"\n>>> {' '.join(cmd)}")
    return subprocess.run(cmd, cwd=ROOT).returncode


def main() -> None:
    strict = "--strict" in sys.argv
    codes: list[int] = []

    codes.append(run("refresh_kiwi_dashboard.py"))
    codes.append(run("traceability.py"))

    drift = run("check_kiwi_dashboards.py")
    if drift != 0:
        print(
            "\n[sync] Dashboard DRIFT — Regression_Dashboard / Automation_Coverage "
            "do not match case rows. Report to QA or fix typed dashboard values."
        )
        if strict:
            codes.append(drift)

    failed = [c for c in codes if c != 0]
    if failed:
        print(f"\n[sync] finished with error(s): {failed}")
        sys.exit(max(failed))
    print("\n[sync] ok — Kiwi dashboards cached; traceability CSV/HTML/summary updated.")


if __name__ == "__main__":
    main()
