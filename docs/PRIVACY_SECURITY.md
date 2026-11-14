# ZENO Android privacy and release security

## Current manifest review

The current app requests:
- `INTERNET` for remote AI/voice connectivity.
- `RECORD_AUDIO` for the explicitly enabled voice mode.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` for the visible microphone foreground service.
- `POST_NOTIFICATIONS` for the foreground-service notification on supported Android versions.

It does not request SMS, call-log, contacts, Accessibility Service, device-admin, package-installation, location, or broad `QUERY_ALL_PACKAGES` access.

## Changes in this branch

- `allowBackup=false`: app-private data is excluded from ordinary Android backup.
- `usesCleartextTraffic=false`: cleartext HTTP traffic is disallowed; use HTTPS for remote services.
- Chrome package visibility is declared narrowly so Google search can explicitly target Chrome.
- The microphone foreground service is not exported to other apps.
- App search uses launcher-intent visibility rather than broad package enumeration.
- Settings disclose that app search is local and voice audio is sent to the configured voice service while a voice session is active.
- Google search opens `https://www.google.com/search` explicitly in `com.android.chrome`; ZENO does not auto-open a result website. If Chrome is absent, it reports that instead of silently switching browsers.

## Privacy release requirements

Before public release:
1. Publish a real privacy policy identifying the operator, data categories, purpose, processors (including voice/AI providers), retention, deletion process, contact route and cross-border processing where applicable.
2. Verify the actual SDK network behavior and provider retention terms; do not promise that audio or chat is never retained unless verified contractually and technically.
3. Keep microphone capture off until the user enables voice mode. Show a persistent foreground notification while listening and stop capture immediately when disabled.
4. Request only permissions required for a feature, at the time the user invokes it.
5. Keep OAuth secrets, API keys and refresh tokens out of the APK. Store server credentials in a managed backend; encrypt refresh tokens at rest.
6. Do not add analytics, advertising SDKs, Accessibility, SMS, contacts or location access without a documented feature need and a separate privacy review.
7. Test data deletion, logout, account switching, network failures and permission revocation.

## Play Protect and APK signing

No developer can guarantee that Play Protect will never show a warning. A first-time APK installed from a browser or file-sharing app may receive an “app scan recommended” prompt simply because Google has not seen it before. Sensitive permissions and older target SDKs can trigger stronger warnings.

For a trustworthy public release:
- Use a dedicated release signing key; never ship a debug-signed APK as the public production build.
- Store the keystore and passwords in GitHub Actions encrypted secrets, never in Git.
- Keep the signing certificate stable across updates.
- Target a current Android API and test the release APK on supported Android versions.
- Prefer Google Play distribution with Play App Signing, complete the Data safety form accurately, and provide the privacy policy.
- Scan dependencies and the final APK; publish checksums and release notes.

The current GitHub Actions artifact is a debug build for testing. It is not yet a production-signed release. The above steps reduce avoidable trust warnings; they cannot override or bypass Play Protect.
