# Input

Place `store_plan_adjustments.xlsx` here (columns: `store`, `date`, `time_ranges`, `amount`). The
`amount` column is optional per row — leave it blank to estimate that row's loss via the intraday
share/actual-sales algorithm, or fill it in with a known loss to subtract it from the plan directly.

Paths are configured under `system-configuration.resources` in `application.yml`. Relative paths
are resolved against the working directory or, when not found there, the module directory, so the
defaults work regardless of how the app is launched. The output report is written to
`src/main/resources/output/` (created automatically if missing).
