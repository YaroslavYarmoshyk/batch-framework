# store-plan-adjustment

Spring Batch module that **corrects daily turnover plans** for store/date combinations where the
store lost trading hours (renovation, partial closure, equipment failure, …). It reads a list of
disruptions from an Excel file and writes an Excel report comparing the original plan against the
adjusted turnover.

The job is **strictly read-only** against the database: every query is a `SELECT`. It never writes,
updates, or alters any table.

## How it works

For each affected daily plan the turnover is recomputed as:

```
adjusted = plan − (Σ share within the lost window) × plan + (actual sales within the lost window)
```

- **share** — the store/category's typical within-day turnover distribution, in 30-minute slots,
  built as a *pooled ratio* over the **4 most recent occurrences of the same weekday before the
  run's start date**: `Σ slot turnover / Σ whole-day turnover`. Dates listed in the input file are
  excluded per store so a "broken" day does not pollute the profile.
- **actual sales within the lost window** — real `transactions` for that exact store/category/date
  inside the disrupted slots (a closure is often only partial; future dates simply have none).

`Σ share` is clamped to `[0, 1]`. `margin` is carried through from the original plan unchanged.

### Pipeline (`PlanAdjustmentService.adjustPlans`)

1. Read the disruptions file → one request per (store, date), time ranges normalized to 30-min slots.
2. Resolve store **names** → `locations.id`; unknown names are logged and skipped.
3. Load `daily_sales_plans` within `[START_DATE, END_DATE]` for those stores, keep only the
   disrupted (store, date) pairs.
4. Build intraday share profiles (`ShareProfileService`).
5. Read actual slot-level sales on the plan dates.
6. Apply the formula and resolve category names for the report.
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
| `system-configuration.input-columns` | yml | 0-based column indices: `STORE`, `DATE`, `TIME_RANGES`. |

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

Endpoints are rounded to the nearest 30 minutes (`19:10 → 19:00`, `19:20 → 19:30`). Multiple rows
for the same store+date are merged.

## Output report

Sheet `Adjusted plans`, one row per adjusted (store, category, date):

`date` · `store` · `category` · `turnover` · `margin` · `adjusted_turnover`

## Edge cases

- **`all day`** → whole plan removed, only actual sales remain.
- **Future plan dates** → no transactions yet ⇒ actual sales `= 0`.
- **No historical profile** (store had no sales on those weekdays) ⇒ `Σ share = 0`; only actual
  sales are added back (logged).
- **Degenerate range** (`19:10-19:14`, collapses after rounding) ⇒ the single slot the start falls in.
- **Overnight / inverted range** (`23:00-01:00`) ⇒ skipped with a warning.
- **Unknown store name** / **plan with no matching disruption** ⇒ skipped.
- **Null/zero plan turnover** ⇒ adjusted `= actual sales`.

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

Unit tests cover the time-range parser (`TimeSlotParserTest`) and the adjustment formula
(`PlanAdjustmentServiceTest`) and need no database.

> The repository's Gradle wrapper jar is empty, so the `gradle` commands above use a system Gradle
> 9.3.1 install. Run `gradle wrapper --gradle-version 9.3.1` to restore `./gradlew`.
