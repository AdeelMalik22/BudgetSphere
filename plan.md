# BudgetSphere implementation plan

## Goal

Build a complete native, offline Android personal finance application using the uploaded BudgetSphere specification. Production data starts empty. All accounts and records remain local, and no Internet permission, cloud service, analytics SDK, or advertising service is included.

This plan is the implementation checklist and delivery record. A feature is complete only after its behavior is implemented and the relevant validation passes. Record blocked, failed, skipped, and unexecuted checks explicitly.

## Current status

- The repository was initially empty, with no commits or remote branches.
- Repository-local Git author: Subhan Adeel <ms1640070@gmail.com>.
- Initial Gradle, Android manifest, branding, application, money/date utilities, and Room model/DAO files have been created. These are preliminary and have not yet passed build or security validation.
- Android SDK and Gradle preparation has begun. Toolchain installation and dependency compatibility still need verification.
- Implementation has resumed. Each feature or fix is committed and pushed separately after its relevant checks.
- The plan has been committed and pushed to `main`; Git write access is verified. Do not add assistant contributor attribution or co-author trailers.

## Technical direction

- Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL; minimum API 26.
- Select current stable, mutually compatible Android, Kotlin, Compose, KSP, Room, and Hilt versions and verify them through an actual build. Pin the Gradle wrapper and distribution checksum.
- Clean Architecture with presentation, domain, and data layers. Feature ViewModels expose immutable UI state and consume repository/use-case contracts.
- Room is the single source of truth for accounts, profiles, categories, transactions, budgets, and receipt metadata. Use DataStore for nonsensitive preferences.
- Hilt supplies repositories, database, settings, security services, and dispatchers. Use Coroutines and Flow, lifecycle-aware collection, and cancellation on account/profile changes.
- Navigation Compose connects authentication, the five primary destinations, forms, details, and settings subflows.
- Store money as integer minor units with an explicit ISO currency. Use decimal-safe parsing, checked arithmetic, and currency-specific precision.
- Use custom Compose Canvas charts, Coil for private local images, and WorkManager only for justified local reminders.
- Keep secrets and financial data out of logs. Disable Android automatic backup and document the application's explicit encrypted-backup workflow.

## Phase 1 — Foundation and build environment

- [ ] Verify SDK, JDK, Gradle, Android plugin, Kotlin, Compose, KSP, Room, and Hilt compatibility.
- [ ] Complete Gradle wrapper, modules, dependency pins, test runners, and build configuration.
- [ ] Establish core, data, domain, and feature package boundaries and repository interfaces.
- [ ] Configure Hilt, Room, DataStore, lifecycle handling, and Navigation Compose.
- [ ] Create a reusable design system: navy/emerald palette, typography, spacing, cards, inputs, semantic colors, light/dark/system themes.
- [ ] Finish adaptive, legacy-compatible, and monochrome launcher assets and branded splash screen; export a 1024×1024 branding PNG if supported.
- [ ] Build the empty application and verify dependency resolution before adding more feature layers.

Validation gate: debug APK builds; manifest has only justified permissions; app launches to an honest empty/welcome state. No unresolved dependency or toolchain failures.

## Phase 2 — Authentication, sessions, and privacy

- [ ] Implement validated local signup and login using name, normalized username/email, password, and confirmation.
- [ ] Use a strong salted adaptive password derivation function, securely generated salts, safe comparison, bounded inputs, and background-thread derivation. Store only derivation metadata and verifier in private storage.
- [ ] Handle duplicate identifiers, invalid credentials, throttling, change password, logout, and local account deletion safely.
- [ ] Keep authenticated session state in memory, require authentication after process restart/logout, and clear account-scoped UI state on logout.
- [ ] Implement AndroidX BiometricPrompt availability checks, enrollment/disable controls, authenticated Keystore-backed unlock, invalidated-key handling, and explicit password fallback.
- [ ] Require a successful password login before biometric enrollment; never treat failed/canceled biometrics as authentication success.
- [ ] Implement one centralized privacy manager. Fresh login and profile switching must never reveal values without authentication.
- [ ] Hide balances, amounts, charts, budgets, receipt previews, accessible semantics, and other sensitive content throughout navigation.
- [ ] Require successful biometric verification or account-password verification to reveal; failures remain hidden.
- [ ] Apply screenshot/recent-app protection and configurable background privacy locking with safe lifecycle transitions.
- [ ] Explain installation-local accounts and the absence of remote password reset. Avoid a fake recovery flow.

Validation gate: password-verifier tests and authentication flows pass; canceled/failed reveal stays hidden; backgrounding, logout, navigation, and process recreation cannot bypass privacy or authentication.

