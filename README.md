# Archery Shot Counter — Companion

![Archery Shot Counter](assets/banner.png)

The phone companion for the [Archery Shot Counter watch app](https://github.com/Vemestael/ArcheryShotCounter-wearos), a Wear OS app that automatically counts archery shots via the wrist accelerometer. This app receives your training sessions from the watch, keeps a full local history, and lets you browse, edit, back up and restore your data from your phone.

It extends the watch app rather than working on its own — shots are detected on the watch. Part of the [Archery Shot Counter](https://github.com/Vemestael/ArcheryShotCounter) project.

## Screenshots

<img src="assets/history.png" width="200" alt="Session history"> <img src="assets/detail.png" width="200" alt="Per-shot detail"> <img src="assets/edit.png" width="200" alt="Edit session"> <img src="assets/themes.png" width="200" alt="Theme picker">

Six colour themes, the same as on the watch:

<img src="assets/themes-strip.png" alt="Brass, Forest, Target, Steel, Neon and Parchment themes">

## Features

- **Session history** — every session synced from the watch, with date, time range, duration, shot count and shots per end
- **Live session tracking** — a session still in progress on the watch shows up immediately with a live indicator and shot count, updating in real time
- **Per-shot detail** — tap a session to see the time and impact strength of every recorded shot
- **Edit sessions** — tap the pencil to change start/end time, shot count or shots per end; edits sync back to the watch
- **Year grouping** — older sessions are grouped under a year label as your history grows
- **Two-way sync** — pull-to-refresh or the sync button reconciles with the watch in both directions, deletions included
- **Export / import** — back up or restore your full history as JSON or CSV
- **Clear data** — wipe local data on the phone without affecting the watch
- **Six colour themes** — the same palettes as the watch (Brass, Forest, Target, Steel, Neon, Parchment)
- **12 interface languages** — English, Russian, Spanish, French, German, Portuguese, Chinese, Japanese, Korean, Arabic, Turkish and Hindi
- **No accounts, no servers** — data only moves between your own watch and phone

## Requirements

- Android 8.0+ (API 26+)
- Google Play services (for the Wearable Data Layer API)
- The [watch app](https://github.com/Vemestael/ArcheryShotCounter-wearos) installed and paired to sync sessions from

## Installation

Download the latest APK from the [Releases](https://github.com/Vemestael/ArcheryShotCounter-android/releases) page and install it on your phone.

```bash
adb install ArcheryShotCounterCompanion-<version>.apk
```

Or copy the APK to the phone and open it directly — you'll need to allow installs from the source the first time.

## How it works

The app talks to the watch through the Wear OS [Data Layer API](https://developer.android.com/training/wearables/data/data-layer). The watch pushes each session as a `DataItem` on every recorded shot, plus a lightweight marker for whichever session is currently in progress — the phone's live listener picks these up while the app is open, and a manual sync (button or pull-to-refresh) reconciles everything, including sessions recorded while the phone was offline.

## Building from source

```bash
git clone https://github.com/Vemestael/ArcheryShotCounter-android.git
cd ArcheryShotCounter-android
./gradlew assembleDebug
```

For a signed release build, create `keystore.properties` in the project root (not committed):

```properties
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then run:

```bash
./gradlew assembleRelease
```

## License

MIT — see [LICENSE](LICENSE).
