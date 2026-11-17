# Account role and home Street View

## Live role source

Account Settings gets the account type from existing authenticated UI selection,
then reads the actual backend profile:

- Resident: `GET /server/resident_function/api/residents/profile/:residentId`
- HOA board: resident profile `board_of_directors=true` -> **HOA Administrator**
- Vendor: `GET /server/resident_function/api/vendors/:vendorId/profile`

Support mode reads the selected resident profile temporarily; the original vendor session is preserved. It stays read-only.

## Android home street-level imagery

The resident profile already returns `address`, `latitude`, and `longitude`.
Android now uses native **Google Street View** via the Maps SDK for Android, as an analogue of the iOS `AppleLookAroundCard`. If coordinates are missing, Android attempts geocoding of the saved address. If no street panorama is available, it shows a fallback message.

Street View may show a road or another nearby viewpoint, not the home's front facade. It needs Google Play services, Street View coverage, a Google Cloud project with enabled billing, the Maps SDK for Android enabled, and an API key.

### Provisioning

1. Open Google Cloud Console > Google Maps Platform > API & Services / Credentials.
2. Enable **Maps SDK for Android**; attach a billing account.
3. Create an API key restricted to **Maps SDK for Android** and restrict Android applications by the actual app package and SHA-1 signing certificate fingerprints:
   - App ID: `com.clubhouselinks.android`
   - Add the fingerprint for the **Codemagic QA APK** signing certificate used to test.
   - Add the **production signing** fingerprint before rolling out to Play.
4. In Codemagic app/environment group settings, configure the secret environment variable `GOOGLE_MAPS_API_KEY`. Do **not** commit the key, enter it in chat, or put it in source control.
5. Run a fresh build: the Gradle script injects the key into the Android manifest and enables the native panorama card automatically.

If no key is configured, QA still builds and the resident Account Settings shows a message instead of Street View. Vendor accounts do not display a residential street-level image.

## iOS counterpart

`ClubhouseResident/Sources/Views/AccountSettingsView.swift` displays account type using the existing profile flag, plus `AppleLookAroundCard` for a nonblank resident address. No Google API key is used on iOS.

## Security and privacy

Avoid displaying residential home imagery to people who are not authorized to view that resident's profile. The current backend API is not yet secured by server-issued sessions, so production must add account-based authorization before exposing private profile addresses broadly. Support-mode browsing should remain restricted and audited.
