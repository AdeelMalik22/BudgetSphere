# BudgetSphere development context

## Project

BudgetSphere is an offline-first Android expense and budget manager. It has no remote services, no network permission, no seeded data, and no remote account recovery. Accounts, profiles, transactions, budgets, receipts, and preferences are local to the installation.

Use JDK 17 or 21 and Android SDK platform 36. The project uses Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Compose, Hilt, Room, DataStore, Coroutines/Flow, and Navigation Compose.

## Important commands

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

For the prepared cloud environment:

```sh
source tools/cloud-env.sh
bash tools/cloud-setup.sh
```

Read [docs/verification.md](docs/verification.md) for the latest recorded results and device-only gaps. A successful JVM test or APK build does not prove hardware, OEM, accessibility, or release behavior.

## Architecture

- `domain`: platform-independent models, validation, money/date rules, ledger aggregation, and repository contracts.
- `data`: Room entities/DAO/schema, scoped repositories, receipt storage, export, and encrypted backup/restore.
- `core`: password hashing, backup encryption, biometric integration, privacy state, DataStore settings, dependency injection, and reminders.
- `ui`: Compose screens, navigation, ViewModel actions/state, authentication, profiles, transactions, dashboard, analytics, budgets, categories, settings, and portability.

Room is the financial source of truth. Account/profile ownership is represented in composite keys and must be checked in repository operations. Workspace changes must invalidate observers, navigation state, and privacy reveal grants.

## Security constraints

- Do not add network access, analytics, remote identity, fake balances, seeded accounts, or silent password recovery.
- Do not weaken password verification, backup authentication, TLS, dependency verification, or screenshot protection to make a check pass.
- Passwords and passphrases are supplied as mutable `CharArray` values and must be cleared on every path.
- Backups use authenticated AES-GCM with a separately derived passphrase key. Validate versions, sizes, IDs, references, dates, currencies, and amounts before database mutation.
- CSV/JSON exports are intentionally unencrypted and must remain explicitly authorized and profile-scoped.
- Financial database and receipt files rely on Android private storage/device encryption; this app does not independently encrypt them at rest.
- Room schema upgrades require explicit non-destructive migrations and migration tests. Do not enable destructive migration fallback.

## Current review note

The privacy lock masks amounts, charts, and sensitive form contents, but descriptive financial metadata is still visible while locked in some screens. In particular, transaction titles/categories/dates, transaction detail headings, budget category names/months, and the categories screen need a complete locked-state masking pass. Keep this limitation documented until fixed and tested.

Relevant code includes:

- `app/src/main/java/com/budgetsphere/app/ui/TransactionScreens.kt`
- `app/src/main/java/com/budgetsphere/app/ui/Dashboard.kt`
- `app/src/main/java/com/budgetsphere/app/ui/BudgetScreen.kt`
- `app/src/main/java/com/budgetsphere/app/ui/BudgetApp.kt`

## Change discipline

Inspect the working tree before editing and preserve unrelated user changes. Prefer `apply_patch` for source edits. Add or update tests for behavior changes, especially ownership, lifecycle races, privacy, backup validation, and receipt cleanup. Run the narrowest relevant checks first, then the full verification gate when dependencies and Android tooling are available.

Do not claim device testing, CI success, publication, or release readiness unless it was actually performed. Do not commit credentials, keystores, generated build outputs, or assistant attribution.

