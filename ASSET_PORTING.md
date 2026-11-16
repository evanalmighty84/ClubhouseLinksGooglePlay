# Clubhouse Links — iOS-to-Android visual asset map

The existing SwiftUI iOS application is the design source of truth:
`evanalmighty84/ClubhouseLinksAppStore`.

## Where to put images

Copy the *image files* (not the iOS `Contents.json`) into:

`app/src/main/res/drawable-nodpi/`

Use all-lowercase filenames with underscores only. Keep the original extensions.
Create the `drawable-nodpi` folder if it does not exist. Android treats these
as drawable resource names; a `.png` file called `clubhouse_logo.png`
becomes `R.drawable.clubhouse_logo`.

| iOS source file under `ClubhouseResident/Assets.xcassets/` | Android destination filename |
| --- | --- |
| `clubhouse_app_icon.imageset/clubhouse_app_icon.png` | `clubhouse_app_icon.png` |
| `clubhouse_logo.imageset/clubhouse_logo.png` | `clubhouse_logo.png` |
| `clubhouse_bird_wave_sprite_512.imageset/clubhouse_bird_wave_sprite_512.png` | `clubhouse_bird_wave_sprite_512.png` |
| `clubhouse_links_home_fallback.imageset/clubhouse_links_home_fallback.jpeg` | `clubhouse_links_home_fallback.jpeg` |
| `hoa.imageset/hoa.png` | `hoa.png` |
| `foodtruck.imageset/foodtruck.png` | `foodtruck.png` |
| `neighborhood-projects-placeholder.imageset/neighborhood-projects-placeholder.png` | `neighborhood_projects_placeholder.png` |
| `clubhouse_bird_flying_00.imageset/clubhouse_bird_flying_00.png` | `clubhouse_bird_flying_00.png` |
| `clubhouse_bird_flying_01.imageset/clubhouse_bird_flying_01.png` | `clubhouse_bird_flying_01.png` |
| `clubhouse_bird_flying_02.imageset/clubhouse_bird_flying_02.png` | `clubhouse_bird_flying_02.png` |
| `clubhouse_bird_flying_03.imageset/clubhouse_bird_flying_03.png` | `clubhouse_bird_flying_03.png` |
| `clubhouse_bird_flying_04.imageset/clubhouse_bird_flying_04.png` | `clubhouse_bird_flying_04.png` |
| `clubhouse_bird_flying_05.imageset/clubhouse_bird_flying_05.png` | `clubhouse_bird_flying_05.png` |
| `clubhouse_bird_flying_06.imageset/clubhouse_bird_flying_06.png` | `clubhouse_bird_flying_06.png` |
| `clubhouse_bird_flying_07.imageset/clubhouse_bird_flying_07.png` | `clubhouse_bird_flying_07.png` |

The static Home screen now uses `clubhouse_app_icon`,
`clubhouse_links_home_fallback`, and `foodtruck` if installed.
Missing artwork is intentionally skipped without crashing Android.

The animated bird frames are not yet wired into an Android animation.
They are provided as the exact iOS source filenames to port next.

## Quick local copy on a Mac

Assuming both repositories are cloned side by side in your working folder:

```bash
mkdir -p ClubhouseLinksGooglePlay/app/src/main/res/drawable-nodpi
SRC=ClubhouseLinksAppStore/ClubhouseResident/Assets.xcassets
DST=ClubhouseLinksGooglePlay/app/src/main/res/drawable-nodpi
cp "$SRC/clubhouse_app_icon.imageset/clubhouse_app_icon.png" "$DST/"
cp "$SRC/clubhouse_logo.imageset/clubhouse_logo.png" "$DST/"
cp "$SRC/clubhouse_bird_wave_sprite_512.imageset/clubhouse_bird_wave_sprite_512.png" "$DST/"
cp "$SRC/clubhouse_links_home_fallback.imageset/clubhouse_links_home_fallback.jpeg" "$DST/"
cp "$SRC/hoa.imageset/hoa.png" "$DST/"
cp "$SRC/foodtruck.imageset/foodtruck.png" "$DST/"
cp "$SRC/neighborhood-projects-placeholder.imageset/neighborhood-projects-placeholder.png" "$DST/neighborhood_projects_placeholder.png"
for i in 00 01 02 03 04 05 06 07; do
  cp "$SRC/clubhouse_bird_flying_$i.imageset/clubhouse_bird_flying_$i.png" "$DST/"
done
```

Commit and push the images into the Android repository. For example:

```bash
cd ClubhouseLinksGooglePlay
git add app/src/main/res/drawable-nodpi
git commit -m "assets: add original Clubhouse Links iOS artwork to Android"
git push origin main
```

## What not to upload here

- No keystores, passwords, production tokens, or `.env` files.
- No resident/customer personal photographs. Those belong behind authenticated APIs.
- Vendor logos and project photos should load from their backend URL fields,
  not be hand-copied into the application bundle.
- iOS `AppIcon.appiconset` files are not an Android launcher-icon configuration.
  Android adaptive launcher icons need their own `mipmap` assets and XML.

## Parity limitations

This native Android port currently has working SMS verification,
resident/vendor profile loading, service request endpoints and initial
events/projects screens. Exact iOS parity still requires native equivalents
for image upload, iOS support-mode authorization, HOA event authoring,
Apple Maps address suggestions (Android alternative), animation, and
platform-specific push notifications. The QA preview is isolated sample data
and is not proof of a real authenticated role switch.

The live backend must enforce authorization for all user-specific routes
before a production Android release.
