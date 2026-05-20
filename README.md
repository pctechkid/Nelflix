# Nelflix

Nelflix is Ronnel's independent Android media app built with Kotlin Multiplatform and Compose Multiplatform.

The app uses an internal libmpv/mpv playback engine, supports Stremio-compatible addons, preserves watch progress/history flows, and includes Android downloads and an in-app GitHub Releases updater.

## Android Builds

Useful commands:

```bash
./gradlew :composeApp:assembleFullDebug
./gradlew :composeApp:assembleFullRelease
```

The debug APK is generated at:

```text
composeApp/build/outputs/apk/full/debug/composeApp-full-debug.apk
```

## App Identity

- App name: `Nelflix`
- Android application ID: `com.nelfix.ronnel`
- Author: Ronnel

Kotlin source packages remain under `com.nuvio.app` to avoid a risky source-wide package migration, but the installable Android package identity is `com.nelfix.ronnel`.

## Updater

The Android updater uses GitHub Releases and intentionally installs debug APK assets only. Configure the target repository in `local.properties`:

```properties
UPDATE_GITHUB_OWNER=your-github-user
UPDATE_GITHUB_REPO=Nelflix
UPDATE_GITHUB_API_BASE_URL=https://api.github.com
```

See `docs/RELEASES.md` for release asset naming and publishing notes.
