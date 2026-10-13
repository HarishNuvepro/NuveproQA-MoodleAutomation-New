"""Classify Pre_Delivery_Lab_Checklist rows: automatable vs not (2026-09-22)."""
from __future__ import annotations

from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")
SHEET = "Pre_Delivery_Lab_Checklist"

# Row title/summary keyword buckets (first match wins).
NOT_AUTO = [
    ("2476", "performance / scale soak — manual or dedicated perf env"),
    ("screen sizes", "responsive layout — manual visual"),
    ("multiple team members", "concurrent editors — manual coordination"),
    ("auto-popul", "CloudLabs/backend autopopulate — needs live integration assert"),
    ("Plan ID, Team ID", "backend autopopulate — not UI-only"),
    ("subsequent scroll batches", "infinite scroll dedupe — heavy seed data"),
]

DEFER_SAVE = [
    ("Audit Log", "needs successful save + DB/log read"),
    ("automatically becomes visible", "needs save at 100%"),
    ("remains hidden", "needs save state"),
    ("re-uploading a screenshot", "file upload + persistence"),
    ("Reference Screenshot is mandatory for the first submission", "file upload on first save"),
    ("forcefully enable", "needs admin flag + specific DB state"),
    ("backend-warning", "needs prior save at 100% then config change"),
]

DASHBOARD = [
    ("dashboard", "readiness dashboard — batch 3 (needs dashboard URL + PO)"),
    ("Dashboard", "readiness dashboard — batch 3"),
    ("Total Labs card", "dashboard summary cards"),
    ("Checklist Submitted", "dashboard"),
    ("Ready Labs", "dashboard"),
    ("Courses dropdown", "dashboard filter"),
    ("Apply", "dashboard filter apply"),
    ("Open button", "dashboard table"),
]

AUTO_CHECKLIST = [
    ("Lab Readiness", "checklist / capability"),
    ("checklist completion", "progress bar"),
    ("Support Engineer", "validation"),
    ("status indicator turns green", "progress bar colour"),
    ("environment type", "envtype toggle"),
    ("Catalog", "section / items"),
    ("Orgs", "section"),
    ("Moodle", "section"),
    ("students do not see", "role / nav"),
    ("checkboxes", "checklist toggle"),
    ("mandatory", "validation alert"),
    ("screenshot", "validation alert (no upload)"),
    ("progress bar", "CGAP colour bands"),
    ("CGAP-PDL", "derived gap — authored"),
]


def classify(title: str, summary: str) -> tuple[str, str]:
    text = f"{title} {summary}".lower()
    for needle, reason in NOT_AUTO:
        if needle.lower() in text:
            return "No", reason
    for needle, reason in DEFER_SAVE:
        if needle.lower() in text:
            return "Deferred", reason
    for needle, reason in DASHBOARD:
        if needle.lower() in text:
            return "Partial", reason
    for needle, reason in AUTO_CHECKLIST:
        if needle.lower() in text:
            return "Yes", reason
    return "Partial", "needs case-by-case PO / data (default Partial)"


def main() -> None:
    wb = openpyxl.load_workbook(WORKBOOK, read_only=True, data_only=True)
    sh = wb[SHEET]
    from collections import Counter

    counts = Counter()
    rows = []
    for i, r in enumerate(sh.iter_rows(min_row=2, values_only=True), start=2):
        if not r[0]:
            continue
        title = str(r[1] or "")
        summary = str(r[2] or "") if len(r) > 2 else ""
        auto = str(r[10] or "").strip()
        verdict, reason = classify(title, summary)
        counts[verdict] += 1
        rows.append((i, str(r[0])[:30], auto, verdict, title[:55]))
    total = sum(counts.values())
    print(f"{SHEET}: {total} cases\n")
    print("Recommended automation potential (mechanical classification):")
    for k in ("Yes", "Partial", "Deferred", "No"):
        print(f"  {k:8s} {counts[k]:3d}  ({100 * counts[k] / total:.0f}%)")
    automatable = counts["Yes"] + counts["Partial"] + counts["Deferred"]
    print(f"\nAutomatable (Yes+Partial+Deferred): {automatable}/{total} ({100 * automatable / total:.0f}%)")
    print(f"Not automatable (No): {counts['No']}/{total}")
    print("\nCurrently Kiwi Automated=Yes:", sum(1 for r in rows if r[2] == "Yes"))


if __name__ == "__main__":
    main()
