# UnityHub

Native Android worship-team app built with Kotlin and Jetpack Compose.

## Platform
- Java 17
- Minimum Android 8.0 (API 26)
- Supabase authentication

## Local Supabase setup
Copy `local.properties.example` to `local.properties` in the project root. Keep any existing `sdk.dir` line. Add your Supabase Project URL and publishable key using the variable names shown in the example. Do not commit `local.properties`.

Add `unityhub://auth` to Supabase Dashboard → Authentication → URL Configuration → Redirect URLs for email confirmation callbacks.

Use only the Supabase publishable key in this Android app. Never place a service-role or secret key in the client.

The app currently contains the Compose dashboard shell and email/password sign-in and registration screens. Organization onboarding and role-aware access are the next implementation stage. Android build verification is still pending in Code on the Go.
