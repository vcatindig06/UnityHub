# UnityHub

Native Android worship-team app built with Kotlin and Jetpack Compose.

## Platform
- Java 17
- Minimum Android 8.0 (API 26)
- Supabase authentication and database

## Supabase project
The Android app is configured for the **UnityHub** Supabase project:
- Project ref: `jbcxqeuvzdwkaetaipln`
- Project URL: `https://jbcxqeuvzdwkaetaipln.supabase.co`

## Local Supabase setup
Copy `local.properties.example` to `local.properties` in the project root. Keep any existing `sdk.dir` line. Set `SUPABASE_URL` to the UnityHub URL above and replace `YOUR_UNITYHUB_PUBLISHABLE_KEY` with the publishable key from the UnityHub project's API Keys page. Do not commit `local.properties`.

Add `unityhub://auth` to Supabase Dashboard → Authentication → URL Configuration → Redirect URLs for email confirmation callbacks.

Use only the Supabase publishable key in this Android app. Never place a service-role or secret key in the client.

## Database status
The UnityHub Supabase project was newly created and currently has no public tables or recorded migrations. Authentication can be connected, but organization, membership, songs, setlists, and other data features require the UnityHub schema, RLS policies, and database functions to be designed and applied before use. Do not point this app at the former WorshipFlow project.

The app currently contains the Compose dashboard shell and email/password sign-in and registration screens. Organization onboarding and role-aware access are the next implementation stage.
