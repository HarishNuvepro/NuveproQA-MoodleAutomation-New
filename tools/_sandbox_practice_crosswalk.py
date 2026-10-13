"""Crosswalk Moodle_Sandbox_Labs (Yes) vs Practice_Project automation in Kiwi."""
from __future__ import annotations

import re
from pathlib import Path

import openpyxl

WORKBOOK = Path("docs/MoodleRegression_Testing_Kiwi new.xlsx")


def load_rows(sheet: str) -> list[dict]:
    wb = openpyxl.load_workbook(WORKBOOK, read_only=True, data_only=True)
    sh = wb[sheet]
    rows = []
    for i, row in enumerate(sh.iter_rows(min_row=2, values_only=True), start=2):
        sno = str(row[0] or "").strip()
        if not sno:
            continue
        title = str(row[1] or "")
        summary = str(row[2] or "") if len(row) > 2 else ""
        text = f"{title} {summary}".lower()
        rows.append(
            {
                "row": i,
                "sno": sno,
                "title": title[:100],
                "text": text,
                "auto": str(row[10] or "").strip().lower() if len(row) > 10 else "",
                "ref": str(row[11] or "") if len(row) > 11 else "",
            }
        )
    return rows


def pg_tag(ref: str) -> str:
    m = re.search(r"@PG-(\d+)", ref or "")
    return f"PG-{m.group(1)}" if m else ""


def pp_tag(ref: str) -> str:
    m = re.search(r"@PP-(\d+)|@CGAP-PGG-(\d+)|@CGAP-PP-(\d+)", ref or "")
    if not m:
        return ""
    for g in m.groups():
        if g:
            if "CGAP-PGG" in ref:
                return f"CGAP-PGG-{g}"
            if "CGAP-PP" in ref:
                return f"CGAP-PP-{g}"
            return f"PP-{g}"
    return ""


THEME_KEYWORDS = {
    "manage_labs": ["manage lab", "no labs", "active lab", "managelabs", "create from manage"],
    "lab_actions": ["start lab", "stop lab", "delete lab", "sync", "lab action", "lifecycle"],
    "console_ui": ["console", "control panel", "jump to console", "credentials", "usage", "instruction", "notes"],
    "landing": ["landing", "start sandbox", "continue lab", "sandbox"],
    "create_activity": ["create activity", "add activity", "modedit", "playground activity"],
    "duration_expiry": ["duration", "remaining", "expired", "extend", "expiry"],
    "search_export": ["search", "export", "download", "filter"],
    "roles": ["student", "teacher", "manager", "role", "permission"],
    "template_type": ["vm", "composition", "aws", "gcp", "csp", "catalog", "account template"],
    "bulk": ["bulk", "multiple user", "pagination"],
    "speedtest": ["speed test", "speedtest"],
    "negative": ["invalid", "error", "blocked", "denied", "negative"],
}


def themes(text: str) -> set[str]:
    hit = {k for k, kws in THEME_KEYWORDS.items() if any(kw in text for kw in kws)}
    return hit or {"general"}


def token_set(text: str) -> set[str]:
    return set(re.findall(r"[a-z]{5,}", text))


def score_match(sb: dict, pp: dict) -> int:
    tb = themes(sb["text"])
    pb = themes(pp["text"])
    theme_overlap = len(tb & pb)
    tok = len(token_set(sb["text"]) & token_set(pp["text"]))
    # Practice-specific-only themes reduce false "covered"
    if "guided" in pp["text"] or "practice project" in pp["text"] or "checkpoint" in pp["text"]:
        if "manage_labs" in tb and "manage_labs" not in pb:
            theme_overlap = max(0, theme_overlap - 1)
    return tok + theme_overlap * 4


def main() -> None:
    sb = load_rows("Moodle_Sandbox_Labs")
    pp = load_rows("Practice_Project")
    sb_yes = [r for r in sb if r["auto"] == "yes"]
    pp_verified = [r for r in pp if r["auto"] in ("yes", "partial", "deviation")]

    # Refs that explicitly cite sandbox features
    pp_cites_sandbox = [r for r in pp if "sandbox" in r["ref"].lower() or "@pg-" in r["ref"].lower()]

    print(f"Sandbox total={len(sb)} Yes={len(sb_yes)}")
    print(f"Practice total={len(pp)} Yes/Partial/Deviation={len(pp_verified)}")
    print(f"Practice rows citing sandbox/PG in ref: {len(pp_cites_sandbox)}")
    print()

    covered_yes = []
    covered_partial = []
    not_covered = []

    for s in sb_yes:
        best_pp = None
        best = 0
        for p in pp_verified:
            sc = score_match(s, p)
            if sc > best:
                best = sc
                best_pp = p
        entry = {
            "sb_row": s["row"],
            "pg": pg_tag(s["ref"]) or s["sno"],
            "sb_title": s["title"],
            "sb_ref": s["ref"][:70],
            "best_pp": pp_tag(best_pp["ref"]) if best_pp else "",
            "pp_auto": best_pp["auto"] if best_pp else "",
            "pp_title": (best_pp["title"][:60] if best_pp else ""),
            "score": best,
        }
        if best_pp and best >= 8 and best_pp["auto"] == "yes":
            covered_yes.append(entry)
        elif best_pp and best >= 5:
            covered_partial.append(entry)
        else:
            not_covered.append(entry)

    print("=== Interpretation (heuristic — not 1:1 Kiwi row mapping) ===")
    print(f"Sandbox Yes with strong PP Yes analogue: {len(covered_yes)}")
    print(f"Sandbox Yes with only Partial/Deviation/weak PP analogue: {len(covered_partial)}")
    print(f"Sandbox Yes with NO meaningful PP analogue in sheet: {len(not_covered)}")
    print()

    print("--- A) Sandbox @PG Yes — NO strong Practice equivalent (automation gap for PP) ---")
    for e in not_covered:
        print(f"  row {e['sb_row']:3d} {e['pg']:8s}  {e['sb_title'][:55]}")
        if e["best_pp"]:
            print(f"           nearest: {e['best_pp']} ({e['pp_auto']}, score={e['score']}) {e['pp_title']}")
    print()

    print("--- B) Sandbox Yes — PP covered only as Partial/Deviation/weak (needs PP hardening) ---")
    for e in sorted(covered_partial, key=lambda x: -x["score"])[:30]:
        print(f"  row {e['sb_row']:3d} {e['pg']:8s} -> {e['best_pp']} ({e['pp_auto']}, score={e['score']})")
    print()

    # Reverse: PP Yes that look sandbox-like but sandbox might not have
    sb_verified = [r for r in sb if r["auto"] == "yes"]
    pp_yes = [r for r in pp if r["auto"] == "yes"]
    pp_only = []
    for p in pp_yes:
        if "practice project" in p["text"] or "guided" in p["text"] or "checkpoint" in p["text"]:
            continue
        best = max((score_match(s, p), s) for s in sb_verified)
        if best[0] < 5:
            pp_only.append((p["row"], pp_tag(p["ref"]) or p["sno"], p["title"][:50], best[0]))
    print(f"--- C) PP Yes that are NOT sandbox-like (PP-specific), sample count: {len(pp_only)} ---")
    for row, tag, title, sc in pp_only[:15]:
        print(f"  PP row {row} {tag}: {title}")


if __name__ == "__main__":
    main()
