"""
Build re-run commands for failed/skipped scenarios after a module execution.

Reads target/cucumber.json by default (latest Maven run). Writes under reports/rerun/:
  <module_id>_rerun.json   — structured list
  <module_id>_rerun.tags   — Cucumber tag expression only
  <module_id>_rerun.cmd    — serial re-run (stable; recommended for flakes)
  <module_id>_rerun_parallel.cmd — same tags, -Pparallel
  index.md                 — all modules summary

Usage:
  python tools/generate_module_rerun.py --module users
  python tools/generate_module_rerun.py --all
  python tools/generate_module_rerun.py --module sandbox --source cucumber.json

After a parallel module run:
  rerun generate sandbox
  rerun run sandbox
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "tools") not in sys.path:
    sys.path.insert(0, str(ROOT / "tools"))

from failure_report import NON_PASS, artifact_dir, case_tag, scenario_status  # noqa: E402
from module_rerun_registry import ModuleRerunSpec, resolve_module, unique_specs  # noqa: E402
from traceability import META_TAGS, load_cuke_json, tags_in  # noqa: E402

TARGET = ROOT / "target"
RERUN_DIR = ROOT / "reports" / "rerun"
DEFAULT_SOURCES = ("cucumber.json",)


def load_scenarios_from_sources(sources: tuple[str, ...]) -> tuple[list[dict], list[str]]:
    """Load scenarios from named JSON files under target/ (last file wins per scenario name)."""
    seen: dict[str, dict] = {}
    used: list[str] = []
    for fname in sources:
        path = TARGET / fname
        if not path.exists():
            continue
        used.append(fname)
        for feat in load_cuke_json(path):
            uri = feat.get("uri", "") or ""
            for el in feat.get("elements", []):
                if el.get("type") not in (None, "scenario"):
                    continue
                name = el.get("name", "(unnamed)")
                raw_tags = [t.get("name", "") for t in el.get("tags", [])]
                status, step, reason = scenario_status(
                    el.get("steps", []), el.get("before", []) + el.get("after", [])
                )
                seen[name] = {
                    "feature": feat.get("name", ""),
                    "uri": uri,
                    "name": name,
                    "tags": raw_tags,
                    "status": status,
                    "step": step,
                    "reason": reason,
                }
    return list(seen.values()), used


def feature_file_from_uri(uri: str) -> str:
    u = uri.replace("\\", "/")
    if "/" in u:
        return u.rsplit("/", 1)[-1]
    return u


def scenario_belongs_to_module(uri: str, spec: ModuleRerunSpec) -> bool:
    fname = feature_file_from_uri(uri)
    return any(fname.endswith(ff) or ff in fname for ff in spec.feature_files)


def rerun_tags_for_scenario(raw_tags: list[str], spec: ModuleRerunSpec) -> list[str]:
    """Case-level @tags to re-run (exclude meta / group tags)."""
    parsed = tags_in(" ".join(raw_tags))
    prefixes = tuple(p.upper() for p in spec.tag_prefixes)
    out: list[str] = []
    for tag in parsed:
        if tag.upper() in META_TAGS:
            continue
        norm = tag.upper()
        if any(norm.startswith(p) or norm.replace("-", "").startswith(p.replace("-", "")) for p in prefixes):
            out.append(tag if tag.startswith("@") else f"@{tag}")
    if out:
        return sorted(set(out), key=lambda t: t.upper())
    primary = case_tag(raw_tags)
    if primary and primary != "(untagged)":
        return [primary if primary.startswith("@") else f"@{primary}"]
    return []


def tag_expression(tags: list[str]) -> str:
    tags = sorted(set(tags), key=lambda t: t.upper())
    if not tags:
        return ""
    if len(tags) == 1:
        return tags[0]
    return "(" + " or ".join(tags) + ")"


def features_arg(spec: ModuleRerunSpec, scenarios: list[dict]) -> str:
    """Pick -Dcucumber.features from failing scenarios' URIs, or all module files."""
    files: list[str] = []
    for s in scenarios:
        fn = feature_file_from_uri(s.get("uri", ""))
        if fn and fn not in files:
            files.append(fn)
    if not files:
        files = list(spec.feature_files)
    paths = [f"src/test/resources/features/{fn}" for fn in files]
    return ",".join(paths)


def build_mvn_cmd(
    spec: ModuleRerunSpec,
    tag_expr: str,
    features: str,
    parallel: bool,
) -> str:
    parts = [".\\mvnw.cmd", "test"]
    if parallel:
        parts.append("-Pparallel")
        for arg in spec.parallel_maven_args:
            parts.append(f'"{arg}"')
    else:
        parts.append("-Pwatch")
    if spec.sandbox_parallel:
        parts.append('"-Dsandbox.parallel=true"')
    if spec.defer_cleanup:
        parts.append('"-Dautomation.deferCleanup=true"')
    parts.append(f'"-Dcucumber.features={features}"')
    full_tags = tag_expr
    if spec.default_module_tags and spec.default_module_tags not in tag_expr:
        full_tags = f"({tag_expr}) and {spec.default_module_tags}" if tag_expr else spec.default_module_tags
    parts.append(f'"-Dcucumber.tags={full_tags} and not @skip and not @sandbox-cleanup and not @module-cleanup"')
    return " ".join(parts)


