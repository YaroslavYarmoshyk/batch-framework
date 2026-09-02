# Input

Place `avg_items_per_check_plan.xlsx` here (columns: `stores`, `avg_items_per_check_plan`) — one
row per store name with its planned average items-per-check value.

The path is configured under `system-configuration.input` in `application.yml`. Relative paths
are resolved against the working directory or, when not found there, the module directory, so the
default works regardless of how the app is launched.
