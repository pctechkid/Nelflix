# Nelflix MPV Android build

Nelflix uses a custom build of `mpv-android-lib` 0.1.9 for Android subtitle
rendering. The build normalizes ASS/SSA bold and italic style flags and inline
overrides to regular text. All other libass behavior remains native, including
BorderStyle 3 opaque boxes, event timing, and positioning tags such as `\pos`,
`\move`, and `\an`. This covers embedded MKV tracks without extracting or
rewriting subtitle files and without delaying playback.

Sources:

- mpv Android tag: `abdallahmehiz/mpv-android@v0.1.9`
- mpv revision: `7653cc8f096e41d39697280986f4e75534a12988`
- libass revision: `338fd2cea8ac156a910b04838b5f40b868e41160`
- libplacebo revision: `f1cc9dd8e70027b6b150cac359fdc2d259b5b0b2`
- dav1d revision: `0558c332ca3563248969be0b754de553a187369d`
- build fork: `pctechkid/mpv-android`
- build branch: `codex/opaque-box-style-runs`
- build commit: `3215a31c8319040df1d56425c6df3fb2fd55fcb2`

The patch is stored in `libass-disable-bold-italic.patch`. The resulting
universal AAR contains `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`, and is
stored at `composeApp/libs/mpv-android-lib-nelflix-0.1.9.aar`.
