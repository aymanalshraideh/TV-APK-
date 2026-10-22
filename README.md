# Noor TV

An Android TV app built for the Haier LE55B8600TUN (Android 6.0, 1 GB RAM, D-pad remote). Arabic, right-to-left UI.

## Features

- **Holy Quran:** every reciter and riwaya from mp3quran.net, with search, letter filter and favorites. Plays in the background, moves on to the next surah, and offers "continue listening" on launch.
- **Prayer times:** all Jordanian cities via aladhan.com, using the Jordan Ministry of Awqaf method (switchable). Shows a live countdown to the next prayer, supports per-prayer minute offsets, and caches the whole month for offline use.
- **Adhan:** at each prayer time, the app pauses whatever is playing and streams a Quran radio station for 5 minutes (station and duration set in Settings). It then resumes what was playing.
- **Jordan radio:** Jordanian stations from radio-browser.info, with Quran stations listed first. Includes search, favorites and the last played station.
- **Classic cartoons (80s/90s):** playlists from official YouTube channels such as Spacetoon via the YouTube Data API. Episodes open in SmartTube or any installed YouTube client. Also includes a personal library from USB or internal storage with resume and `.srt` subtitles.

## Download

Every push to `main` builds a release APK with GitHub Actions:

- **Latest APK:** the `apk` branch → `NoorTV.apk`
- Or: the **Actions** tab → latest run → `NoorTV-apk` artifact

## Install on the TV

- Over ADB: enable Developer options and ADB on the TV, then run
  `adb connect <TV_IP>:5555` and `adb install -r NoorTV.apk`
- Or copy the APK to a USB stick, enable "Unknown sources", and install it from a file manager.

## Build locally

1. Open the project in Android Studio (Ladybug or newer). Accept the Gradle wrapper prompt if it appears.
2. Optional: add `youtube.apiKey=YOUR_KEY` to `local.properties`.
3. Run `gradle assembleRelease`. The APK is written to `app/build/outputs/apk/release/`.

## Customization

- **Extra radio stations:** `app/src/main/assets/stations_jo.json`. Set each stream URL in `url`; entries with an empty `url` are ignored.
- **Official cartoon channels:** `app/src/main/assets/classic_channels.json`. Use `handle` (e.g. `@spacetoonyoutube`) or `channelId`, plus `displayName` and `category`.
- **YouTube API key:** enter it in the app's Settings, or set the `YOUTUBE_API_KEY` repository secret for CI builds.

## Technical notes

- `minSdk 23`, Jetpack Compose for TV, Media3 (ExoPlayer + MediaSession), OkHttp, kotlinx.serialization, Coil.
- Bundles the ISRG Root X1 certificate, because Android 6 does not trust Let's Encrypt certificates.
- Uses `java.time` through core library desugaring.
- Settings and favorites are stored in SharedPreferences. API responses are cached on disk for offline use.
- **Signing:** the release build is signed with the debug key so it can be sideloaded directly. Replace it with your own keystore before publishing anywhere.
