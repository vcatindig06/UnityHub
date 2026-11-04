# UnityHub Firebase Cloud Messaging setup

UnityHub's Android FCM client and Supabase push-delivery Edge Function are integrated. The Android client configuration is now included in the repository as a four-value public Firebase config file. Live push delivery still requires a Firebase service-account private key to be added to Supabase Edge Function secrets.

## 1. Registered Firebase Android app

The uploaded `google-services.json` matches the UnityHub Android application:

- Firebase project ID: `unityhub-19f9e`
- Firebase Android app ID: `1:901794877137:android:8b67948ff8af80cc127ff0`
- Android package: `com.unityhub.app`

The four client values are stored in the repository root file `firebase-config.properties`:

- `FIREBASE_API_KEY`
- `FIREBASE_APPLICATION_ID`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

The Gradle build reads this file automatically. Environment variables and local `local.properties` values override the checked-in values when present. This means GitHub Actions no longer needs four Firebase client secrets just to build the APK.

Firebase documents that Firebase-service API keys are public project/app identifiers, not authorization credentials, and are safe in app configuration. Keep the key limited to Firebase-related APIs; do not add unrelated Google APIs (especially the Gemini Developer API) to its allowlist. Firebase Security Rules and App Check—not API-key secrecy—protect Firebase data.

## 2. Enable Firebase Cloud Messaging HTTP v1

1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Select project `unityhub-19f9e`.
3. Open **Project settings → Cloud Messaging**.
4. Confirm that the Firebase Cloud Messaging API (HTTP v1) is enabled for the project.

## 3. Create a server-side Firebase service-account credential

This is separate from `google-services.json` and must never be placed in Android source code, the APK, or GitHub.

1. In Firebase Console, open **Project settings → Service accounts**.
2. Generate a new private key for a service account dedicated to UnityHub push delivery.
3. In Google Cloud IAM, ensure that service account has the **Firebase Cloud Messaging API Admin** role on project `unityhub-19f9e`.
4. Keep the downloaded service-account JSON private. Do not commit it, send it in chat, or add it to GitHub Actions.

## 4. Add the service-account JSON to Supabase

Open the [UnityHub Supabase Edge Function Secrets page](https://supabase.com/dashboard/project/jbcxqeuvzdwkaetaipln/functions/secrets).

Add this secret:

- Key: `FIREBASE_SERVICE_ACCOUNT_JSON`
- Value: the complete JSON contents of the Firebase service-account key file

Save the secret. The deployed `send-notification-push` function reads it at runtime, exchanges the private key for a short-lived Google OAuth access token, and sends FCM HTTP v1 messages. The value must remain server-side in Supabase.

Supabase also supports setting Edge Function secrets using the Supabase CLI:

```bash
supabase secrets set --project-ref jbcxqeuvzdwkaetaipln FIREBASE_SERVICE_ACCOUNT_JSON="$(cat /secure/path/service-account.json)"
```

Run that command only in a trusted local terminal. Do not paste the JSON into shell history, a source file, a repository, or chat. Prefer the Supabase Dashboard secret form when possible.

## 5. Build and install

The repository now contains the public Firebase Android values, so the next GitHub Actions build will generate Firebase initialization resources automatically.

1. Open the repository's **Actions** tab.
2. Select **Android build** and run it on the `main` branch, or wait for the configuration commit's automatic build.
3. Download the `unityhub-debug-apk` artifact.
4. Install it on a physical Android device with Google Play services.
5. Sign in to UnityHub and allow notification permission on Android 13 or newer.

After sign-in, the app registers its FCM token against the current Supabase user. It removes that device token from the account when the user signs out.

## 6. Expected push events

- A newly created service assignment notifies the assigned team member.
- A confirmed or declined assignment notifies the person who assigned the role.
- A generated pending-assignment reminder notifies the assigned team member.

The in-app Notifications screen remains the durable notification history. Push delivery is an additional alert channel.

## Security notes

- The Firebase Android API key in `firebase-config.properties` is public client configuration, not a service-account credential. Firebase's API-key guidance: https://firebase.google.com/docs/projects/api-keys
- Keep the Firebase service-account JSON only in Supabase Edge Function secrets.
- Never put Supabase service-role/secret keys, the Firebase service-account JSON, signing keys, or `local.properties` in source control.
- FCM device tokens are stored per signed-in user in `public.device_push_tokens`, protected by Row Level Security.
- The database webhook request is authenticated with an HMAC secret stored in Supabase Vault.
