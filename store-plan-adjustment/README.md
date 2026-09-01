# store-plan-adjustment

Spring Batch module that **corrects daily turnover plans** for store/date combinations where the
store lost trading hours (renovation, partial closure, equipment failure, …). It reads a list of
disruptions from an Excel file and writes an Excel report comparing the original plan against the
adjusted turnover.

The job is **strictly read-only** against the database: every query is a `SELECT`. It never writes,
updates, or alters any table.

## How it works

For each disrupted daily plan the turnover is recomputed as:

```
adjusted = plan − (Σ share within the lost window) × plan + (actual sales within the lost window) − amount
```

- **share** — the store/category's typical within-day turnover distribution, in 30-minute slots,
  built as a *pooled ratio* over the **4 most recent occurrences of the same weekday before the
  run's start date**: `Σ slot turnover / Σ whole-day turnover`. Dates listed in the input file are
  excluded per store so a "broken" day does not pollute the profile.
- **actual sales within the lost window** — real `transactions` for that exact store/category/date
  inside the disrupted slots (a closure is often only partial; future dates simply have none).
- **amount** — an optional, per-row **known** loss declared directly in the input file (see below).
  A row that carries an amount **skips share/actual-sales estimation for its own window entirely**
  and is subtracted from the plan as-is; a row with no amount is estimated as above. Both kinds of
  rows can appear for the same store/date and combine additively (e.g. an `all day` row with an
  amount plus a separate time-range row with no amount). The store/date's total amount is split
  across its categories in proportion to each category's plan turnover (equally if the store/date's
  total turnover is zero), so the per-category deductions sum back to the declared amount.

`Σ share` is clamped to `[0, 1]`. `margin` is carried through from the original plan unchanged.
Plan dates for which the input file has no disruption at all are reported with their turnover
unchanged (see *Output report* below).

### Pipeline (`PlanAdjustmentService.adjustPlans`)

1. Read the disruptions file → one request per (store, date): time ranges from rows with no amount
   are normalized to 30-min slots and go through the formula above; rows with an amount are summed
   instead and skip estimation. Rows whose date falls outside `[START_DATE, END_DATE]` are dropped
   before anything else — a store with no adjustment inside this run's range is not part of the run
   at all, and never appears in the report.
2. Resolve store **names** → `locations.id`; unknown names are logged and skipped.
3. Load **every** `daily_sales_plans` row within `[START_DATE, END_DATE]` for the remaining stores.
4. Build intraday share profiles (`ShareProfileService`) for the disrupted (store, date) subset only.
5. Read actual slot-level sales on the disrupted plan dates.
6. Apply the formula to disrupted plans, pass every other plan through unchanged, and resolve
   category names for the report.
7. Write the report workbook (`ReportExcelService`).

## Configuration

Set in `src/main/resources/application.yml`; sensitive/volatile values come from environment
variables.

| Setting | Source | Notes |
|---|---|---|
| `START_DATE`, `END_DATE` | env | `dd.MM.yyyy`. Range of plan dates to adjust. |
| `DB_USERNAME`, `DB_PASSWORD` | env | SQL Server credentials. |
| `system-configuration.resources.input` | yml | Path to `store_plan_adjustments.xlsx`. |
| `system-configuration.resources.output` | yml | Path to the report (parent folder auto-created). |
| `system-configuration.input-columns` | yml | 0-based column indices: `STORE`, `DATE`, `TIME_RANGES`, `AMOUNT`. |

Relative paths are resolved so they work regardless of the JVM's working directory: an absolute
path is used as-is; a relative path is taken relative to the working directory when the file already
exists there, otherwise relative to the **module directory** (the nearest ancestor of the running
classes/jar containing `build.gradle`). So the defaults point at `src/main/resources/input` and
`src/main/resources/output` inside this module whether the job is started via `gradle
:store-plan-adjustment:bootRun`, the IntelliJ Spring Boot run configuration, or the packaged jar.

## Input file — `store_plan_adjustments.xlsx`

First row is a header; one row per disruption. A sample lives at
[`src/main/resources/input/store_plan_adjustments.xlsx`](src/main/resources/input/store_plan_adjustments.xlsx).

| Column | Example | Meaning |
|---|---|---|
| `store` | `Магазин №101` | Store **name** (must match `locations.name` of a store open as of `START_DATE` — i.e. no `close_date`, or one after `START_DATE`). |
| `date` | `10.06.2026` | The disrupted day (Excel date cell or `dd.MM.yyyy` text). |
| `time_ranges` | `19:00-20:00;21:34-22:10` | `;`-separated `HH:mm-HH:mm` ranges, or `all day`. |
| `amount` | `300` | Optional. A **known** turnover loss for this row's window. When present, this row is subtracted from the plan as-is instead of being estimated — see *How it works*. Leave blank to keep the row on the share/actual-sales algorithm. |

Endpoints are rounded to the nearest 30 minutes (`19:10 → 19:00`, `19:20 → 19:30`). Multiple rows
for the same store+date are merged: rows with no `amount` union their time ranges; rows with an
`amount` have their amounts summed. A row still needs a valid `time_ranges` value even when it
carries an `amount`.

## Output report

Sheet `Adjusted plans`, one row per (store, category, date) in range for the stores that appear in
the input file — disrupted rows are corrected, every other date is reported with its plan turnover
unchanged:

`date` · `store` · `category` · `turnover` · `margin` · `adjusted_turnover`

## Edge cases

- **`all day`** → whole plan removed, only actual sales remain (unless the row also has an `amount`,
  in which case the amount is used instead — see below).
- **Future plan dates** → no transactions yet ⇒ actual sales `= 0`.
- **No historical profile** (store had no sales on those weekdays) ⇒ `Σ share = 0`; only actual
  sales are added back (logged) — not logged when the window is entirely covered by an `amount`.
- **Degenerate range** (`19:10-19:14`, collapses after rounding) ⇒ the single slot the start falls in.
- **Overnight / inverted range** (`23:00-01:00`) ⇒ skipped with a warning.
- **Unknown store name** ⇒ skipped. **Plan with no matching disruption** ⇒ reported unchanged (not skipped).
- **Null/zero plan turnover** ⇒ adjusted `= actual sales − amount`.
- **`amount` present** ⇒ the row's window skips share/actual-sales estimation entirely; the
  store/date's total amount is split across its categories in proportion to plan turnover (equally
  if that total is zero). An unparseable `amount` cell warns and falls back to the algorithm for
  that row rather than dropping it.

## Build & run

```bash
# Compile + unit tests (no DB required)
gradle :store-plan-adjustment:test

# Package
gradle :store-plan-adjustment:bootJar

# Run (requires DB access / VPN)
START_DATE=01.06.2026 END_DATE=30.06.2026 \
DB_USERNAME=... DB_PASSWORD=... \
gradle :store-plan-adjustment:bootRun
```

Unit tests cover the time-range parser (`TimeSlotParserTest`), the adjustment/allocation formulas
(`PlanAdjustmentServiceTest`), and input-file merging including the `amount` column
(`AdjustmentFileServiceTest`) — none need a database.

> The repository's Gradle wrapper jar is empty, so the `gradle` commands above use a system Gradle
> 9.3.1 install. Run `gradle wrapper --gradle-version 9.3.1` to restore `./gradlew`.
