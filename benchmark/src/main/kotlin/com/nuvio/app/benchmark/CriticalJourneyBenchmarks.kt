package com.nuvio.app.benchmark

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.benchmark.macro.ArtMetric
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class CriticalJourneyBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val metrics = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ArtMetric(),
    )

    @Test
    fun homeFling() = measureFlingJourney()

    @Test
    fun detailsFling() = measureFlingJourney(
        launchUri = benchmarkArguments.getString("nelflix.detailsUri") ?: DefaultDetailsUri,
    )

    @Test
    fun library500Rows() = measureLibrary(500)

    @Test
    fun library5000Rows() = measureLibrary(5_000)

    @Test
    fun library20000Rows() = measureLibrary(20_000)

    @Test
    fun searchResultsFling() {
        val query = requireArgument("nelflix.searchQuery")
        val device = UiDevice.getInstance(instrumentation)
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = metrics,
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = DefaultIterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForNelflix()
                Assume.assumeTrue(device.tapLabel("Search"))
                val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000L)
                Assume.assumeNotNull(input)
                input!!.text = query
                SystemClock.sleep(2_000L)
            },
        ) {
            device.flingVertically()
        }
    }

    @Test
    fun streamPreparationAndResults() {
        val detailsUri = requireArgument("nelflix.streamDetailsUri")
        val playLabel = benchmarkArguments.getString("nelflix.playLabel") ?: "Play"
        val device = UiDevice.getInstance(instrumentation)
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = metrics,
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = DefaultIterations,
            setupBlock = {
                pressHome()
                startActivityAndWait(viewIntent(detailsUri))
                device.waitForNelflix()
            },
        ) {
            Assume.assumeTrue(device.tapLabel(playLabel))
            SystemClock.sleep(3_000L)
            device.flingVertically(3)
        }
    }

    private fun measureLibrary(expectedRows: Int) {
        assumeFixtureRows(expectedRows)
        val device = UiDevice.getInstance(instrumentation)
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = metrics,
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = DefaultIterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForNelflix()
                Assume.assumeTrue(device.tapLabel("Library"))
                SystemClock.sleep(1_000L)
            },
        ) {
            device.flingVertically(7)
        }
    }

    private fun measureFlingJourney(launchUri: String? = null) {
        val device = UiDevice.getInstance(instrumentation)
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = metrics,
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = DefaultIterations,
            setupBlock = {
                pressHome()
                if (launchUri == null) {
                    startActivityAndWait()
                } else {
                    startActivityAndWait(viewIntent(launchUri))
                }
                device.waitForNelflix()
                SystemClock.sleep(1_000L)
            },
        ) {
            device.flingVertically()
        }
    }

    private fun viewIntent(uri: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        setPackage(TargetPackage)
    }
}
