package com.nuvio.app.benchmark

import androidx.benchmark.macro.ArtMetric
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ColdStartupBenchmark : StartupBenchmark(StartupMode.COLD)

@LargeTest
@RunWith(AndroidJUnit4::class)
class WarmStartupBenchmark : StartupBenchmark(StartupMode.WARM)

@OptIn(ExperimentalMetricApi::class)
abstract class StartupBenchmark(
    private val startupMode: StartupMode,
) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() {
        val device = UiDevice.getInstance(instrumentation)
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
                ArtMetric(),
            ),
            compilationMode = CompilationMode.None(),
            startupMode = startupMode,
            iterations = DefaultIterations,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            device.waitForNelflix()
        }
    }
}
