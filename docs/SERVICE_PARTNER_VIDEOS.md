# Service Partner videos on Android and iOS

Both native apps now replace the *Neighborhood Projects* media area with a
partner video when a specific business is picked in the **Vendor** filter.
Selecting **All Vendors** restores the neighborhood photo / project gallery.

## Video naming convention

One optional MP4 per business, using the same filename stem across platforms:

`service_partner_<vendor_name_in_lowercase_underscores>.mp4`

Examples:

| Selected vendor | MP4 name |
| --- | --- |
| City roofing by Design | `service_partner_city_roofing_by_design.mp4` |
| W3 Electric | `service_partner_w3_electric.mp4` |
| ASPEN LANDSCAPING | `service_partner_aspen_landscaping.mp4` |
| Antonio's Mobile Dent Repair | `service_partner_antonio_s_mobile_dent_repair.mp4` |

Normalize punctuation and spaces to underscores, collapse repeats, and remove
leading/trailing underscores. The names are derived from each vendor's real
`company_name` in the API response, not database ID.

**Until a vendor-specific file exists:**
- iOS uses `ClubhouseResident/Resources/clubhouse-bird-newproject.mp4`
- Android uses `app/src/main/res/raw/clubhouse_bird_newproject.mp4`

The screen displays **Sample Service Partner Video** to avoid implying this
generic demonstration is the vendor's actual work.

## Install a vendor-specific MP4

iOS source folder:

`ClubhouseResident/Resources/`

Android source folder:

`app/src/main/res/raw/`

For iOS, also ensure the newly added video is included in the target's Copy
Bundle Resources build phase / XcodeGen resource configuration. The current
bundled generic video is already available.

For example, if you are in the Android repo and want to give City roofing by
Design a custom clip:

```bash
cp "/path/to/city-roofing-demo.mp4" \
  "app/src/main/res/raw/service_partner_city_roofing_by_design.mp4"
git add app/src/main/res/raw
git commit -m "assets: add City roofing partner video"
git pull --rebase origin main
git push origin main
```

For iOS, put the same clip at:

`ClubhouseResident/Resources/service_partner_city_roofing_by_design.mp4`

In both apps, the video is muted and loops automatically when selected. Avoid
customer information or imagery unless there is authorization to use it.
Keep clips short and compressed because bundling one MP4 per vendor increases
APK/IPA sizes. A backend-hosted URL per vendor will scale better if the partner
library grows.
