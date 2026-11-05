# UnityHub

UnityHub is a native Android application for worship-team coordination, song management, and service planning.

## Development environment

Use **Android Studio stable** as the primary IDE. The repository is a standard Gradle Android project and includes a Gradle Wrapper.

- Language: Kotlin
- UI: Jetpack Compose and Material 3
- Architecture direction: MVVM with clear data, domain, and presentation boundaries
- Minimum Android version: Android 8.0 (API 26)
- Compile SDK: 37.0 preview platform (needed by current stable Compose dependencies)\n- Target SDK: 36 (runtime behavior will be raised only after compatibility testing)
- Java toolchain: Java 17
- Android Gradle Plugin: 9.3.1
- Kotlin: 2.4.21
- Gradle Wrapper: 9.7.0
- Compose BOM: 2026.09.00
- Backend: Supabase Auth and PostgreSQL
- Supabase Kotlin SDK: 3.8.0
- Ktor: 3.4.3

All build-tool and library versions are pinned. Avoid dynamic dependency versions.

## Open the project

1. Install the current stable Android Studio release.
2. Clone this repository.
3. In Android Studio, choose **Open** and select the UnityHub repository folder.
4. Allow Gradle sync to finish.
5. Connect an Android device or start an emulator, then run the app configuration.

Android Studio creates or updates the local properties file for the Android SDK. Do not commit that file.

## Supabase project

UnityHub uses the existing Supabase project:

- Project ref: jbcxqeuvzdwkaetaipln
- Project URL: https://jbcxqeuvzdwkaetaipln.supabase.co

### Local configuration

Add these entries to the root local.properties file, preserving the sdk.dir line created by Android Studio:

    SUPABASE_URL=https://jbcxqeuvzdwkaetaipln.supabase.co
    SUPABASE_PUBLISHABLE_KEY=YOUR_UNITYHUB_PUBLISHABLE_KEY

Use only the Supabase publishable key in this Android app. Never use a service-role or secret key in a mobile client.

Add `unityhub://auth` and `unityhub://auth?mode=recovery` to Supabase Dashboard → Authentication → URL Configuration → Redirect URLs. The first handles email confirmation and OAuth callbacks; the second returns password-reset links to the in-app password update screen.

### GitHub Actions configuration

For builds on GitHub-hosted runners, configure these repository secrets under **Settings → Secrets and variables → Actions**:

- SUPABASE_URL: https://jbcxqeuvzdwkaetaipln.supabase.co
- SUPABASE_PUBLISHABLE_KEY: the UnityHub project's publishable key.

Gradle reads the values from environment variables in CI and from local.properties during local development. The workflow never writes or commits local Supabase configuration.

## Build and test

Linux / macOS:

    ./gradlew clean testDebugUnitTest assembleDebug

Windows PowerShell:

    .\gradlew.bat clean testDebugUnitTest assembleDebug

The debug APK is generated at:

    app/build/outputs/apk/debug/app-debug.apk

GitHub Actions runs the same test and build tasks and publishes the APK as a workflow artifact.

## Current application foundation

Implemented workflows include:

- Email/password registration, sign-in, and sign-out
- Profile display name captured during registration
- Organization creation
- Authenticated organization directory search
- Requests to join an organization
- Administrator approval or decline of join requests
- Team membership list and role display
- Song library search and editor-only song management
- Raw ChordPro song content editing and file import/export
- Read-only offline song and ChordPro chart access using a user-scoped Room cache

The app uses Supabase Row Level Security for authorization. Authorization decisions must remain enforced by database policies and trusted server-side functions; hiding controls in the Android UI is not a security boundary.

## Planned engineering direction

- Separate domain logic from Compose screens and Supabase data access.
- Extend the Room offline cache to service plans and setlists.
- Add WorkManager for reliable background synchronization and safe queued edits.
- Add automated unit, integration, and UI tests as workflows are implemented.
- Add observability, accessibility checks, and release signing before production distribution.

Do not add service-role keys, signing keys, keystores, or local SDK configuration to the repository.
