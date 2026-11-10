# ClubhouseLinksGooglePlay

Native **Android / Kotlin / Jetpack Compose** port of the [Clubhouse Links iOS app](https://github.com/evanalmighty84/ClubhouseLinksAppStore). The original SwiftUI repository is unchanged. Both platforms target the existing Heroku resident/vendor APIs and shared Clubhouse Links PostgreSQL backend.

> **Development / QA only. Not ready to publish to Google Play.**
> The existing account-specific API endpoints currently require a review and upgrade to server-enforced authenticated sessions and authorization checks. An Android client must not rely on a numeric vendor or resident ID, a phone number, or a local preference as an authorization mechanism.

## Implemented Android screens

- Native navigation: Home, Events, Services, Requests, Account, Street Fair.
- SMS verification using the existing iOS API contract; both `success=true` and `verified=true` are required before continuing.
- Resident signup with first name, last name, address, invite code, and verified phone.
- Verified-phone vendor account lookup and local account switch/sign-out.
- Community events fetched for the current resident (no fabricated events).
- Resident vendor directory and service-request submission using the same payload as iOS.
- Vendor service-request inbox, full pagination, new/in-progress/completed filtering, and viewed/accepted/declined/completed status actions.
- Resident request-history screen, resident and vendor profile details, resident completed-project readout.
- Street Fair screen linking community activities and vendor services.
- Firebase Messaging service **scaffold**, notification channel, and pending FCM token persistence. No background token upload or iOS APNs token reuse.
- GitHub Actions debug-APK workflow and Codemagic debug/release-AAB workflows (release signing not configured).

## Reused iOS API routes

All routes are under
`https://crm-function-app-5d4de511071d.herokuapp.com/server/resident_function/api/`.

| Android feature | API route |
| --- | --- |
| SMS request/check | `POST residents/send-verification`, `POST residents/check-verification` |
| Resident signup | `POST residents/signup` |
| Vendor lookup | `POST vendors/login` |
| Resident directory | `GET residents/vendors/:residentId` |
| Resident events | `GET residents/events/:residentId` |
| Board events | `GET hoaBoardMembers/:residentId/events` (API available, board role gating remains to be added) |
| Resident service request | `POST residents/:residentId/service-requests` |
| Resident request history | `GET residents/:residentId/service-requests` |
| Vendor request inbox | `GET vendors/:vendorId/service-requests?limit=100&offset=...` |
| Vendor request actions | `PATCH vendors/:vendorId/service-requests/:requestId/viewed` and `.../status` |
| Resident profile/projects | `GET residents/profile/:id`, `GET residents/completed-projects/:id` |
| Vendor profile | `GET vendors/:vendorId/profile` |

**Do not test the mutating operations against production accounts unless you intend to change live data.** Endpoints were matched to the Swift source; live server contracts and responses have not yet been validated from an Android device.

## Android Studio

1. Open the repo as a Gradle project in Android Studio.
2. Use Java 17 and Android SDK 35; let Gradle sync.
3. Build/run the `app` debug variant on an emulator or Android device.
4. GitHub Actions creates a debug APK artifact on successful build.
5. No secrets or release keystores should be committed.

Build configuration: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, target SDK 35, min SDK 26. The GitHub Actions workflow uses Gradle 8.10.2.

## Firebase Cloud Messaging: setup remaining

1. Create a Firebase Android app with package `com.clubhouselinks.android`.
2. Download `google-services.json` to `app/` **locally** and add the Google Services Gradle plugin/configuration as required by your Firebase setup.
3. Implement a backend device-token table/registration route for **FCM**, separate from APNs. Authenticate the account before accepting tokens, validate ownership, implement token rotation and logout removal.
4. Allow `POST_NOTIFICATIONS` permission on Android 13+ and test foreground/background behavior.
5. Configure the notification deep links and privacy disclosures.

Current `ClubhouseMessagingService` provides a basic notification channel and stores an unregistered FCM token locally; it **does not guarantee production push notifications**.

## Production release blockers

- [ ] **Server authentication/authorization:** backend-issued session/token, expired-session handling, access control on ALL resident/vendor endpoints, secure refresh/sign-out, rate limiting, and account data separation.
- [ ] **Real device API tests:** signup, phone verification, approved/pending neighborhood behavior, vendor lookup, events, service requests, status changes, history, profiles.
- [ ] **FCM integration:** Firebase configuration, Android token registration on server, notifications permission, deep links, sender testing.
- [ ] **Feature parity:** native vendor profile edits and logo uploads; HOA board event management; resident address autocomplete and maps/geocoding; street fair-specific vendor/event data; completed-project uploading; resident notifications and neighborhood feed.
- [ ] **User experience:** native app icon, branding assets, accessibility and tablet layouts, error/empty states, offline behavior.
- [ ] **Release signing:** keystore, Play App Signing, AAB upload, version policy, privacy policy, Data Safety declarations, Play closed testing requirements.
- [ ] **Security:** repo is currently publicly visible on GitHub; never commit credentials, keystores, Firebase service-account credentials or private signing files.

## CI and building

`main` triggers `.github/workflows/android-ci.yml`, which installs Gradle and builds a debug APK in GitHub Actions. See the **Actions** tab for current status and artifacts. `codemagic.yaml` provides a parallel Linux build configuration.

## Source of truth

iOS: `evanalmighty84/ClubhouseLinksAppStore` (SwiftUI, XcodeGen, Codemagic).
Backend: `evanalmighty84/ClubhouseLinks` (Heroku resident/CRM functions).
Android: this repo. Maintain feature parity by comparing actual iOS API payloads and server routes before porting each new screen.
