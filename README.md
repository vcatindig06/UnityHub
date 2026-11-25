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

Members can switch between all notifications and unread notifications, mark individual items as read, or mark all of their unread notifications as read. Notifications stream live through Supabase Realtime while UnityHub is open, and the Notifications tab displays a badge with the signed-in member’s unread count. Realtime reads are filtered to the signed-in recipient and remain protected by row-level security. The migration `supabase/migrations/20261124190000_enable_notifications_realtime.sql` adds the table to the `supabase_realtime` publication; `supabase/migrations/20261124200000_grant_notification_read_timestamp_update.sql` grants authenticated members column-level update access to `read_at` only, still restricted to their own rows by RLS.

### Offline service responses

Members can confirm or decline their own upcoming service assignments while offline when the assignment is available in the saved dashboard snapshot. When declining, they can include an optional explanation of up to 300 characters. Invited members can also mark a previously synced rehearsal as Available or Can't attend while offline. UnityHub stores each response on the device and labels it **Saved · waiting to sync**. A network-constrained WorkManager task submits queued service and rehearsal responses before downloading the next offline snapshot. Supabase remains the authority: each update is restricted to the assignment, organization, and current membership, and the local queue entry is removed after the server accepts the update or confirms that the response is no longer eligible. Worship leaders can review submitted decline notes in the assignment list and overview. Signing out clears unsent responses and warns before doing so. See [Offline Member Responses](docs/OFFLINE_RESPONSES.md) for scope and behavior.

### Service plan editing

Administrators and worship leaders can edit an existing service's title, date, start time, and notes from the service details screen. The edit action is hidden in read-only and offline views. Dates and times are validated before a write is sent to Supabase.

### Service plan search and status filters

The Service Planning screen supports searching service titles, dates, and notes, and filtering by Draft, Published, Completed, or Cancelled status. The same filters apply to the month calendar, week calendar, and list view. Calendar day counts and selected-day cards reflect the active filters, including when viewing the device's offline read-only service cache.

### Service run sheet

Administrators and worship leaders can set or edit a duration for each song, prayer, reading, announcement, or other service item. The Run of Show summary totals all entered durations and estimates the finish time only when every item has a duration and the service start time is set. Missing durations are counted explicitly rather than treated as zero. Duration edits use the existing service-item duration field and are retained by the existing offline cache and setlist-template copying. See [Service Run of Show](docs/SERVICE_RUN_SHEET.md) for the complete workflow.

### Live Stage

Service details include **Start Live Stage** when a setlist is available. Live Stage provides an immersive, high-contrast display for worship teams, with large lyrics, optional chord rows, per-item semitone transposition, adjustable text size, optional lyric auto-scroll with Slow / Normal / Fast speed controls, and previous/next navigation through the ordered service setlist. It keeps the phone awake and temporarily hides system bars while active, restoring them when Live Stage closes. The view also works with locally cached service setlists in offline read-only mode.

### Rehearsal management

Administrators and worship leaders can schedule rehearsals, optionally link them to an existing service plan, and invite team members. The rehearsal screen includes a monthly calendar, Upcoming / Past / All filters, and a list view. Invitees receive in-app and Android push notifications and can respond Available or Can't attend, including queuing availability from a previously synced rehearsal while offline. Rehearsal details can be handed off to the device calendar, and each member can schedule a local 1-day, 1-hour, or 15-minute reminder. Leaders can see availability and attendance response rates, complete a rehearsal, and record each invitee as Attended or Absent. The Home dashboard shows the next scheduled rehearsal. See [Rehearsal Management](docs/REHEARSALS.md) for the full workflow.

The migration `supabase/migrations/20261112022019_create_rehearsal_management.sql` creates organization-scoped rehearsal and assignment tables, row-level security policies, explicit authenticated Data API grants, and notification triggers. A member can only update their own availability; leaders manage invitations and attendance. Rehearsal invitations use the existing signed server-side push delivery path.

### Service templates

Administrators and worship leaders can save a service's setlist as a named template, browse the team's reusable templates, rename or delete them, and append a saved template to another draft or published service. Applying a template appends items after the existing setlist; it does not replace existing items. Song references, custom items, durations, notes, item order, and ChordPro snapshots are retained.

