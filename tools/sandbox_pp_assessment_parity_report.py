"""Report Sandbox↔Practice parity scenarios vs Assessment (CGAP-PP-* / CGAP-A-SB-*)."""
from __future__ import annotations

import re
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
WORKBOOK = ROOT / "docs/MoodleRegression_Testing_Kiwi new.xlsx"
PP_FEAT = ROOT / "src/test/resources/features/practice_project_sandbox_parity.feature"
AS_FEAT = ROOT / "src/test/resources/features/assessment_sandbox_parity.feature"

# PP parity gap id -> Assessment parity id (same sandbox mechanic)
PAIR = {
    7: 7, 8: 8, 9: 9, 10: 10, 11: 11, 13: 13, 14: 14, 15: 15, 16: 16,
    17: 17, 18: 18, 19: 19, 20: 20, 21: 21, 23: 23,
    # Assessment-only extensions for SB-user / PP-user overlap outside parity file numbers:
    26: 26, 27: 27, 28: 28,
}
DEFERRED_BOTH = {12, 22, 24, 25}


def tags_in(path: Path, prefix: str) -> set[int]:
    text = path.read_text(encoding="utf-8")
    return {int(m.group(1)) for m in re.finditer(rf"@{prefix}-(\d+)", text)}


def kiwi_asmt() -> dict[str, tuple[str, str]]:
    wb = openpyxl.load_workbook(WORKBOOK, read_only=True, data_only=True)
    sh = wb["Moodle_Assessment_Labs"]
    out: dict[str, tuple[str, str]] = {}
    for row in sh.iter_rows(min_row=2, values_only=True):
        sno = str(row[0] or "").strip()
        if not sno.startswith("CGAP-A-SB-"):
            continue
        out[sno] = (str(row[10] or "").strip(), str(row[11] or "")[:80])
    return out


def main() -> None:
    pp = tags_in(PP_FEAT, "CGAP-PP")
    asmt = tags_in(AS_FEAT, "CGAP-A-SB")
    kiwi = kiwi_asmt()

    print("=== Practice Project sandbox parity feature ===")
    print(f"  Scenarios: {sorted(pp)}")
    print(f"  Deferred (no feature): {sorted(DEFERRED_BOTH - pp)}")
    print()
    print("=== Assessment sandbox parity feature ===")
    print(f"  Scenarios: {sorted(asmt)}")
    print(f"  Extra vs PP file: {sorted(asmt - pp)}")
    print()

    missing_asmt = []
    for n in sorted(pp):
        a = PAIR.get(n, n)
        if a not in asmt:
            missing_asmt.append((n, a))
    if missing_asmt:
        print("MISSING Assessment scenarios for PP parity numbers:")
        for p, a in missing_asmt:
            print(f"  CGAP-PP-{p} -> CGAP-A-SB-{a}")
    else:
        print("OK: every PP parity scenario has a matching CGAP-A-SB-* in assessment_sandbox_parity.feature")
    print()

    print("=== Kiwi Moodle_Assessment_Labs (CGAP-A-SB-*) ===")
    for sno in sorted(kiwi, key=lambda s: int(s.split("-")[-1])):
        auto, ref = kiwi[sno]
        print(f"  {sno:14s}  {auto:10s}  {ref}")
    print()
    print("Run: .\\mvnw.cmd test -Pslow \"-Dcucumber.features=src/test/resources/features/assessment_sandbox_parity.feature\"")


if __name__ == "__main__":
    main()
