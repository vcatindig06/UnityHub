# UnityHub in-app updates

UnityHub checks a version manifest hosted in the public `unityhub-updates` Supabase Storage bucket. The Android app constructs download URLs only from the configured Supabase project URL and a validated relative release path. It does not send users to GitHub or open an external browser for the APK.

## What the update center does

- Checks the installed `versionCode` against the latest published release and prompts when a newer build exists.
- Shows installed and latest version numbers, an automatic-download preference, and the complete available release-note history in Account settings.
- Displays a What's New dialog after an update, covering every release newer than the last version whose notes were viewed.
- Can automatically download a new APK in the background when enabled. Android still displays its own install confirmation; ordinary Android apps cannot silently replace themselves.
- Verifies the release path, file size, SHA-256 digest, package name, version code/name, and signing certificate before opening Android's installer.
- Uses a FileProvider URI. The downloaded APK is stored in the app's private files directory and is not exposed through a public filesystem path.

The update bucket is public for downloads because it contains only published app binaries and their release manifest. Upload access is restricted to the GitHub Actions publisher using a Supabase service-role key kept exclusively as a CI secret. Never put that key in the Android app, repository, or a public config file.

## One-time distribution setup

The in-app updater requires a persistent signing key. Android rejects an update if the new APK is signed by a different certificate than the installed app.

1. Generate and securely back up a UnityHub signing keystore on a trusted computer. Example:

   ```bash
   keytool -genkeypair -v -keystore unityhub-release.jks -alias unityhub -keyalg RSA -keysize 2048 -validity 10000
   ```

   Keep the keystore and passwords in a secure password manager or encrypted backup. If the key is lost, existing installations cannot receive signed in-place updates from a replacement key.

2. Convert the keystore to a single-line Base64 value on a trusted computer:

   ```bash
   base64 -w 0 unityhub-release.jks
   ```

   On macOS, use `base64 < unityhub-release.jks | tr -d '\\n'`.

3. Add these repository secrets under **GitHub → UnityHub → Settings → Secrets and variables → Actions**:

   | Secret name | Value |
   |---|---|
   | `UNITYHUB_KEYSTORE_BASE64` | The Base64 output from step 2 |
   | `UNITYHUB_KEYSTORE_PASSWORD` | Keystore password |
   | `UNITYHUB_KEY_ALIAS` | `unityhub` (or the alias you created) |
   | `UNITYHUB_KEY_PASSWORD` | Password for that key |
   | `SUPABASE_SERVICE_ROLE_KEY` | The service-role key for the UnityHub Supabase project |

   Keep the existing `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` Actions secrets too. The service-role key is used only by the publishing workflow and is never compiled into the app.

4. Push a version-bumped commit to `main`. The Android workflow runs unit tests and builds the APK. On a push to `main`, when both the persistent signing key and Supabase service-role secret are configured, CI creates or updates the public `unityhub-updates` bucket, uploads the signed APK, then publishes `manifest.json` last. Pull-request builds never publish updates.

If the signing or service-role secrets are absent, CI still runs the normal test/build and uploads its debug artifact, but it deliberately skips in-app publication. The app will not invent a GitHub fallback.

## Important first-install transition

Version 0.30.0 predates the in-app updater, so the first installation of version 0.31.0 must come from a trusted UnityHub APK build artifact. After that, new versions are downloaded from UnityHub's Supabase update service, not GitHub.

The previously distributed 0.30.0 test APK was built on a hosted CI runner without a persistent signing key. A hosted runner's temporary debug certificate is not a stable release identity. If the installed APK and the first persistently signed update use different certificates, UnityHub downloads and verifies the new package, then opens Android's file picker so you can save it to Downloads. Sync your online team data first, uninstall the older app, and install the saved APK. Uninstalling clears local offline caches and any unsynchronized device-only data. After the persistently signed build is installed, later updates can be installed in place.

The initial 0.31.0 app will only be able to check for updates after CI has published a manifest. Configure the required signing and Supabase secrets before the merge build when possible. If they are configured later, publish a subsequent version-bumped release; the installed updater will download that release and guide you through the one-time signer transition if needed.

Android may require the user to allow **Install unknown apps** for UnityHub. This is an OS-level approval, followed by Android's normal confirmation screen. The auto-download option does not bypass either step.

## Publishing a release

1. Increase both `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add the new release entry to `app-updates/release-notes.json`, using the same `versionCode` and `versionName`. Write concise user-facing notes in the `changes` array.
3. Raise `minimumSupportedVersionCode` only when older installations must be blocked. It is 1 by default, so updates are optional.
4. Merge to `main`. The CI workflow computes the SHA-256 and size from the exact APK it built, uploads that APK to Supabase Storage, then uploads the generated manifest.

Do not manually edit Supabase's `storage.objects` metadata table. File operations must go through the Storage API.