The migration `supabase/migrations/20261106125100_create_service_templates.sql` creates organization-scoped template and template-item tables. Both tables use row-level security. Organization members can read their team's templates; only administrators and worship leaders can create, rename, delete, or apply them. The Data API grants are explicit and limited to authenticated users.

### Personal song favorites

Members can save a private favorites list without changing the shared song library. The migration `supabase/migrations/20261106104500_add_personal_song_favorites.sql` creates the favorites table with authenticated-only Data API grants and row-level policies. A member can only list, add, or remove their own favorites for songs in organizations they currently belong to.

Song editing remains limited to administrators, worship leaders, and song editors while online. The Add Song form keeps its Cancel and Save actions pinned above the system navigation area so they remain accessible while the chart is scrolled.

### Ministry position management

Administrators and worship leaders can create, rename, and delete reusable ministry positions from the Team screen. Position names are validated for length and case-insensitive duplicates. Before deleting a position, UnityHub shows how many member profiles currently use it; confirming deletion removes that position from those profiles. Existing service assignment role labels are retained. See [Ministry Position Management](docs/MINISTRY_POSITIONS.md) for the workflow and data behavior.

### Team member administration

The migration `supabase/migrations/20261106090000_secure_member_administration.sql` protects the organization creator from being demoted or removed outside an ownership transfer. It adds an owner-only `transfer_organization_ownership` RPC that promotes the selected existing member to Administrator, updates `organizations.created_by`, and changes the previous owner's role to Team Member in one transaction. The role-change and removal RPCs also lock the organization first and retain the last-administrator safeguard.

Only the authenticated organization owner can transfer ownership. Only authenticated administrators can change member roles or remove non-owner members. The Android UI mirrors these restrictions, while the database functions remain the authorization boundary.

### Member profiles and participation

Team members can open a read-only profile from the Team directory. The profile shows the member's organization role, membership date, assigned ministry positions, recent service assignments, rehearsal invitations, and recorded attendance. A participation summary separates upcoming service roles, service responses, upcoming rehearsals, and completed rehearsal attendance. The profile uses existing organization-scoped membership and activity records; it does not expose email addresses or private service/rehearsal response notes. No additional database tables or client permissions are required.

See [Member Profiles](docs/MEMBER_PROFILES.md) for the data scope and behavior.

### In-app updates

UnityHub checks a version manifest and downloads verified APK updates from its own Supabase Storage bucket. The Account screen tracks the installed/latest version, offers automatic background downloading, and lists all published release notes. After an update, a What's New prompt lists every release since the user last viewed the notes. Downloads are checked against their SHA-256, file size, package identity, version, and installed signing certificate before Android's installer is launched. Android always retains the final installation approval; the app does not silently install APKs or redirect users to GitHub. See [In-app Updates](docs/APP_UPDATES.md) for setup, signing, and publishing requirements.

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

GitHub Actions runs the same test and build tasks and publishes the APK as a workflow artifact. When the stable UnityHub signing key and Supabase update-publisher secret are configured, pushes to `main` also publish the signed APK and update manifest to the private-write/public-download `unityhub-updates` Supabase Storage bucket. See [In-app Updates](docs/APP_UPDATES.md) for the one-time signing and publishing setup.

## Current application foundation

Implemented workflows include:

