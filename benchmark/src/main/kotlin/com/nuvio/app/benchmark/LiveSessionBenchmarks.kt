package com.nuvio.app.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.ArtMetric
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiDevice
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class LiveSessionBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val metrics = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ArtMetric(),
    )

    @Test
    fun playerSeekAndPanels() {
        Assume.assumeTrue(benchmarkArguments.getString("nelflix.livePlayer") == "true")
        val device = UiDevice.getInstance(instrumentation)
        Assume.assumeTrue(device.currentPackageName == TargetPackage)
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = metrics,
            compilationMode = CompilationMode.Ignore(),
            iterations = DefaultIterations,
        ) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            device.waitForIdle(300)
            val y = (device.displayHeight * 0.80f).toInt()
            device.swipe(
                (device.displayWidth * 0.25f).toInt(),
                y,
                (device.displayWidth * 0.70f).toInt(),
                y,
                30,
            )
            device.tapLabel("Subs", 2_000L)
            device.pressBack()
            device.tapLabel("Audio", 2_000L)
            device.pressBack()
        }
    }

    @Test
    fun watchTogetherSteadyStateAndCorrection() {
        val role = requireArgument("nelflix.watchTogetherRole")
        Assume.assumeTrue(role == "host" || role == "joiner")
        val device = UiDevice.getInstance(instrumentation)
        Assume.assumeTrue(device.currentPackageName == TargetPackage)
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = metrics,
            compilationMode = CompilationMode.Ignore(),
            iterations = DefaultIterations,
        ) {
            if (role == "joiner") {
                val centerY = device.displayHeight / 2
                device.swipe(
                    (device.displayWidth * 0.45f).toInt(),
                    centerY,
                    (device.displayWidth * 0.72f).toInt(),
                    centerY,
                    20,
                )
            }
            SystemClock.sleep(10_000L)
        }
    }
}
