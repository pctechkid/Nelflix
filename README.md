# Nelflix

> Ronnel's independent Android media app, built with Kotlin Multiplatform and Compose Multiplatform.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-full%20build-3DDC84?logo=android&logoColor=white)](composeApp)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](composeApp)
[![Latest release](https://img.shields.io/github/v/release/pctechkid/Nelflix?label=release)](https://github.com/pctechkid/Nelflix/releases/latest)

Nelflix is a personalized streaming companion focused on fast discovery, addon-powered catalogs, smooth internal playback, and sync-friendly watch flows. It keeps the app experience self-contained with an internal `libmpv`/mpv player, Android downloads, profile-aware settings, and a GitHub Releases updater.

## ✨ Highlights

- 🎬 **Internal MPV playback** with subtitle styling, audio/subtitle track controls, chapters, skip intro/outro support, and tuned mpv defaults.
- 🧩 **Stremio-compatible addons** for catalogs, metadata, streams, and subtitles.
- 🔎 **Discovery and search** powered by installed addons, TMDB enrichment, and custom collection sources.
- 👤 **Profiles and sync flows** for watch progress, history, library behavior, and addon preferences.
- ✅ **Trakt integration** for library, watch progress, comments, scrobbling, lists, and watchlist workflows.
- 📥 **Android downloads** with live notification progress.
- 🛠️ **Plugin runtime support** through the full build flavor.
- 🚀 **In-app updater** backed by GitHub Releases.
- 🧾 **Debug log export** for easier crash and playback troubleshooting.

## 📱 Android Builds

Useful Gradle commands:

```bash
./gradlew :composeApp:assembleFullDebug
./gradlew :composeApp:assembleFullRelease
```

Generated APK paths:

```text
composeApp/build/outputs/apk/full/debug/composeApp-full-debug.apk
composeApp/build/outputs/apk/full/release/composeApp-full-release.apk
```

The Android package ID is:

```text
com.nelfix.ronnel
```

> Note: Kotlin source packages still live under `com.nuvio.app` to avoid a risky source-wide package migration. The installable Android identity is `com.nelfix.ronnel`.

## ⚙️ Local Configuration

Create or update `local.properties` for optional services and release tooling:

```properties
UPDATE_GITHUB_OWNER=your-github-user
UPDATE_GITHUB_REPO=Nelflix
UPDATE_GITHUB_API_BASE_URL=https://api.github.com

TRAKT_CLIENT_ID=your-trakt-client-id
TRAKT_CLIENT_SECRET=your-trakt-client-secret
TRAKT_REDIRECT_URI=nelflix://auth/trakt
```

Release signing values are read from local properties when present:

```properties
NUVIO_RELEASE_STORE_FILE=path/to/keystore.jks
NUVIO_RELEASE_STORE_PASSWORD=...
NUVIO_RELEASE_KEY_ALIAS=...
NUVIO_RELEASE_KEY_PASSWORD=...
```

Version metadata is shared through:

```text
iosApp/Configuration/Version.xcconfig
```

## 🚢 Releases

Android updates are published through GitHub Releases.

- Latest release: [github.com/pctechkid/Nelflix/releases/latest](https://github.com/pctechkid/Nelflix/releases/latest)
- Release notes and asset naming: [Docs/RELEASES.md](Docs/RELEASES.md)

Recommended APK asset naming format:

```text
Nelflix-full-release-vX.Y.Z-versionCodeNN.apk
```

## 🧱 Project Layout

```text
composeApp/      Kotlin Multiplatform app, Android build, shared Compose UI
iosApp/          iOS host/configuration files
Docs/            Release notes and addon reference material
scripts/         Helper scripts
vendor/          Vendored dependencies and supporting code
MPVKit/          MPVKit-related dependency source
libass-android/  Android subtitle rendering dependency
```

## 🧪 Development Notes

- Prefer focused changes and test playback paths carefully.
- Keep release builds minified because that is the real updater/distribution path.
- For MPV-related changes, verify both debug and release builds when possible.
- For stream or addon behavior, test with fresh addon refreshes and reused cached links.

## 🤝 Contributing

Contributions, bug reports, and focused fixes are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening larger changes, especially anything that affects playback, sync, navigation, settings, addons, or app direction.

## 📄 License

Nelflix is licensed under the [GNU General Public License v3.0](LICENSE).

