"""Phase 0 hygiene: Lab Templates false-Yes reconciliation + dashboard refresh."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(script: str) -> None:
    cmd = [sys.executable, str(ROOT / "tools" / script)]
    print(">>>", " ".join(cmd))
    subprocess.check_call(cmd, cwd=ROOT)


def main() -> None:
    # Verified refs + Pass on rows that have green @LT-* scenarios (does not claim missing tags).
    run("update_lab_templates_coverage.py")
    # Downgrade Kiwi Yes rows whose @LT-* has no Cucumber scenario (fixes false-Yes traceability).
    run("reconcile_lab_templates_refs.py")
    run("fix_moodle_tags_refs.py")
    run("sync_kiwi_and_traceability.py")
    print("Phase 0 hygiene complete (do not re-run update_lab_templates_coverage after reconcile).")


if __name__ == "__main__":
    main()
