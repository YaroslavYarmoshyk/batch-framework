# Input

Place `Акційна історія.xlsx` and `Актуальні СДП.xlsx` here.

Paths are configured under `excel-management.input` in `application.yml`. Relative paths are
resolved against the working directory or, when not found there, the module directory, so the
defaults work regardless of how the app is launched. The output report is written to
`src/main/resources/output/` (created automatically if missing).
