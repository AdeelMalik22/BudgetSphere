# BudgetSphere

Native offline personal expense and budget management for Android 8.0+ (API 26).

## Development

Install JDK 17 or 21 and Android SDK platform 36 and build-tools 36.0.0. Configure `ANDROID_HOME` or an ignored `local.properties` with `sdk.dir=/path/to/android-sdk`.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

The last command requires an Android emulator or connected device. No runtime Internet access is required. The build downloads pinned dependencies from Google Maven and Maven Central.

See [plan.md](plan.md) for the implementation sequence, acceptance criteria, and current verification status. This repository is under active implementation; individual commits do not imply all features are complete.