def generate_for_module(
    spec: ModuleRerunSpec,
    all_scenarios: list[dict],
    sources: list[str],
) -> dict:
    module_scenarios = [s for s in all_scenarios if scenario_belongs_to_module(s.get("uri", ""), spec)]
    bad = [s for s in module_scenarios if s["status"] != "PASSED"]
    rerun_tag_set: list[str] = []
    items: list[dict] = []
    for s in bad:
        rtags = rerun_tags_for_scenario(s["tags"], spec)
        rerun_tag_set.extend(rtags)
        items.append(
            {
                "case_tags": rtags,
                "scenario": s["name"],
                "feature_file": feature_file_from_uri(s.get("uri", "")),
                "status": s["status"],
                "step": s["step"],
                "reason": s["reason"],
                "artifacts": artifact_dir(s["name"]),
            }
        )
    unique_tags = sorted(set(rerun_tag_set), key=lambda t: t.upper())
    tag_expr = tag_expression(unique_tags)
    features = features_arg(spec, bad)

    RERUN_DIR.mkdir(parents=True, exist_ok=True)
    mid = spec.module_id
    payload = {
        "module_id": mid,
        "label": spec.label,
        "kiwi_sheet": spec.kiwi_sheet,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "cucumber_sources": sources,
        "scenarios_in_module": len(module_scenarios),
        "non_pass": len(bad),
        "rerun_tags": unique_tags,
        "items": items,
    }
    (RERUN_DIR / f"{mid}_rerun.json").write_text(
        json.dumps(payload, indent=2), encoding="utf-8"
    )
    (RERUN_DIR / f"{mid}_rerun.tags").write_text(tag_expr + "\n", encoding="utf-8")

    if tag_expr:
        serial = build_mvn_cmd(spec, tag_expr, features, parallel=False)
        par = build_mvn_cmd(spec, tag_expr, features, parallel=True)
    else:
        serial = f"REM No failed/skipped scenarios for {spec.label} in {', '.join(sources) or 'cucumber JSON'}"
        par = serial

    (RERUN_DIR / f"{mid}_rerun.cmd").write_text(serial + "\n", encoding="utf-8")
    (RERUN_DIR / f"{mid}_rerun_parallel.cmd").write_text(par + "\n", encoding="utf-8")

    return {
        "module_id": mid,
        "label": spec.label,
        "non_pass": len(bad),
        "rerun_count": len(unique_tags),
        "tag_expr": tag_expr,
    }


def write_index(summaries: list[dict], sources: list[str]) -> None:
    lines = [
        "# Module re-run index",
        "",
        f"Generated: {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}",
        f"Sources: `{', '.join(sources) or 'none'}`",
        "",
        "| Module | Failed/skipped scenarios | Tags to re-run |",
        "|--------|-------------------------:|----------------|",
    ]
    for s in summaries:
        expr = s["tag_expr"] or "— (all passed)"
        if len(expr) > 60:
            expr = expr[:57] + "..."
        lines.append(
            f"| {s['label']} | {s['non_pass']} | {expr} |"
        )
    lines.extend(
        [
            "",
            "Re-run: `rerun run <module_id>` or open `reports/rerun/<module_id>_rerun.cmd`.",
            "",
        ]
    )
    (RERUN_DIR / "index.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

    parser = argparse.ArgumentParser(description="Generate module re-run files from Cucumber JSON")
    parser.add_argument("--module", "-m", help="Module id (users, sandbox, mci, …)")
    parser.add_argument("--all", action="store_true", help="All registered modules")
    parser.add_argument(
        "--source",
        action="append",
        default=None,
        help="Cucumber JSON under target/ (repeatable; default: cucumber.json only)",
    )
    args = parser.parse_args()

    sources = tuple(args.source) if args.source else DEFAULT_SOURCES
    scenarios, used = load_scenarios_from_sources(sources)
    if not scenarios:
        print(f"No scenarios in {sources} — run the module first (`mvn test`).")
        sys.exit(1)

    if args.all:
        specs = unique_specs()
    elif args.module:
        spec = resolve_module(args.module)
        if not spec:
            print(f"Unknown module {args.module!r}. Known: {[s.module_id for s in unique_specs()]}")
            sys.exit(1)
        specs = [spec]
    else:
        parser.print_help()
        sys.exit(1)

    summaries = []
    for spec in specs:
        summary = generate_for_module(spec, scenarios, used)
        summaries.append(summary)
        print(
            f"{spec.label}: {summary['non_pass']} non-pass -> "
            f"{summary['rerun_count']} tag(s) -> reports/rerun/{spec.module_id}_rerun.cmd"
        )

    if len(specs) > 1:
        write_index(summaries, used)


if __name__ == "__main__":
    main()
