# Nelflix performance benchmarks

This module profiles the `fullBenchmark` Android build. That build uses the
release optimizer and resource shrinking, is not debuggable, and is profileable
by shell only. It must not be used as evidence when an emulator or a debug APK
is involved.

## Device preparation

1. Use a physical representative phone or Xiaomi Pad 7 with thermal state,
   battery level, network, brightness, and enabled services recorded.
2. Connect the device with ADB and confirm it with `adb devices -l`.
3. Disable battery saver and leave the device unplugged after charging.
4. Select and record each supported refresh-rate mode (60, 90, or 120 Hz).
5. Sign in manually with a dedicated benchmark account. Never pass credentials
   through instrumentation arguments or store them in this repository.
6. Prepare separate deterministic accounts or snapshots with 500, 5,000, and
   20,000 watched rows. Pass only the expected row count to the benchmark.
7. Close unrelated applications and let the device reach a stable temperature
   before every five-run comparison.

## Build checks

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :benchmark:assembleFullBenchmarkBenchmark
.\gradlew.bat :composeApp:assembleFullBenchmark
```

Generate Compose compiler reports without changing normal builds:

```powershell
.\gradlew.bat :composeApp:compileFullReleaseKotlinAndroid --project-prop=nelflix.enableComposeCompilerReports=true --rerun-tasks
```

Reports are written under `composeApp/build/compose_compiler`.

## Benchmark runs

Run all ungated startup and scrolling journeys:

```powershell
.\gradlew.bat :benchmark:connectedFullBenchmarkBenchmarkAndroidTest
```

Use instrumentation arguments for fixture-dependent journeys. Examples:

```powershell
.\gradlew.bat :benchmark:connectedFullBenchmarkBenchmarkAndroidTest --project-prop=android.testInstrumentationRunnerArguments.nelflix.watchedRows=5000
.\gradlew.bat :benchmark:connectedFullBenchmarkBenchmarkAndroidTest --project-prop=android.testInstrumentationRunnerArguments.nelflix.searchQuery=benchmark-query
```

Available arguments:

| Argument | Purpose |
| --- | --- |
| `nelflix.detailsUri` | Stable details deep link; defaults to a public test title. |
| `nelflix.watchedRows` | Enables only the matching 500, 5,000, or 20,000 row fixture. |
| `nelflix.searchQuery` | Enables the search journey without recording private input. |
| `nelflix.streamDetailsUri` | Enables stream preparation for a non-private test title. |
| `nelflix.playLabel` | Overrides the localized Play accessibility label. |
| `nelflix.livePlayer=true` | Enables a manually prepared player seek/panel session. |
| `nelflix.watchTogetherRole` | Enables a manually prepared `host` or `joiner` session. |

Live player and Watch Together benchmarks deliberately attach to an already
prepared session so room codes, stream URLs, addon details, and account data do
not enter source control or test output.

## Baseline profile

After the physical-device journeys are stable, generate the Full profile with:

```powershell
.\gradlew.bat :composeApp:generateFullReleaseBaselineProfile
```

Rebuild the release APK, confirm the profile is packaged, and compare at least
five `CompilationMode.None` runs before and after. Keep the profile only when
startup and critical journeys improve without a regression elsewhere.

## Result policy

Macrobenchmark reports startup timing, frame percentiles, jank, peak memory,
and ART behavior, and emits Perfetto traces for CPU, allocation/GC, and
main-thread analysis. TTFD is reportable only after the app has a truthful
route-ready `reportFullyDrawn` signal. Network request and cache-hit counts need
explicit non-sensitive counters; do not infer them from timing or print URLs.

Record raw outputs outside source control. A valid comparison uses the same
device, refresh rate, fixtures, network conditions, build inputs, and five-run
method on both sides.