- Email/password registration, sign-in, and sign-out
- Profile display name captured during registration and stored in the RLS-protected public profiles table; account edits update the shared team profile
- Organization creation
- Authenticated organization directory search
- Requests to join an organization
- Administrator approval or decline of join requests
- Team directory with member display names, team access roles, and assigned ministry positions, including member search and ministry-position filters; admins and worship leaders can manage the reusable ministry position catalogue
- Administrator-only member access controls for changing access roles and removing members, plus an atomic owner transfer that promotes the new owner and retains the previous owner as a Team Member
- Song library search across titles, artists, keys, time signatures, ChordPro lyrics/chords, and song notes; sort by title, artist, or most recently updated; editor-only song management; and personal per-member favorites with an All songs / Favorites filter. Search and sorting run off the UI thread so large charts do not block typing.
- ChordPro chart reading with semitone transpose, adjustable text size, and hands-free auto-scroll with adjustable speed from a persistent bottom controller; raw ChordPro song content editing and file import/export
- Read-only offline song and ChordPro chart access using a user-scoped Room cache
- Read-only offline service plans, ordered setlists (including saved ChordPro snapshots), service assignments, and previously synced rehearsal schedules and invited rosters. Service confirmations/declines and rehearsal availability responses can be queued for later sync. Member confirmations and declines, including optional notes of up to 300 characters, can be queued offline and synchronized later without granting offline edit access to leaders or other members.
- Copy a previous service's complete ordered setlist into another service, appending items while preserving item types, notes, durations, song references, and saved ChordPro snapshots.
- A service Run of Show planner with per-item duration and notes editing, transition cues and scripture references (up to 500 characters per item), total planned runtime, missing-duration counts, and an estimated finish time when the timing information is complete. Offline assignment data is scoped to the signed-in user; assignment creation and removal remain online-only, while member responses can be queued from the Home dashboard.
- Automatic background refresh of the signed-in user's song library, service plans/setlists, service assignment snapshots, rehearsals, and rehearsal rosters. WorkManager runs after a workspace is opened and periodically when a network connection is available. It submits the signed-in member's queued service-assignment and rehearsal-availability responses before refreshing the offline snapshots; all writes remain scoped to the saved user and active organization.
- A Home dashboard offline-sync status card showing live WorkManager progress, the last successful sync time, retry/error state, and a manual Sync now action. Member Confirm / Can't serve and rehearsal Available / Can't attend responses can be saved to user-scoped local queues while offline and uploaded automatically when a validated connection returns. The dashboard labels these responses as waiting to sync until Supabase confirms the update.
- A personal Home dashboard section for the signed-in member's upcoming service assignments, with role, service date, response status, and quick Confirm / Can't serve actions.
- A Home dashboard card for the next scheduled rehearsal, linked directly to rehearsal details.
- A notification center with individual and bulk mark-as-read actions plus an All / Unread filter; rehearsal invitations and availability responses are deep-linked to rehearsal details.
- Organization-scoped rehearsal scheduling, team invitations, online and offline member availability responses, linked service plans, and leader-recorded attendance, with availability/attendance summaries and searchable, filterable rehearsal rosters.
- An administrator and worship-leader assignment overview for upcoming services, with response totals, member/role/service search, and filters for awaiting, confirmed, and unable-to-serve replies.
- A dedicated Account screen for updating the signed-in display name, reviewing workspace role and app version, managing Android notification settings, and checking UnityHub updates with an automatic-download preference and complete release history. New releases download directly from UnityHub-managed Supabase Storage; Android still requires the user to approve installation. Signing out requires confirmation before user-scoped offline copies are cleared.

The app uses Supabase Row Level Security for authorization. Authorization decisions must remain enforced by database policies and trusted server-side functions; hiding controls in the Android UI is not a security boundary.

## Planned engineering direction

- Separate domain logic from Compose screens and Supabase data access.
- Expand the offline response queue to other carefully scoped member actions only after defining conflict, cancellation, and authorization behavior for each action.
- Add automated unit, integration, and UI tests as workflows are implemented.
- Add observability, accessibility checks, and release signing before production distribution.

Do not add service-role keys, signing keys, keystores, or local SDK configuration to the repository.


### Team Chat

The Team tab includes an organization-wide text chat. Messages are limited to 1,000 characters and history is loaded in stable 50-message pages using a timestamp-plus-ID cursor, so loading older messages does not depend on an ever-growing offset. Realtime message inserts are merged into the existing history without duplicates, and the conversation preserves the reader's position unless they are already near the bottom. The Team tab shows a private unread-message count from the recipient's Team Chat notifications; messages become read as they enter the reader's viewport. Each member has one organization-scoped read cursor, and messages show how many current team members have read them. Read-state rows are protected by row-level security, members can update only their own cursor, and a database guard prevents read progress from moving backwards or pointing into another organization. Reading messages also marks only the matching recipient's Team Chat notifications as read, keeping the Notifications badge in sync.

New messages create a private in-app notification for every other current organization member and use the existing Firebase push-delivery pipeline; the sender does not receive a notification for their own message, and the message body is not included in the push preview. Tapping a chat notification opens Team Chat in the matching active workspace. The `send-notification-push` Edge Function remains the push-delivery service. The first release supports text messages only; attachments, direct messages, edits, and deletes are not included. The message composer supports the Android keyboard Send action, and a failed send keeps the unsent draft available for retry.
