# UnityHub Firebase Cloud Messaging setup

The FCM Android client and server delivery endpoint are included in this version. Push delivery remains disabled until a Firebase Android app and Firebase service-account credential are configured. The existing Supabase in-app Notifications screen continues to work without Firebase.

## 1. Register the Android app with Firebase

1. Open the Firebase Console: https://console.firebase.google.com/
2. Create a Firebase project for UnityHub or select an existing one.
3. Add an Android app.
4. Enter this exact Android package name: `com.unityhub.app`.
5. Complete registration and open **Project settings → General**.

## 2. Add the Android app configuration to GitHub Actions

From the registered Android app's `google-services.json`, copy these values into **GitHub → UnityHub → Settings → Secrets and variables → Actions → New repository secret**:

| Secret name | Value in `google-services.json` |
| --- | --- |
| `FIREBASE_API_KEY` | `client[0].api_key[0].current_key` |
| `FIREBASE_APPLICATION_ID` | `client[0].client_info.mobilesdk_app_id` |
| `FIREBASE_PROJECT_ID` | `project_info.project_id` |
| `FIREBASE_SENDER_ID` | `project_info.project_number` |

For local Android Studio builds, add those four values to the root `local.properties` file. Preserve the existing `sdk.dir`, `SUPABASE_URL`, and `SUPABASE_PUBLISHABLE_KEY` values. Do not commit `local.properties` or `google-services.json`.

These four values are Firebase Android client configuration. They are not the Firebase server private key. The Gradle build allows them to be blank so it can still compile before Firebase setup is complete; FCM initialization is skipped when any required value is missing.

## 3. Create the Firebase server credential

1. In Firebase Console, open **Project settings → Cloud Messaging** and make sure the Firebase Cloud Messaging API (HTTP v1) is enabled.
2. Open **Project settings → Service accounts** and generate a private key for a service account dedicated to UnityHub message delivery.
3. In Google Cloud IAM, grant the service account the permission needed to send Firebase Cloud Messaging messages (the Firebase Cloud Messaging API Admin role).
4. Keep the downloaded service-account JSON private. Never place it in the Android project, the APK, or GitHub source.

## 4. Store the service credential in Supabase

1. Open the UnityHub Supabase project: https://supabase.com/dashboard/project/jbcxqeuvzdwkaetaipln
2. Go to **Edge Functions → Secrets**.
3. Add a secret named exactly `FIREBASE_SERVICE_ACCOUNT_JSON`.
4. Paste the complete contents of the Firebase service-account JSON file as its value, then save it.

The deployed `send-notification-push` Edge Function reads this secret at runtime. It uses the private key server-side to obtain a short-lived Google OAuth token and send FCM HTTP v1 messages. Do not put this credential in a GitHub Actions variable or the Android app.

## 5. Build and install

After the four Firebase Android configuration values have been saved as GitHub repository secrets and `FIREBASE_SERVICE_ACCOUNT_JSON` has been saved in Supabase:

1. Open the repository's **Actions** tab.
2. Select **Android build**.
3. Choose **Run workflow** on the `main` branch.
4. Download and install the resulting `unityhub-debug-apk` on a physical Android device with Google Play services.
5. Sign in to UnityHub. On Android 13 or newer, allow the notification permission when prompted.

The app registers its FCM token against the signed-in Supabase user. It removes that device token from the account when the user signs out.

## 6. Expected push events

- A newly created service assignment notifies the assigned team member.
- A confirmed or declined assignment notifies the person who assigned the role.
- A generated pending-assignment reminder notifies the assigned team member.

The in-app Notifications screen remains available as the durable notification history. Push delivery is an additional alert channel.

## Security notes

- The Android app contains only the Firebase Android client configuration and the Supabase publishable key.
- The Firebase service-account JSON is stored only as a Supabase Edge Function secret.
- FCM device tokens are stored per signed-in user in `public.device_push_tokens`, protected by Row Level Security.
- The database webhook request is authenticated with an HMAC secret stored in Supabase Vault.
- Never add Supabase service-role/secret keys, Firebase service-account JSON, signing keys, or `local.properties` to source control.
