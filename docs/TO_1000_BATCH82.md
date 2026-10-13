# Path to 1000 verified automated cases (918 → 1000)

**Verified automated** = Kiwi `Automated` in Yes/Partial/Deviation **and** `Automation_Ref` `@tag` matches a Cucumber scenario (`reports/traceability_audit_summary.md`).

## Batch 82 list

`reports/to_1000_batch82.csv` — export via `python tools/export_to_1000_batch82.py`

| Area | Rows in batch | Action |
|------|-------------:|--------|
| Activities | 67 | Linked to existing `@SB-manage-*` in `sandbox.feature` (Manage Labs parity) |
| Pre_Delivery_Lab_Checklist | 8 | Partial refs → `@PDL-*` dashboard/mutate tags |
| Moodle_Reports | 2 | `@RPT-role` / `@RPT-subpage` deviation |
| Practice_Project | 1 | `@PP-30` deviation |
| Moodle settings | 1 | `@SET-70` |
| Content / Difficulty | 3 | `@contentsource` / `@difficulty` |

Apply Kiwi updates:

```powershell
python tools/apply_to_1000_batch82_kiwi.py
python tools/sync_kiwi_and_traceability.py
```

Optional smoke:

```powershell
.\mvnw.cmd test "-Dcucumber.tags=@activities_parity and not @provisioning"
```

**Note:** Activities sheet duplicates Manage Labs behaviour already covered on **Moodle_Sandbox_Labs**; refs point at the **same** proven scenarios (admin path). Teacher/manager matrix rows stay **Partial** per project role scope.
