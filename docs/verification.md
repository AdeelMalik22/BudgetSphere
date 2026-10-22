# Verification record

Date: 2026-10-22. This document distinguishes implemented functionality, current-machine checks, and device/release validation. It is not an independent security audit.

## Automated checks

The following commands passed after the final source-formatting pass:

```sh
source tools/cloud-env.sh   # this prepared cloud machine only
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Debug APK assembly | Passed in the current cloud machine |
| JVM/Robolectric tests | 23 passed, 0 failed, 0 errors, 0 skipped |
| Android lint | Passed with 0 errors, 8 warnings, and 2 hints |
| Instrumentation APK assembly | Passed |
| Connected Android instrumentation execution | Not run: adb requires `/home/agent/.android`, but the machine root filesystem is read-only even after a filesystem permission grant |
| Physical biometrics/camera/notification/SAF/OEM checks | Not run; require an Android device or a functioning emulator/adb host |
| GitHub Actions | Workflow is provided; remote execution is not verified by these local results |
| Signed release build/publication | Not performed; only a development-signed APK is generated |

The reusable `tools/cloud-setup.sh` was executed successfully against this environment, including SDK prerequisites, trust/proxy configuration, APK assembly, tests, lint, and instrumentation APK assembly. This verifies refresh/reuse in the current instance, not restoration into a fresh published task.

Lint advisories include five available dependency updates, the legacy `fullBackupContent` recommendation (`allowBackup=false` already disables automatic backup), an unnecessary API-26 resource qualifier, a KTX helper suggestion, and two state-boxing hints. No lint errors were suppressed or baselined.

## What the tests exercise

- `FinanceTest`: integer minor units, USD/JPY/KWD precision, unsupported non-monetary currency rejection, invalid/negative/overflow inputs, checked totals, budget remaining, identifier/password validation, leap-year dates, and week preferences.
- `LedgerTest`: income-minus-expense balance, overall/category monthly spending, date-bounded category/month reports, and replacement of immutable derived state after data changes.
- `PasswordHasherTest`: independent salts/verifiers, correct/incorrect passwords, and rejection of malformed derivation parameters.
- `PrivacyManagerTest`: failed authentication remains hidden; scope changes, locking, and logout invalidate earlier reveal grants.
- `DocumentAuthorizationTest`: a user-approved file operation can finish after backgrounding, while grants cannot be reused, transferred to another profile, or used after logout.
- `DatabaseIsolationTest`: scoped reads, aggregates, mutations, filters, foreign-key ownership rejection, category restrictions, cascades, and file-backed database close/reopen persistence.
- `RepositoryWorkflowTest`: account creation/login/password changes, account/profile mutation rejection, receipt cleanup/orphans, camera orientation normalization, encrypted backup/restore with receipt files and relationships, malformed integer/preference rejection before commit, tamper rejection, and profile export isolation.
- `SessionRaceTest`: a blocked password verification finishing after logout cannot repopulate the authenticated session.
- `ProfileSwitchRaceTest`: delayed manual loads and delayed startup restoration cannot replace a newer manual workspace selection, render the previous profile, or overwrite the saved active profile.
- `LoadStateTest`: failed reads become retry states without exposing underlying exception details; successful data remains distinguishable from loading/failure.
- `AppFlowTest`: Compose signup with invalid-field feedback, profile creation, rejected/correct password reveal, expense creation/editing, budget creation, privacy on transaction/analytics/budget screens, switching to an empty Friend profile, and switching back with values still hidden.

`DeviceAppFlowTest` supplies the corresponding instrumentation workflow using a unique local account per run. Building its APK does not mean it executed.

## Device acceptance still required

Run `./gradlew connectedDebugAndroidTest` on a working Android host, then manually verify:

1. Force-stop/reopen, authentication after process death, persisted expense data, saved-profile ownership, and privacy defaults.
2. Strong biometric enrollment/unlock, cancellation, failure, password fallback, changed enrollment, key invalidation, and disabled/no-hardware cases.
3. Screenshots and recent-app previews on minimum/current supported Android versions, foreground/background transitions, rotation, and external-picker return.
4. Local Photo Picker/camera capture, URI permission denial, process death during camera use, missing/corrupt receipts, and low-storage behavior.
5. CSV/JSON and encrypted backup/restore with actual Storage Access Framework providers, wrong passphrases, truncated files, and failed writes.
6. Notification permission denial, opt-in reminders, Android scheduling delays, and hidden notification content.
7. TalkBack semantics, large fonts, contrast, small/tablet/landscape layouts, keyboard behavior, and reduced-motion settings.
8. Large transaction histories/receipt collections and backup memory use on low-memory devices. Version 1 backups use a bounded in-memory format; they are not a streaming archive.

## Limits and release gate

No future-version migration path exists yet because this is schema version 1. The initial schema is exported, and persistence/reopening is tested. Every schema upgrade must add explicit migration code and migration tests; destructive fallback must remain disabled.

Financial Room/receipt files rely on Android private storage and device encryption; they are not separately encrypted at rest. Read [SECURITY.md](../SECURITY.md) for the password, biometric, backup, recovery, and export model.

The application can be developed and built here. Device-only acceptance, independent security review, release signing, fresh-task environment restoration, and publication remain distinct outstanding checks. Do not treat a local debug build or saved environment draft as production-release approval.
