<h1 align="center">JSpecify Migration Kit</h1>

<p align="center">
  Production-minded tooling for moving Java projects from legacy nullness annotations to JSpecify.
</p>

<p align="center">
  <a href="https://github.com/javamodernizationlabs/JSpecify-migration-kit/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/javamodernizationlabs/JSpecify-migration-kit/actions/workflows/ci.yml/badge.svg"></a>
  <a href="https://github.com/javamodernizationlabs/JSpecify-migration-kit/actions/workflows/jml.yml"><img alt="JML" src="https://github.com/javamodernizationlabs/JSpecify-migration-kit/actions/workflows/jml.yml/badge.svg"></a>
  <a href="https://github.com/javamodernizationlabs/JSpecify-migration-kit/actions/workflows/security.yml"><img alt="Security" src="https://github.com/javamodernizationlabs/JSpecify-migration-kit/actions/workflows/security.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-blue.svg"></a>
  <a href="https://docs.oracle.com/en/java/javase/21/"><img alt="Java 21+" src="https://img.shields.io/badge/Java-21%2B-007396.svg"></a>
  <a href="https://jspecify.dev/"><img alt="JSpecify 1.0" src="https://img.shields.io/badge/JSpecify-1.0.0-4b32c3.svg"></a>
</p>

<p align="center">
  <a href="#why-jspecify-migration-kit">Why</a> |
  <a href="#installation">Installation</a> |
  <a href="#quick-start">Quick Start</a> |
  <a href="#build-integrations">Build Integrations</a> |
  <a href="#release-artifacts">Release Artifacts</a> |
  <a href="#migration-playbook">Migration Playbook</a> |
  <a href="#configuration">Configuration</a> |
  <a href="#github-actions">GitHub Actions</a> |
  <a href="#modules">Modules</a>
</p>

---

JSpecify Migration Kit is a Java 21+ CLI, Gradle plugin, Maven plugin surface,
and OpenRewrite recipe catalog for teams that want null-safety migration to be
observable, repeatable, and CI-friendly.

It inventories existing annotations, rewrites safe cases, reports risky cases,
generates NullAway configuration, and verifies what Kotlin callers will see
after the migration.

