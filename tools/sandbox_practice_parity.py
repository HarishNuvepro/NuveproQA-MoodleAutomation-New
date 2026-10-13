"""Sandbox (53 Yes) vs Practice Project — tag-level parity from authored automation."""
from __future__ import annotations

import re
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")

# Primary sandbox scenario tag -> (coverage vs Practice, PP automation refs, note)
PARITY: dict[str, tuple[str, str, str]] = {
    "SB-create-1": (
        "Partial",
        "@PP-2 @PP-15 @CGAP-PP-1",
        "Practice create form; not sandbox PDF/parallel/help toggles or admin vs student activity name",
    ),
    "SB-create-9": (
        "Partial",
        "@CGAP-PGG-14",
        "Student cannot open admin pages; not the same as student blocked from modedit add Lab",
    ),
    "SB-manage-1": ("No", "—", "Manage Labs admin Sync/Stop after student Start — no PP feature"),
    "SB-manage-3": ("No", "—", "Admin Create from No labs tab — sandbox Manage Labs only"),
    "SB-manage-8": ("No", "—", "Delete confirmation, student message, create-after-delete — sandbox only"),
    "SB-manage-9": ("No", "—", "Restart lab after Stop from Manage Labs — sandbox only"),
    "SB-manage-12": ("No", "—", "Bulk Create on Manage Labs — sandbox only"),
    "SB-manage-15": ("No", "—", "Manage Labs search/pagination/gates — sandbox only"),
    "SB-manage-20": ("No", "—", "Negative misconfigured lab Start — sandbox only"),
    "SB-manage-23": ("No", "—", "Manage Labs Active/No labs listing chrome — sandbox only"),
    "SB-manage-26": (
        "Partial",
        "@PP-139 @guidedlab",
        "Start confirmation cancel (sandbox); PP only asserts Stop/Submit confirm copy in guided_lab",
    ),
    "SB-manage-27": ("No", "—", "Lab Usage / cost popup on sandbox console — not in PP automation"),
    "SB-manage-29": (
        "Partial",
        "@PP-11 @guidedlab",
        "Sandbox password change on credentials popup; PP only checks credentials presence",
    ),
    "SB-manage-31": ("No", "—", "Refresh lab on sandbox console — not automated for PP"),
    "SB-manage-34": ("No", "—", "Invalid-template Create error on landing — not in PP"),
    "SB-user-1": (
        "Partial",
        "@PP-9 @PP-11 @guidedlab",
        "Console instructions/duration/credentials + Access Lab redirect; PP lifecycle omits cloud redirect",
    ),
    "SB-user-3": ("Partial", "@PP-11 @guidedlab", "Stop from console — covered in guided_lab (Kiwi Partial)"),
    "SB-user-4": ("Partial", "@PP-11 @guidedlab", "Continue/resume console — guided_lab continue step"),
    "SB-user-5": (
        "Partial",
        "@CGAP-PGG-13 @CGAP-PGG-14",
        "Student cannot manage activity; PP checks admin buttons / admin page, not full sandbox manage UI",
    ),
    "SB-user-6": ("No", "—", "Lab notes save/persist — sandbox/playground console; no PP scenario"),
    "SB-user-7": (
        "Partial",
        "@PP-5 @CGAP-PGG-20",
        "Landing details + Start only; PP lacks sandbox id/status/duration-on-desc-page checks",
    ),
    "SB-user-8": ("No", "—", "Export PDF on sandbox console — PP help/PDF flows differ (PP-13 still No)"),
    "SB-user-11": ("Partial", "@CGAP-PGG-20", "Read More modal — CGAP-PGG-20 asserts description/Read More"),
    "SB-user-12": ("No", "—", "Support tab link on sandbox console — not in PP"),
    "SB-user-13": ("No", "—", "Console Back / browser Back to landing — not in PP"),
    "SB-user-14": (
        "Partial",
        "@PP-14 (variant)",
        "VM iframe, speed test, fullscreen, new tab — PP parallel-view only; variants still Partial/Deferred in Kiwi",
    ),
    "SB-user-18": (
        "Partial",
        "@PP-11 @guidedlab",
        "Allotted/consumed/remaining vs CloudLabs — PP checks duration presence not API match",
    ),
    "SB-user-19": ("No", "—", "Catalog Proceed + unlimited + invalid Start/Create — sandbox catalog UI only"),
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
    rows = []
    for i, row in enumerate(sh.iter_rows(min_row=2, values_only=True), start=2):
        if not row[0]:
            continue
        if str(row[10] or "").strip() != "Yes":
            continue
        ref = str(row[11] or "")
        tag = resolve_tag(ref)
        cov, pp_ref, note = PARITY.get(tag, ("Unknown", "—", f"No parity entry for tag {tag}"))
        rows.append(
            {
                "row": i,
                "sno": str(row[0]),
                "summary": (str(row[1] or "")[:72]),
                "tag": tag,
                "cov": cov,
                "pp": pp_ref,
                "note": note,
            }
        )

    by_cov = {"Yes": [], "Partial": [], "No": [], "Unknown": []}
    for r in rows:
        by_cov.setdefault(r["cov"], []).append(r)

    print(f"Sandbox Automated=Yes: {len(rows)} Kiwi rows\n")
    print(f"  Full PP equivalent (Yes):     {len(by_cov.get('Yes', []))}")
    print(f"  Overlap / partial in PP:      {len(by_cov.get('Partial', []))}")
    print(f"  Not covered in PP automation: {len(by_cov.get('No', []))}")
    if by_cov.get("Unknown"):
        print(f"  Unmapped tags:                {len(by_cov['Unknown'])}")
    print()

    print("=== NOT covered in Practice Project (no matching scenario) ===")
    for r in by_cov.get("No", []):
        print(f"  row {r['row']:3d}  {r['tag']:14s}  {r['summary']}")
    print()

    print("=== Partial overlap only (sandbox Yes; PP has related but not same case) ===")
    for r in by_cov.get("Partial", []):
        print(f"  row {r['row']:3d}  {r['tag']:14s}  {r['pp']:22s}  {r['summary'][:50]}")
    print()

    # Tag rollup
    tags: dict[str, list] = {}
    for r in rows:
        tags.setdefault(r["tag"], []).append(r)
    print("=== By sandbox scenario tag (primary automation) ===")
    for tag in sorted(tags):
        cov = PARITY.get(tag, ("?", "?", "?"))[0]
        print(f"  {tag:16s}  {cov:7s}  ({len(tags[tag])} Kiwi rows)  {PARITY.get(tag, ('','',tag))[2][:60]}")


if __name__ == "__main__":
    main()
