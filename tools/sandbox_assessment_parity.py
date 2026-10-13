"""Sandbox (53 Yes) vs Assessment — tag-level parity (assessment_sandbox_parity.feature)."""
from __future__ import annotations

import re
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")

# sandbox primary tag -> (coverage on Assessment, automation ref, note)
PARITY: dict[str, tuple[str, str, str]] = {
    "SB-manage-1": ("Partial", "@CGAP-A-SB-17", "Assessment Manage Labs Sync/Stop after student start"),
    "SB-manage-3": ("Partial", "@CGAP-A-SB-18", "Create from No labs tab on assessment"),
    "SB-manage-8": ("Partial", "@CGAP-A-SB-19", "Delete + sync on assessment Manage Labs"),
    "SB-manage-9": ("Partial", "@CGAP-A-SB-20", "Stop then Start again on assessment"),
    "SB-manage-12": ("Deferred", "CGAP-A-SB-22", "Bulk Create — deferred on assessment"),
    "SB-manage-15": ("Partial", "@CGAP-A-SB-21", "Action gates on assessment Manage Labs"),
    "SB-manage-23": ("Partial", "@CGAP-A-SB-23", "Active/No labs chrome on assessment"),
    "SB-manage-26": ("Partial", "@CGAP-A-SB-15", "Cancel Start Assessment — no lab"),
    "SB-manage-27": ("Partial", "@CGAP-A-SB-7", "Lab Usage / cost on assessment console"),
    "SB-manage-29": ("Partial", "@CGAP-A-SB-13", "Password change on assessment console"),
    "SB-manage-31": ("Partial", "@CGAP-A-SB-9", "Refresh lab on assessment console"),
    "SB-manage-34": ("Deferred", "CGAP-A-SB-25", "Invalid template — deferred"),
    "SB-user-1": ("Partial", "@CGAP-A-SB-14", "Access Lab / Jump to Console on assessment"),
    "SB-user-6": ("Partial", "@CGAP-A-SB-8", "Lab notes on assessment console"),
    "SB-user-8": ("Deferred", "CGAP-A-SB-12", "Export PDF — deferred"),
    "SB-user-12": ("Partial", "@CGAP-A-SB-10", "Support mailto on assessment console"),
    "SB-user-13": ("Partial", "@CGAP-A-SB-11", "Console Back on assessment"),
    "SB-user-18": ("Partial", "@CGAP-A-SB-16", "Duration vs CloudLabs on assessment"),
    "SB-user-19": ("Deferred", "CGAP-A-SB-24", "Catalog/unlimited — deferred"),
}


def resolve_tag(ref: str) -> str:
    ref = ref or ""
    m = re.search(r"@(SB-[a-z0-9-]+)", ref)
    if m:
        return m.group(1)
    m = re.search(r"Covered by (@SB-[a-z0-9-]+)", ref)
    if m:
        return m.group(1).lstrip("@")
    return ""


def main() -> None:
    wb = openpyxl.load_workbook(WORKBOOK, read_only=True, data_only=True)
    sh = wb["Moodle_Sandbox_Labs"]
    by_cov = {"Yes": 0, "Partial": 0, "No": 0, "Deferred": 0, "Unknown": 0}
    for row in sh.iter_rows(min_row=2, values_only=True):
        if not row[0] or str(row[10] or "").strip() != "Yes":
            continue
        tag = resolve_tag(str(row[11] or ""))
        cov = PARITY.get(tag, ("No", "—", "not in assessment parity scope"))[0]
        by_cov[cov] = by_cov.get(cov, 0) + 1
    total = sum(by_cov.values())
    print(f"Sandbox Yes rows mapped to Assessment parity: {total}\n")
    for k in ("Partial", "Deferred", "No", "Unknown"):
        print(f"  {k:10s} {by_cov.get(k, 0)}")
    print("\nRun assessment parity: mvnw test -Pslow -Dcucumber.features=assessment_sandbox_parity.feature")


if __name__ == "__main__":
    main()
