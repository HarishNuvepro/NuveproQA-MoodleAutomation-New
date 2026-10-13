# Conversion before execution (Kiwi ↔ Cucumber linking)

Use this when you want **maximum traceability conversion now** and will **run tests later**.

## One-shot promote (recommended)

```powershell
python tools/promote_all_verifiable_coverage.py
python tools/sync_kiwi_and_traceability.py
```

What it does:

1. Re-runs module-specific `tools/update_*_coverage.py` scripts (Git URL, randomization, assessment parity, MCI, settings, …).
2. Scans **all** `src/test/resources/features/*.feature` scenarios.
3. For each Kiwi case row (every module sheet):
   - **Automated = No** → left unchanged (QA judgement).
   - If `Automation_Ref` / S.No contains a `@tag` that exists in the repo:
     - Real steps (no stub Then) → **Yes**
     - Stub / `@provisioning` scenario → **Partial** (still **verified** in traceability)
     - `@deviation` scenario → **Deviation**
4. Refreshes **Automation_Coverage** counts per touched sheet.

## Metrics (strict)

| Metric | Meaning |
|--------|---------|
| **Verified automated** | Yes/Partial/Deviation **and** matching Cucumber `@tag` |
| **Kiwi Yes only** | Under-counts Partial/Deviation links |
| **Case_Status Pass** | Only after you run tests and merge cucumber JSON |

Check: `reports/traceability_audit_summary.md`, `reports/traceability_portfolio_counts.txt`.

## After you execute tests

```powershell
python tools/merge_cuke_for_traceability.py
python tools/sync_kiwi_and_traceability.py
python tools/failure_report.py
```

## What cannot be converted without new work

Typical **remaining not verified** buckets:

| Bucket | Why |
|--------|-----|
| **Blank `Automated`** | No Kiwi judgement + no `@tag` in ref |
| **Deferred CGAP** | Gap row — scenario not written yet |
| **Hard modules** | Moodle_Logs, cron, multi-tenant, Orgo, SSO — no `.feature` suite |
| **Greenfield lab sheets** | CloudLabs status, Lab Control Panel, region labs — no page objects |
| **End-user visibility** | Partial only until `@provisioning` run proves outcome |

Partial rows are **intentionally** verified before execution: they prove a scenario exists; Pass/Fail comes from the run.