## Phase 3 — Profiles and isolated persistence

- [ ] Finalize account, profile, category, transaction, budget, and attachment entities with foreign keys, indexes, uniqueness constraints, and deliberate cascade/restrict rules.
- [ ] Export the initial Room schema and define an explicit migration policy without destructive fallback.
- [ ] Scope every read, mutation, aggregate, search, attachment, and export by authenticated account and profile. Validate ownership inside repositories, not only in UI.
- [ ] Implement create, rename, edit, delete, and switch profile; include description, avatar/icon, theme color, and currency.
- [ ] Create profile-specific default categories atomically with each profile.
- [ ] Restore the last selected profile only after validating ownership.
- [ ] Switch scopes atomically: clear rendered financial state, cancel old observers and work, reset filters/search, and reject late results from old scopes.
- [ ] Define currency-change behavior that preserves historical monetary meaning and never invents conversion rates.

Validation gate: database tests reject cross-account/profile relationships and mutations; rapid profile switching never renders another workspace's transactions, totals, budgets, charts, search results, or receipts.

## Phase 4 — Transactions, categories, and receipts

- [ ] Implement real expense and income creation, detail, edit, deletion, and persistence across restarts.
- [ ] Support required title, positive amount, category, date, currency, ownership, and timestamps; optional description, notes, payment method, tags, and attachments.
- [ ] Add validated forms, useful keyboard actions, date picker, inline errors, loading states, duplicate-submit protection, and destructive-action confirmations.
- [ ] Implement transaction history with date grouping, search, category/type/date/amount filters, sort controls, and bounded/paginated queries with lazy rendering.
- [ ] Implement custom category creation/editing, icon/color selection, and explicit reassignment when deleting categories with financial records. Handle budget reassignment conflicts atomically.
- [ ] Implement Photo Picker and secure camera capture, private file import, bounded decoding, compression, thumbnails, preview, removal, and FileProvider grants.
- [ ] Keep attachments account/profile scoped and preserve them during transaction editing.
- [ ] Clean up canceled imports and orphaned files; handle missing/corrupt images, storage exhaustion, permission denial, and process restarts.

Validation gate: expense/income CRUD and filtering work against Room; edit preserves receipts; category reassignment never deletes transactions; inaccessible or corrupt images produce useful errors.

## Phase 5 — Dashboard, budgets, and analytics

- [ ] Build the dashboard header, profile switcher, prominent privacy eye control, summary cards, recent activity, and quick actions.
- [ ] Calculate balance as income minus expenses; calculate monthly spending and remaining budget without treating budgets as income.
- [ ] Implement overall monthly and category budgets with create/edit/delete, uniqueness rules, progress, remaining amounts, and overspending indicators.
- [ ] Use transaction dates and category scope for budget calculations; distinguish overall and category budgets to avoid double counting.
- [ ] Implement week/month/last-month/year/custom-range filters and range validation.
- [ ] Add category distribution, spending trend, income-versus-expense, and budget-versus-actual charts and highest-spending summaries.
- [ ] Prevent hidden chart geometry, labels, semantics, or progress from revealing financial values.
- [ ] Use efficient aggregate queries and reactive updates. Add a memory cache only if measurement justifies it, with account/profile/range/filter keys and explicit invalidation.

Validation gate: summaries and budget calculations match independent fixtures; edits immediately refresh summaries; every report is profile scoped; hidden state masks charts and all financial summaries.

## Phase 6 — Settings, export, backup, and reminders

- [ ] Connect account information, password change, logout, profiles, categories, security, appearance, and preference settings.
- [ ] Persist theme, automatic privacy lock, screenshot protection, first weekday, date format, and validated active-profile preferences with DataStore.
- [ ] Implement profile CSV and JSON export through Storage Access Framework, with proper quoting, explicit sensitive-data authorization, and spreadsheet formula-injection handling.
- [ ] Define a versioned full-account backup format including profiles, categories, transactions, budgets, receipts, relationships, and relevant preferences.
- [ ] Encrypt backups with authenticated encryption and a separately derived backup-passphrase key; use fresh salts/nonces and validate bounded inputs.
- [ ] Validate backup version, authentication, lengths, duplicate IDs, currency/amount/date values, ownership, references, and attachment paths before mutation.
- [ ] Restore with explicit confirmation, transactional database writes, staged attachment files, safe account/profile ID mapping, and no silent overwrite.
- [ ] Explain backup-passphrase loss, credential loss, device/Keystore limitations, export confidentiality, and database-at-rest limitations accurately.
- [ ] Add daily expense and monthly budget-review reminders using local scheduling, opt-in notification permission, and generic notification text without amounts.
- [ ] Confirm destructive profile-data and account-data deletion and remove associated private receipt files.