Latest release: [`v0.1.0`](https://github.com/javamodernizationlabs/JSpecify-migration-kit/releases/tag/v0.1.0).

## Why JSpecify Migration Kit?

JSpecify is a type-use nullness model. Most legacy Java annotations are not.
That gap makes migrations easy to start and easy to get subtly wrong.

This kit is built around a safer workflow:

| Capability | What it gives you |
| --- | --- |
| Annotation inventory | Finds JetBrains, JSR-305, Spring, FindBugs, Checker Framework, RxJava, Reactor, Android, and Micrometer nullness annotations. |
| Migration plan | Turns inventory into ordered phases with risk, issues, recommendations, and CI gates. |
| OpenRewrite recipes | Adds `org.jspecify:jspecify`, converts known annotations, flags unsafe package defaults, and removes old annotation dependencies. |
| Multi-format reports | Writes console, HTML, Markdown, JSON, SARIF, and JUnit XML output for humans, CI, and code scanning. |
| Kotlin verification | Generates Kotlin-facing assertions so platform type leaks are visible before release. |
| NullAway handoff | Produces Gradle/Error Prone snippets and a staged warn-to-error rollout path. |

## Installation

Use the released CLI distribution when you only need the command-line tool:

```bash
curl -L -o jml-cli-0.1.0.zip \
  https://github.com/javamodernizationlabs/JSpecify-migration-kit/releases/download/v0.1.0/jml-cli-0.1.0.zip
unzip -q jml-cli-0.1.0.zip
JML="$PWD/jml-0.1.0/bin/jml"
```

For local development, install the CLI from source:

```bash
./gradlew :jspecify-cli:installDist
JML="$PWD/modules/jspecify-cli/build/install/jml/bin/jml"
```

## Quick Start

Create a first migration plan:

```bash
$JML jspecify plan \
  --project . \
  --format console,html,markdown,sarif,json \
  --output-dir build/reports/jml/jspecify
```

Preview the safe rewrite set:

```bash
$JML jspecify rewrite \
  --project . \
  --recipe add-dependency,convert-known-annotations \
  --dry-run
```

Apply only after reviewing `build/reports/jml/jspecify/rewrite.md`:

```bash
$JML jspecify rewrite \
  --project . \
  --recipe add-dependency,convert-known-annotations \
  --apply
```

Generate coverage and Kotlin interop artifacts:

```bash
$JML jspecify coverage --project . --scope public-api --format html,json
$JML jspecify verify-kotlin --project . --generate-samples --compile
```

## CLI

The command surface follows `jml jspecify <command>`:

| Command | Purpose |
| --- | --- |
| `plan` | Inventory annotations, estimate risk, and write migration reports. |
| `rewrite` | Preview or apply selected rewrite recipes. |
| `coverage` | Measure public API nullness coverage. |
| `nullaway-config` | Generate NullAway/Error Prone Gradle Kotlin DSL snippets. |
| `verify-kotlin` | Generate Kotlin verification sources and optional compile checks. |
| `report` | Emit JSON, Markdown, SARIF, HTML, and JUnit XML reports from a scan. |
| `explain` | Explain a JSpecify Migration Kit rule id. |

Examples:

```bash
jml jspecify plan --project . --baseline-write config/jspecify-baseline.json
jml jspecify plan --project . --baseline config/jspecify-baseline.json --fail-on high
jml jspecify nullaway-config --mode warn --annotated-packages com.acme --apply
jml jspecify explain jspecify.public-api-unspecified
```

JBang alias metadata is included in `jbang-catalog.json`:

```bash
jbang jml@jbang-catalog.json jspecify plan --project .
```

## Build Integrations

### Gradle

```kotlin
plugins {
    id("io.github.javamodernizationlabs.jspecify-migration") version "0.1.0"
}

jspecifyMigration {
    jspecifyVersion.set("1.0.0")
    reportsDirectory.set(layout.buildDirectory.dir("reports/jml/jspecify"))

    migration {
        mode.set("incremental")
        defaultScope.set("public-api")
        convertKnownAnnotations.set(true)
        addNullMarked.set(false)
        inferFromJavadocs.set(false)
    }

    nullaway {
        enabled.set(true)
        mode.set("warn")
        annotatedPackages.set(listOf("com.acme"))
        excludedClasses.set(listOf("com.acme.generated.*"))
    }

    kotlinVerification {
        enabled.set(true)
        generatedSourceSet.set("jspecifyKotlinVerification")
        failOnWarnings.set(false)
    }
}
```

| Task | Output |
| --- | --- |
| `jspecifyPlan` | Migration plan and issue reports. |
| `jspecifyReport` | JSON, Markdown, SARIF, HTML, and JUnit XML reports. |
| `jspecifyRewriteDryRun` | Safe rewrite preview. |
| `jspecifyRewriteApply` | In-place safe rewrite application. |
| `jspecifyCoverage` | Public API nullness coverage report. |
| `jspecifyNullAwayCheck` | NullAway readiness report and Gradle snippet. |
| `jspecifyVerifyKotlin` | Kotlin interop verification artifacts. |

```bash
./gradlew jspecifyPlan jspecifyCoverage jspecifyRewriteDryRun
```

### Maven

```xml
<plugin>
  <groupId>io.github.javamodernizationlabs</groupId>
  <artifactId>jspecify-migration-maven-plugin</artifactId>
  <version>0.1.0</version>
  <configuration>
    <jspecifyVersion>1.0.0</jspecifyVersion>
    <migrationMode>incremental</migrationMode>
    <convertKnownAnnotations>true</convertKnownAnnotations>
    <addNullMarked>false</addNullMarked>
  </configuration>
</plugin>
```

```bash
mvn jspecify-migration:plan
mvn jspecify-migration:rewrite-dry-run
mvn jspecify-migration:rewrite-apply
mvn jspecify-migration:coverage
mvn jspecify-migration:nullaway-check
mvn jspecify-migration:verify-kotlin
mvn jspecify-migration:report
```

### OpenRewrite

```kotlin
plugins {
    id("org.openrewrite.rewrite") version "latest.release"
}

rewrite {
    activeRecipe("io.github.jml.jspecify.Migrate")
}

dependencies {
    rewrite("io.github.javamodernizationlabs:jspecify-migration-rewrite-recipes:0.1.0")
}
```

Available recipe entry points:

| Recipe | Description |
| --- | --- |
| `io.github.jml.jspecify.Migrate` | Safe default migration bundle. |
| `io.github.jml.jspecify.AddJSpecifyDependency` | Adds `org.jspecify:jspecify`. |
| `io.github.jml.jspecify.ConvertKnownAnnotations` | Converts known legacy annotations. |
| `io.github.jml.jspecify.FindUnsafeNullnessDefaults` | Flags legacy package-level defaults for review. |
| `io.github.jml.jspecify.FixTypeUseAnnotationPlacement` | Finds ambiguous container-vs-element placements. |
| `io.github.jml.jspecify.RemoveOldAnnotationDependencies` | Removes old annotation-only dependencies. |
| `io.github.jml.jspecify.SpringPreset` | Spring-focused migration preset. |
| `io.github.jml.jspecify.ReactorPreset` | Reactor-focused migration preset. |
| `io.github.jml.jspecify.MicrometerPreset` | Micrometer-focused migration preset. |

## Release Artifacts

The `v0.1.0` release uses these user-facing assets and coordinates:

| Surface | Coordinate or asset |
| --- | --- |
| CLI distribution | [`jml-cli-0.1.0.zip`](https://github.com/javamodernizationlabs/JSpecify-migration-kit/releases/download/v0.1.0/jml-cli-0.1.0.zip) and [`jml-cli-0.1.0.tar`](https://github.com/javamodernizationlabs/JSpecify-migration-kit/releases/download/v0.1.0/jml-cli-0.1.0.tar) |
| CLI Maven artifact | `io.github.javamodernizationlabs:jml-cli:0.1.0` |
| Core library | `io.github.javamodernizationlabs:jspecify-migration-core:0.1.0` |
| OpenRewrite recipes | `io.github.javamodernizationlabs:jspecify-migration-rewrite-recipes:0.1.0` |
| Maven plugin | `io.github.javamodernizationlabs:jspecify-migration-maven-plugin:0.1.0` |
| Gradle plugin | `io.github.javamodernizationlabs.jspecify-migration` version `0.1.0` |

## Migration Playbook

1. Build an inventory with `jml jspecify plan`.
2. Add the JSpecify dependency with the rewrite recipe.
3. Convert known `@Nullable` and `@NonNull` annotations.
4. Review ambiguous type-use placements manually.
5. Add `@NullMarked` package by package, starting with low-risk internals.
6. Run public API coverage and Kotlin verification.
7. Enable NullAway in `warn` mode.
8. Commit a baseline and promote CI to `error` mode when new issues are under control.

The planner emits the same phases directly in report output, so local runs and
CI reviews stay aligned.

## Configuration

The CLI reads `jml.yml` and `jspecify.yml` from the project root. Unknown keys
are ignored so newer configs stay compatible with older binaries.

```yaml
jspecify:
  version: "1.0.0"

reports:
  formats: [console, html, markdown, sarif, json]
  outputDirectory: build/reports/jml/jspecify

sourceRoots:
  - src/main/java
  - src/test/java

migration:
  generatedCode:
    exclude: true
    patterns:
      - "**/generated/**"
      - "**/target/generated-sources/**"
      - "**/build/generated/**"

packagePolicy:
  markPackages:
    - com.acme.internal
  leaveUnmarked:
    - com.acme.legacy

publicApi:
  include:
    - com.acme.api.**
  exclude:
    - com.acme.internal.**
  jpmsExportsOnly: false

nullaway:
  enabled: true
  mode: warn
  annotatedPackages:
    - com.acme
  excludedClasses:
    - com.acme.generated.*

kotlinVerification:
  enabled: true
  failOnWarnings: false
  generatedTestsDirectory: build/jspecify-kotlin-verification

scanner:
  followSymlinks: false

annotations:
  mappings:
    com.example.Nullable: org.jspecify.annotations.Nullable
    com.example.NotNull: org.jspecify.annotations.NonNull
```

## Reports

Reports default to `build/reports/jml/jspecify`.

| File | Consumer |
| --- | --- |
| `index.html` | Human review in a browser. |
| `plan.md` | Pull request comments, release notes, and migration docs. |
| `plan.json` | Dashboards and internal platform tooling. |
| `plan.sarif` | GitHub code scanning. |
| `TEST-jspecify-migration.xml` | CI systems that understand JUnit XML. |
| `rewrite.md` | Dry-run and apply summaries for rewrite steps. |
| `nullaway.gradle.kts` | Generated Error Prone/NullAway Gradle snippet. |

## GitHub Actions

This repository ships a composite action for JSpecify analysis.

```yaml
name: JML

on:
  pull_request:

jobs:
  analyze:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      security-events: write
    steps:
      - uses: actions/checkout@v4
      - uses: ./.github/actions/jml-analyze
        with:
          tools: jspecify
          fail-on: high
          sarif-upload: true
```

The action installs the local CLI, runs `jml jspecify plan`, writes JSON, SARIF,
HTML, and JUnit reports, then uploads SARIF when requested.

## Before and After

Legacy input:

```java
package com.acme;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UserApi {
    public @NotNull String name() {
        return "";
    }

    public @Nullable String nickname() {
        return null;
    }
}
```

After `convert-known-annotations`:

```java
package com.acme;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class UserApi {
    public @NonNull String name() {
        return "";
    }

    public @Nullable String nickname() {
        return null;
    }
}
```

After package policy review, prefer package-level `@NullMarked`:

```java
@NullMarked
package com.acme;

import org.jspecify.annotations.NullMarked;
```

Kotlin verification can then assert caller-facing types:

```kotlin
fun verifyUserApi(api: com.acme.UserApi) {
    val nameValue: String = api.name()
    val nicknameValue: String? = api.nickname()
}
```

## Modules

| Module | Responsibility |
| --- | --- |
| `jspecify-core` | Config loading, annotation scanning, migration planning, reports, baselines, coverage, rewrite model, and Kotlin verification model. |
| `jspecify-rewrite-recipes` | OpenRewrite recipe catalog and custom Java recipes. |
| `jspecify-cli` | `jml jspecify ...` command line interface. |
| `jspecify-gradle-plugin` | Gradle plugin `io.github.javamodernizationlabs.jspecify-migration`. |
| `jspecify-maven-plugin` | Maven goals for plan, rewrite, coverage, NullAway, Kotlin verification, and reports. |

## Development

```bash
./gradlew build
./gradlew test
./gradlew publishToMavenLocal
./gradlew releaseQualityGate
```

`releaseQualityGate` verifies OSS metadata, reproducible archive settings,
local quality gates, and GitHub security scanning wiring.

## Project Status

Latest release: [`v0.1.0`](https://github.com/javamodernizationlabs/JSpecify-migration-kit/releases/tag/v0.1.0).

Current development branch version: `0.1.0-SNAPSHOT`.

The kit is aimed at early adopters and library maintainers who want a practical
JSpecify migration workflow before enforcing strict nullness gates everywhere.
Expect the command surface, report schema, and rewrite coverage to evolve across
early `0.x` releases.

## Contributing

Contributions are welcome through focused pull requests:

- Keep migrations observable: every automated change should have a report or dry-run path.
- Prefer safe defaults: flag ambiguous type-use semantics instead of guessing.
- Keep CI output machine-readable: SARIF, JSON, and JUnit XML are part of the product.

See [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md),
and [SECURITY.md](SECURITY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
