# Android resident support QA enrollment

The native Android app uses the same resident-search and switch endpoints as the iOS SupportResidentAPI. Aspen Landscaping is vendor ID **35**.

The Android QA emulator generates a 64-character identifier, but this is **not** a legitimate APNs or FCM token. The backend currently authorizes an active vendor support device based on `hoa_vendor_devices`; Android support is only accessible in DEBUG builds.

To authorize your own emulator:

1. Sign in to Aspen and tap **Switch Resident**.
2. Tap **Copy enrollment identifier**.
3. In your private Railway PostgreSQL console, insert that value in the following SQL as indicated. Do not share that identifier in chat, screenshots, logs or Git.
4. Inspect the returned row before committing.

```sql
BEGIN;

INSERT INTO hoa_vendor_devices (
  vendor_id, device_token, platform, app_bundle_id,
  apns_environment, active, support_device, created_at, updated_at
)
SELECT
  35, 'REPLACE_WITH_COPIED_IDENTIFIER', 'android', 'com.clubhouselinks.android',
  'development', TRUE, TRUE, NOW(), NOW()
WHERE EXISTS (
  SELECT 1 FROM hoa_vendors
  WHERE id = 35 AND active = TRUE AND can_support_residents = TRUE
)
ON CONFLICT (device_token)
DO UPDATE SET
  active = TRUE,
  support_device = TRUE,
  updated_at = NOW()
WHERE hoa_vendor_devices.vendor_id = 35
  AND hoa_vendor_devices.platform = 'android'
RETURNING id, vendor_id, platform, active, support_device;

-- COMMIT; after reviewing the returned row
-- ROLLBACK; if the row is unexpected
```

5. In Android, tap **Check authorization again**, search a resident, select the real profile, and exit with **Exit Support**.

For the Android QA support switch, deploy backend commit `0a59595` in
`evanalmighty84/ClubhouseLinks` to Heroku first. This prevents a QA Android
identifier from being inserted into iOS's APNs resident push subscription table.
A GitHub commit alone does **not** guarantee the Heroku deployment happened.

Device identification alone is not production-grade authentication. Maintain
debug-only restrictions until secure server-issued sessions and audit logs are added.
