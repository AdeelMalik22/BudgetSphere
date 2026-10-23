# BudgetSphere

A native offline personal expense and budget manager for Android 8.0+ (API 26), built with Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Coroutines/Flow, and Navigation Compose.

This repository is an active development project. The automated verification results are recorded in [docs/verification.md](docs/verification.md); device-only acceptance and release signing remain outstanding.

## Capabilities

- Local account signup/login, password changes, logout, and Android Keystore-backed biometric enrollment/unlock.
- Authenticated financial privacy controls with default hiding, background locking, and screenshot/recent-app protection.
- Independent profiles with names, descriptions, icons, colors, and currencies; account/profile ownership is enforced in persistence and repositories.
- Expense and income CRUD, exact integer-minor-unit money, categories, date/type/amount/category filters, title/tag search, sorting, and incrementally loaded history.
- Private gallery/camera receipts, compressed images, thumbnails, preview/removal, and cleanup on financial-record deletion.
- Dashboard summaries, category distribution, monthly trends, income/expense comparisons, and overall/category monthly budgets.
- Appearance and security preferences, date formats, first weekday, and opt-in private local reminders.
- Profile CSV/JSON exports and authenticated encrypted full-account backup/restore through Android's document picker.

Production starts empty. There are no remote services, network permission, fake balances, or seeded accounts. Accounts are local to this installation; no email verification or remote password reset is offered.

## Build and run

Use JDK 17 or 21 and an Android SDK with platform 36. Configure `ANDROID_HOME` or an ignored `local.properties` containing `sdk.dir=/path/to/android-sdk`. SDK packages required by the pinned Android plugin may be installed automatically after license acceptance.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebugAndroidTest
```

The Gradle 8.13 wrapper pins the distribution SHA-256. Dependencies are pinned and downloaded through Google Maven/Maven Central over verified HTTPS. The Android plugin is 8.13.2 and Kotlin is 2.2.21; these versions were built together here rather than independently upgrading plugins with incompatible toolchain requirements.

Install on a connected device/emulator:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.budgetsphere.app/.MainActivity
./gradlew connectedDebugAndroidTest
```

The generated APK uses Android's development signing key. Release publishing requires your own private signing configuration; do not commit keystores, passwords, or credentials.

## Prepared cloud environment

For this Linux cloud checkout, `bash tools/cloud-setup.sh` refreshes the local JDK/SDK and runs the verification gate without changing source or lockfiles. In each new task, source `tools/cloud-env.sh` before invoking Gradle. Work in the existing isolated checkout; no additional Git worktree is needed.

The cloud scripts preserve TLS verification, verify downloaded tool checksums, and use the machine's installed trusted CA certificates. They store dependency caches and toolchain configuration outside the checkout.

## Tests and verification

Unit and Robolectric tests exercise monetary precision/overflow, dates, password verifiers, privacy grants, Room ownership/cascades and reopening, repository workflows, receipt cleanup, export isolation, encrypted backups with receipts, malformed backups, and a full Compose workflow including profile switching. An equivalent instrumentation flow is provided for an actual emulator/device. The recorded verification results are not a substitute for running the checks in the current environment.

See [docs/verification.md](docs/verification.md) for results and remaining hardware/device checks. GitHub Actions runs build, JVM tests, lint, and instrumentation APK assembly on pushes/PRs; configuring a workflow does not itself prove a remote run passed.

## Architecture

- `domain`: platform-independent models, repository contracts, money/date validation, ledger aggregation, and one-use document authorization.
- `data`: Room entities/DAO/schema, domain mappers, scoped repositories, private receipt storage, and data portability.
- `core`: Hilt bindings, password/backup cryptography, biometric integration, central privacy state, DataStore, and reminders.
- `ui`: Compose design system, ViewModel state/actions, navigation, authentication, profiles, transactions, dashboard, analytics, budgets, categories, settings, and portability.

Room is the financial source of truth. Flow emissions rebuild immutable, workspace-owned ledger aggregates on a background dispatcher; there is no persistent secondary financial cache. Scope changes replace observers and navigation state. Domain account models exclude password verifier material.

The initial schema is exported under `app/schemas`. Every future schema version must add a non-destructive Room migration and migration tests. Version 1 has no older production schema to migrate from; closing/reopening persistence and schema creation are tested.

## Security, recovery, and limitations

Read [SECURITY.md](SECURITY.md) before relying on local authentication or backups. Financial files rely on Android's sandbox/device encryption and are not independently encrypted at rest by this app. CSV/JSON are unencrypted; encrypted backups require a separate passphrase. Restore adds new profiles and preserves existing data. Changing a currency is blocked after records/budgets exist; historical money is never converted using invented exchange rates.

The privacy lock currently masks monetary values, charts, and sensitive form contents. Descriptive financial metadata such as transaction titles, category names, and dates still needs a complete locked-state masking pass before the privacy feature should be considered production-ready.

Biometric enrollment, camera providers, notification delivery, device/OEM lifecycle behavior, accessibility tooling, and Storage Access Framework providers need device testing. Baseline profiles and additional caches are deliberately omitted until measurement justifies them.

Branding sources and a 1024×1024 PNG are in [branding](branding); optional regeneration uses `python tools/render_branding.py` with Pillow. Launcher, monochrome, and splash assets are native Android resources.

The phase plan and acceptance walkthroughs are in [plan.md](plan.md). Features and fixes are committed and pushed individually.

Contributor and coding-agent context is in [CODEX.md](CODEX.md).
