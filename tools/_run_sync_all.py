"""One-shot: gaps + coverage rows + dashboard + traceability."""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
scripts = [
    "add_assessment_sandbox_parity_gaps.py",
    "update_assessment_sandbox_parity_coverage.py",
    "update_predelivery_coverage.py",
    "sync_kiwi_and_traceability.py",
]
for name in scripts:
    p = ROOT / "tools" / name
    print("\n===", name, "===")
    r = subprocess.run([sys.executable, str(p)], cwd=ROOT)
    if r.returncode != 0:
        sys.exit(r.returncode)
print("\nAll sync steps completed.")
