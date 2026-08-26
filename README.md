# Batch Framework

A multi-module Gradle/Spring Boot monorepo for e-take's batch/forecasting jobs. Each module is an
independently runnable Spring Boot application; `excel-library` is a shared, non-executable library
consumed by the others.

## Modules

| Module                   | Purpose                                                              | Data                              |
|---------------------------|-----------------------------------------------------------------------|------------------------------------|
| `cyclic-action`           | Forecasts sales during promotional (cyclic) actions                   | `input/` xlsx in, `output/` xlsx out |
| `sales-plan`               | Builds the monthly sales plan per store/category                      | DB + classpath resources, `output/` xlsx out |
| `avg-check-plan`           | Forecasts the average-check plan                                      | DB in, `output/` xlsx out          |
| `turnover-plan`            | Builds the quarterly turnover plan                                    | DB in, `output/` xlsx out          |
| `store-plan-adjustment`    | Adjusts existing store plans for ad-hoc time-slot changes             | `input/` xlsx in, `output/` xlsx out |
| `excel-library`            | Shared Excel formatting/formula utilities (library, not an app)       | —                                  |

## Project Structure

This is a single Gradle build (`settings.gradle` lists every module as a subproject); there are no
nested git repositories or per-module remotes — everything lives and is versioned in this one repo.

```
batch-framework/
├── build.gradle              # shared plugin/dependency config for all subprojects
├── gradle/libs.versions.toml # centralized dependency & plugin versions
├── settings.gradle           # declares the module list
├── cyclic-action/
├── sales-plan/
├── avg-check-plan/
├── turnover-plan/
├── store-plan-adjustment/
└── excel-library/
```

Common dependencies (Spring Batch, POI, the JDBC driver, Lombok, etc.) and the Java toolchain
version are declared once in the root `build.gradle` and `gradle/libs.versions.toml`, and applied to
every subproject — individual module `build.gradle` files only add module-specific configuration
(e.g. the `bootJar` name, or a dependency on `excel-library`).

## Input / output convention

Modules that read or write Excel files on disk use `input/` and `output/` folders under
`src/main/resources/` (see each module's `input/README.md` where present). Paths are configured
under each module's `*-management`/`system-configuration*.input`/`.output` keys in
`application.yml`, resolved via a small `ResourcePaths` helper so relative paths work regardless of
the working directory the app is launched from (working directory, then the module directory).

The actual `.xlsx` files placed in those folders are **not** committed — they're real business data
and are git-ignored (see `.gitignore`); only the folder structure (via `README.md` placeholders) is
tracked.

## Building

```
./gradlew build
```

Build a single module:

```
./gradlew :turnover-plan:build
```

## Running

Each module needs its datasource and job-specific parameters supplied as environment variables
before running. Variables in use across modules:

| Variable              | Used by                                                        |
|------------------------|-----------------------------------------------------------------|
| `DB_URL`               | `sales-plan`, `turnover-plan`                                   |
| `DB_USERNAME`, `DB_PASSWORD` | `cyclic-action`, `avg-check-plan`, `store-plan-adjustment`, `turnover-plan` |
| `START_DATE`, `END_DATE`     | `cyclic-action`, `avg-check-plan`, `store-plan-adjustment`  |
| `YEAR`, `MONTH`, `ENABLED_DISTRIBUTION`, `UPLOAD_PLANS`, `CREATE_REPORT` | `sales-plan` |
| `PLANNED_YEAR`, `PLANNED_MONTH` | `turnover-plan`                                             |

```
./gradlew :cyclic-action:bootRun
```

## Configuration

Each module has its own `application.yml`/`application.yaml`. Database connection details, forecast
parameters and input/output paths are configured there.
