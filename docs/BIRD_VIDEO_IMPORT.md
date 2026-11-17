# Copy original iOS bird videos to Android

The original iOS bird animations are MP4s stored under
`ClubhouseResident/Resources`, not in the iOS `Assets.xcassets` image folder.

The Android resident home will automatically play these videos when the files
are present under `app/src/main/res/raw`:

| iOS file | Android filename | Feature |
| --- | --- | --- |
| `clubhouse_bird_announcements.mp4` | `clubhouse_bird_announcements.mp4` | Neighborhood updates introduction |
| `clubhouse-bird-newproject.mp4` | `clubhouse_bird_newproject.mp4` | Start a Project card |

Run from the **Android repository** terminal on the Mac where the iOS project is
cloned:

```bash
SRC="$HOME/Downloads/files/ClubhouseResident/ClubhouseResident/Resources"
DST="$PWD/app/src/main/res/raw"
mkdir -p "$DST"
cp "$SRC/clubhouse_bird_announcements.mp4" "$DST/clubhouse_bird_announcements.mp4"
cp "$SRC/clubhouse-bird-newproject.mp4" "$DST/clubhouse_bird_newproject.mp4"
ls -lh "$DST"
git add app/src/main/res/raw
git commit -m "assets: port original iOS bird videos to Android"
git push origin main
```

If your local iOS Resources directory is elsewhere, use the actual full
Resources path. Android resource filenames must contain only lowercase
letters, digits and underscores.

The Android screen falls back to existing
`clubhouse_bird_wave_sprite_512.png` when a video is missing.
The MP4s stay local in the app and do not transmit customer data.
