# Security and recovery model

BudgetSphere is an offline application. It has no Internet permission, remote identity provider, analytics, advertisements, or remote password-reset service. A username/email is only a local identifier, not a verified email address.

## Passwords and sessions

Passwords are verified using PBKDF2-HMAC-SHA256 with a fresh 32-byte SecureRandom salt, 600,000 iterations, and a 256-bit result. Verification uses constant-time byte comparison. Password derivation runs off the UI thread. Mutable password arrays and key material are cleared after use; JVM/Compose input strings cannot be guaranteed to disappear immediately from memory.

Verifiers and derivation metadata live in the app-private Room database. Password verifiers are not exposed in presentation-layer account models. Login credentials are not stored in DataStore. Authenticated sessions are memory-only and do not survive process death or logout. Session-generation and cancellation checks prevent authentication work finishing after logout from restoring a session. Repeated incorrect attempts trigger a temporary in-process delay; this is not a tamper-resistant protection against an attacker with filesystem access.

Accounts and subprofiles are local to the installation. Subprofiles share the authenticated owner's access and are not separate security principals with independent passwords.

## Biometrics and financial privacy

Biometrics use AndroidX BiometricPrompt with strong biometric authentication and an Android Keystore AES-GCM key requiring authentication for each use. Enrollment requires the current account password. Encrypted account-bound unlock material is stored locally; fingerprint images and biometric templates are never read or stored by the application. New biometric enrollment can invalidate the key. Password changes disable biometric unlock, and the user must enroll again.

The central privacy manager grants visibility only after verification for the current session/profile generation. Profile switching, login, logout, and hiding invalidate earlier challenges. Failed or canceled authentication never grants visibility. Password fallback is explicit.

Financial text, chart geometry, budget progress, receipt previews, and hidden screen-reader semantics are masked while locked. Amount-entry forms require visibility first. Values default to hidden after login and profile switching; background auto-hide is enabled by default. Screenshot protection is enabled by default, always enforced while values are hidden, and reinforced for recent-app previews. Device/OEM behavior still requires physical-device verification.

Exports and backup/restore require an explicit decision while unlocked. A one-use, account/profile-bound authorization lets the already-approved document operation finish after Android's file picker backgrounds the activity; it does not unlock the app or authorize another operation.

## Local data and ownership

Financial records and receipts reside in private app storage. Room composite ownership keys and foreign keys enforce account/profile relationships. Repository operations validate authenticated ownership before reads or mutations. Scoped observers and navigation state are replaced on profile changes; derived ledger state is rebuilt from Room emissions.

The Room database and receipt files are **not independently encrypted at rest by this application**. They rely on Android application sandboxing and device filesystem encryption. A rooted/compromised device, privileged malware, or an attacker with decrypted filesystem access is outside this protection model. Local login does not encrypt the entire database with the user's password.

Android automatic cloud backup and device transfer are excluded. Use the application's explicit encrypted backups. Clearing app storage or uninstalling removes local accounts and data. No destructive Room migration fallback is configured.

## Backups and exports

Encrypted backups use a versioned envelope with PBKDF2-HMAC-SHA256 (600,000 iterations, independent 32-byte random salt) and AES-256-GCM with a fresh 12-byte nonce. The header is authenticated as additional data. A separate passphrase of 12–256 characters protects the backup. Never reuse a nonce/key pair.

Backups preserve profiles, categories, transactions, budgets, receipt images/thumbnails, and supported appearance/privacy preferences. Password verifiers, login sessions, device biometric keys, Android notification permissions, and reminder schedules are not exported. Format version 1 is supported; encrypted input is bounded. Backups are not authenticated until GCM verification succeeds.

Restoration requires an authenticated local account and explicit confirmation. It creates new profile/record IDs and validates references, duplicate identifiers, monetary/date/currency values, preferences, and receipt references before committing database changes. Existing profiles are retained. Receipt files are staged in newly generated private directories and removed if restoration fails before database commit. Preference restoration is a separate atomic DataStore edit; a storage failure is reported separately after successful data restoration.

CSV/JSON exports are **unencrypted**. They contain the active profile's data and should be saved only to trusted destinations. CSV cells are quoted and text that could start a spreadsheet formula is prefixed to prevent execution. Receipt image bytes are included only in encrypted account backups.

A backup and its passphrase allow data restoration into a new local account after loss of the original installation. They do not restore biometric enrollment or recover the original login password. Losing local access and the backup passphrase makes recovery impossible.

## Verification boundaries

Read [docs/verification.md](docs/verification.md) for exact automated checks and remaining device-only validation. Successful JVM tests do not establish hardware biometric behavior, OEM screenshot behavior, or complete resistance to a compromised operating system. This implementation has not undergone an independent security audit.