Validation gate: CSV/JSON output contains only the authorized profile; encrypted backup round-trips all supported data; wrong passwords, tampering, malformed archives, and interrupted restoration leave existing data intact.

## Phase 7 — Polish, security review, and delivery

- [ ] Refine responsive layouts, empty states, navigation/back behavior, forms, loading/errors, and subtle transitions.
- [ ] Verify screen-reader semantics, privacy-safe content descriptions, touch targets, contrast, font scaling, and reduced-motion behavior.
- [ ] Review main-thread work, query indexes, image memory, large histories, repeated taps, rapid switching, lifecycle races, and process restoration.
- [ ] Run unit, database, UI, lint, and build checks; diagnose and fix failures without disabling assertions or substituting trivial checks.
- [ ] Add README build/run/test instructions and a security/backup limitations document.
- [ ] Save tested reusable environment installation/startup instructions where needed; distinguish saved configuration from publication and runtime validation.
- [ ] Review changes for credentials, generated outputs, unnecessary permissions, placeholders, fake data, unfinished core flows, and unintended attribution.
- [ ] Record exact verification outcomes and remaining limitations. Do not claim production readiness or complete acceptance for unexecuted scenarios.

## Verification matrix

| Area | Required evidence |
| --- | --- |
| Money and dates | Currency precision including zero/three-decimal currencies, invalid/negative/overflow amounts, checked sums, month/year boundaries, custom range validation |
| Authentication | Signup validation, unique normalized identifier, correct/incorrect password, salted verifier, password changes, logout/session reset, failed/canceled biometric fallback |
| Persistence and isolation | Foreign-key enforcement, scoped reads and writes, update ownership, category reassignment, cascades, aggregate isolation, validated active profile |
| Privacy | Default hidden state; all destinations masked; reveal only after authentication; canceled/invalid reveal hidden; screenshot/background protection; accessible semantics do not leak values |
| Financial behavior | Expense/income CRUD, balance, budgets, date/category filters, analytics, reactive updates, pagination |
| Attachments | Private storage, cross-profile rejection, thumbnail/receipt round-trip, removal, corrupt/missing files, failed imports and orphan cleanup |
| Portability | Profile export isolation, CSV quoting/formula handling, encrypted backup round-trip, wrong password/tamper rejection, malformed references, atomic restore |
| UI | Signup/login, create profile, add/edit expense, switch profile, privacy controls, navigation, validation, confirmations, empty/error states |
| Delivery | Reproducible Gradle build, unit tests, Room/instrumentation tests, Compose UI tests, lint, documented actual outcomes |

Instrumentation and device-only behavior must be tested on an emulator or physical Android device when available. If this environment cannot run one, record those checks as unrun and provide runnable commands; a successful JVM test or APK build is not a substitute.

## Acceptance walkthroughs

1. **Persistence:** Install, create an account and profile, add an expense, force-stop, reopen, authenticate, and verify the saved record.
2. **Isolation:** Create Personal and Friend profiles with different records. Switch repeatedly, including during loading/search, and verify no cross-profile values appear even briefly.
3. **Privacy:** Hide values on Home, visit every destination and receipt detail, inspect accessibility output, cancel biometric reveal, enter an incorrect password, then authenticate successfully. Values remain hidden until the final success.
4. **Financial correctness:** Add income and expenses across categories and dates; verify balance, ranges, category budgets, overall budget, edits, deletion, and reports.
5. **Data portability:** Export one profile; create an encrypted full backup with receipts; reject tampering and wrong passphrases; restore safely with explicit confirmation and consistent relationships.
6. **Offline and lifecycle:** Use airplane mode, background/foreground, process recreation, missing receipts, and denied permissions without crashes, authentication bypass, or silent data loss.

## Working rules

- Continue phase by phase using reasonable technical decisions; ask only for missing information that materially blocks progress.
- Use the existing isolated checkout rather than creating a worktree.
- Preserve user files and do not log or commit credentials.
- Never weaken TLS, dependency checksums, authentication, or privacy to make a check pass.
- Keep production screens connected to real local state, with no fake balances, placeholder actions, or hardcoded authentication success.
- Use Subhan Adeel <ms1640070@gmail.com> for any authorized commits. Do not add assistant attribution or co-author trailers.
- Do not interpret a local configuration save, successful read-only Git operation, or generated APK as proof of publication, push access, or device-tested behavior.
