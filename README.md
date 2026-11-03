# UnityHub

Native Android worship-team app built with Kotlin and Jetpack Compose.

## Platform

- Java 17
- Minimum Android 8.0 (API 26)
- Kotlin 2.3.21 and Android Gradle Plugin 9.3.1
- Supabase Auth and PostgreSQL database

## Supabase project

The Android app is configured for the **UnityHub** Supabase project:

- Project ref: `jbcxqeuvzdwkaetaipln`
- Project URL: `https://jbcxqeuvzdwkaetaipln.supabase.co`

## Local Supabase setup

Copy `local.properties.example` to `local.properties` in the project root. Keep any existing `sdk.dir` line. Set `SUPABASE_URL` to the UnityHub URL above and replace `YOUR_UNITYHUB_PUBLISHABLE_KEY` with the publishable key from the UnityHub project's API Keys page. Do not commit `local.properties`.

Add `unityhub://auth` to Supabase Dashboard → Authentication → URL Configuration → Redirect URLs for email confirmation callbacks.

Use only the Supabase publishable key in this Android app. Never place a service-role or secret key in the client.

## Database foundation

The UnityHub Supabase project has these application tables:

- `profiles`
- `organizations`
- `organization_directory` (limited team discovery fields)
- `organization_members`
- `organization_join_requests`
- `ministry_positions`
- `member_positions`
- `songs`
- `service_plans`
- `service_plan_items`

RLS is enabled on all exposed application tables. Organization creation, join requests, administrator approval, member role changes, and member removal are handled through database RPC functions. Membership roles are read from database membership records, not user-editable profile metadata.

## Implemented Android workflows

- Email/password registration, sign-in, and sign-out
- Profile display name captured during registration
- Organization creation (creator becomes the first administrator)
- Authenticated organization directory search
- Request to join an organization
- Administrator approval or decline of join requests
- Team membership list and role display

Songs and service plans are represented in the database schema, but their Android screens are not yet implemented. Team invitations, chat, notifications, file sharing, and offline synchronization remain future work.

## Build notes

The project keeps its existing pinned Supabase Kotlin SDK and Ktor versions to reduce dependency changes in the Code on the Go environment. The Android app now includes the Supabase PostgREST module for database queries and RPC calls.

For GitHub Actions builds, add these two repository secrets under **GitHub → Settings → Secrets and variables → Actions → Repository secrets**:

- `SUPABASE_URL`: `https://jbcxqeuvzdwkaetaipln.supabase.co`
- `SUPABASE_PUBLISHABLE_KEY`: the publishable key from the UnityHub Supabase project's API Keys page.

The workflow writes these secrets into a temporary, untracked `local.properties` file on the GitHub runner before compiling. It stops the build if either secret is missing, rather than producing an APK with empty Supabase settings. Never add the publishable key to source files or commit `local.properties`.

Run a debug build with the repository's GitHub Actions workflow or from Code on the Go after syncing Gradle dependencies.
