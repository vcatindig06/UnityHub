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

### Notification center

Members can switch between all notifications and unread notifications, mark individual items as read, or mark all of their unread notifications as read. Bulk updates still write each notification only for its own recipient and use the existing authenticated-only read/update row policies; no new table privileges or server-side credentials are introduced.

### Service plan editing

Administrators and worship leaders can edit an existing service's title, date, start time, and notes from the service details screen. The edit action is hidden in read-only and offline views. Dates and times are validated before a write is sent to Supabase.

### Personal song favorites

Members can save a private favorites list without changing the shared song library. The migration `supabase/migrations/20261106104500_add_personal_song_favorites.sql` creates the favorites table with authenticated-only Data API grants and row-level policies. A member can only list, add, or remove their own favorites for songs in organizations they currently belong to.

Song editing remains limited to administrators, worship leaders, and song editors while online. The Add Song form keeps its Cancel and Save actions pinned above the system navigation area so they remain accessible while the chart is scrolled.

### Team member administration

The migration `supabase/migrations/20261106090000_secure_member_administration.sql` protects the organization creator from being demoted or removed outside an ownership transfer. It adds an owner-only `transfer_organization_ownership` RPC that promotes the selected existing member to Administrator, updates `organizations.created_by`, and changes the previous owner's role to Team Member in one transaction. The role-change and removal RPCs also lock the organization first and retain the last-administrator safeguard.

Only the authenticated organization owner can transfer ownership. Only authenticated administrators can change member roles or remove non-owner members. The Android UI mirrors these restrictions, while the database functions remain the authorization boundary.

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
- Profile display name captured during registration and stored in the RLS-protected public profiles table; account edits update the shared team profile
- Organization creation
- Authenticated organization directory search
- Requests to join an organization
- Administrator approval or decline of join requests
- Team directory with member display names, team access roles, and assigned ministry positions, including member search and ministry-position filters
- Administrator-only member access controls for changing access roles and removing members, plus an atomic owner transfer that promotes the new owner and retains the previous owner as a Team Member
- Song library search, editor-only song management, and personal per-member favorites with an All songs / Favorites filter
- Raw ChordPro song content editing and file import/export
- Read-only offline song and ChordPro chart access using a user-scoped Room cache
- Read-only offline service plans, ordered setlists (including saved ChordPro snapshots), and the last-synced service team assignments with their response statuses.
- Copy a previous service's complete ordered setlist into another service, appending items while preserving item types, notes, durations, song references, and saved ChordPro snapshots. Offline assignment data is scoped to the signed-in user, and assignment creation, removal, and response updates remain online-only.
- Automatic background refresh of the signed-in user's song library, service plans/setlists, and service assignment snapshots. WorkManager runs after a workspace is opened and periodically when a network connection is available; background sync is read-only and scoped to the saved user and active organization.
- A Home dashboard offline-sync status card showing live WorkManager progress, the last successful sync time, retry/error state, and a manual Sync now action.
- A personal Home dashboard section for the signed-in member's upcoming service assignments, with role, service date, response status, and quick Confirm / Can't serve actions.
- A notification center with individual and bulk mark-as-read actions plus an All / Unread filter.
- An administrator and worship-leader assignment overview for upcoming services, with response totals, member/role/service search, and filters for awaiting, confirmed, and unable-to-serve replies.
- A dedicated Account screen for updating the signed-in display name, reviewing workspace role and app version, and managing Android notification settings. Signing out requires confirmation before user-scoped offline copies are cleared.

The app uses Supabase Row Level Security for authorization. Authorization decisions must remain enforced by database policies and trusted server-side functions; hiding controls in the Android UI is not a security boundary.

## Planned engineering direction

- Separate domain logic from Compose screens and Supabase data access.
- Continue the read-only WorkManager background refresh for offline cache data. Queued offline writes remain a separate future feature.
- Add automated unit, integration, and UI tests as workflows are implemented.
- Add observability, accessibility checks, and release signing before production distribution.

Do not add service-role keys, signing keys, keystores, or local SDK configuration to the repository.
