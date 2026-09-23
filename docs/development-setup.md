# Development setup

MedTrack currently supports development with synthetic data only. Do not load
real patient information until the protection, backup, and recovery milestone
has been completed and reviewed.

## Requirements

- JDK 17 or newer (Android Studio's bundled JDK is enough; a system JDK also works)
- Android Studio with Android SDK Platform 35
- Android SDK Build Tools and Platform Tools
- An Android emulator or device running API 26 or newer for device tests

If you build from a terminal outside Android Studio, set `JAVA_HOME` to a JDK
and make sure `java` is on `PATH`. Create an untracked `local.properties` file
in the repository root:

```properties
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

The app builds and its manual workflows run without a model key. The Android
client talks to the MedTrack gateway (mock in debug by default). Provider
credentials belong on the backend only:

```properties
medtrack.gateway.baseUrl=http://10.0.2.2:8088
medtrack.gateway.mock=false
```

Backend OpenRouter key (never in the APK):

```
OPENROUTER_API_KEY=YOUR_SERVER_KEY
```

The local MCP HTTP server is disabled by default. To enable it in a debug build
for synthetic-data testing only, add:

```properties
medtrack.localMcp.enabled=true
```

To seed an empty debug database with the 20-patient synthetic fixture on
startup, add:

```properties
medtrack.seedSyntheticData=true
```

Release builds ignore both flags and keep the server and seeder disabled. Never
commit `local.properties`; it is excluded by `.gitignore`.

## Firebase

The app uses Firebase project `medtrack-6497f` via `apps/android/google-services.json`.
The `com.medtrack.app` client includes an Android OAuth client (debug SHA-1)
and a web client used as `GOOGLE_WEB_CLIENT_ID` for Credential Manager.

Gradle setup follows Firebase’s Android instructions:

- Root `build.gradle.kts`: `id("com.google.gms.google-services") version "4.5.0" apply false`
- App `build.gradle.kts`: `id("com.google.gms.google-services")`
- Firebase BoM `34.19.0` with Analytics and Auth

Kotlin is on 2.2.21 so BoM 34.x compiles; KSP1 is forced with `ksp.useKSP2=false`.

Analytics must not log patient names, diagnoses, or other clinical content. Google
Sign-In and encrypted local storage are required before real-patient use (MT-012).

Google Sign-In uses the web OAuth client from `google-services.json`, selected
structurally for `com.medtrack.app`. Override only if needed:

```properties
medtrack.google.webClientId=YOUR_WEB_CLIENT_ID
```

The gateway verifies Firebase ID token signatures (RS256, issuer, audience,
subject, and expiry). Development `mt-dev.` tokens are rejected when
`MEDTRACK_ENV=production`. Production startup fails if
`MEDTRACK_ALLOW_DEV_TOKENS=true`. Device replacement is explicit
(`replaceExisting`); ordinary login does not revoke another device.

Debug builds still offer the synthetic Ankita path. Release builds require Google
sign-in and stay locked across restart until Google re-auth or device-credential
unlock of a previously verified Google owner. Enable the Google provider in
Firebase Authentication.

## Reproducible commands

From PowerShell in the repository root:

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

The debug APK is produced under `apps\android\build\outputs\apk\debug\`.

With an emulator or device connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Synthetic fixture

`apps/android/src/debug/java/com/medtrack/app/testdata/SyntheticDataset.kt` defines a
deterministic set of 20 fictional patients with duplicate names, unique rooms,
active and discharged records, visits, medicines, task states, attachment
metadata, and follow-ups.

`SyntheticDataSeeder` (debug only) writes those records into an empty database
inside a Room transaction and creates minimal valid PDF files under
`files/reports/visit_<id>/`, which FileProvider exports. Enable seeding with
`medtrack.seedSyntheticData=true`, or call the seeder from instrumentation
tests. Fixture unit tests live in `src/testDebug` so release unit-test
compilation does not depend on debug-only sources.

Safeguard coverage lives in:

- `apps/android/src/test/.../SafeguardRegressionTest.kt`
- `apps/android/src/testDebug/.../SyntheticDatasetTest.kt`
- `apps/android/src/androidTest/.../SafeguardInstrumentedTest.kt`

## Database safety

Room is intentionally configured without destructive migration fallback. If an
installed database has an unsupported schema, startup must fail visibly instead
of replacing the database. Formal migrations begin in MT-002.
