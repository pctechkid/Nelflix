package com.nuvio.app.benchmark

import android.app.Instrumentation
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assume

internal const val TargetPackage = "com.nelfix.ronnel"
internal const val DefaultIterations = 5
internal const val DefaultDetailsUri = "nelflix://meta?type=series&id=tt19064770"
internal const val AppReadyTimeoutMs = 20_000L

internal val instrumentation: Instrumentation
    get() = InstrumentationRegistry.getInstrumentation()

internal val benchmarkArguments: Bundle
    get() = InstrumentationRegistry.getArguments()

internal fun UiDevice.waitForNelflix(timeoutMs: Long = AppReadyTimeoutMs) {
    wait(Until.hasObject(By.pkg(TargetPackage).depth(0)), timeoutMs)
    waitForIdle()
}

internal fun UiDevice.flingVertically(repetitions: Int = 5) {
    val centerX = displayWidth / 2
    val startY = (displayHeight * 0.82f).toInt()
    val endY = (displayHeight * 0.20f).toInt()
    repeat(repetitions) {
        swipe(centerX, startY, centerX, endY, 18)
        waitForIdle(300)
    }
}

internal fun UiDevice.tapLabel(label: String, timeoutMs: Long = 10_000L): Boolean {
    val selector = Until.findObject(By.desc(label))
    val target = wait(selector, timeoutMs)
        ?: wait(Until.findObject(By.text(label)), 1_000L)
        ?: wait(Until.findObject(By.textContains(label)), 1_000L)
        ?: return false
    target.click()
    waitForIdle()
    return true
}

internal fun requireArgument(name: String): String {
    val value = benchmarkArguments.getString(name)?.trim().orEmpty()
    Assume.assumeTrue("Missing instrumentation argument: $name", value.isNotEmpty())
    return value
}

internal fun assumeFixtureRows(expectedRows: Int) {
    val configuredRows = benchmarkArguments.getString("nelflix.watchedRows")?.toIntOrNull()
    Assume.assumeTrue(
        "Prepare a benchmark account with $expectedRows watched rows and pass nelflix.watchedRows=$expectedRows",
        configuredRows == expectedRows,
    )
}

internal fun UiDevice.flingElementByDescription(description: String, repetitions: Int = 4) {
    val element = wait(Until.findObject(By.desc(description)), 10_000L)
        ?: wait(Until.findObject(By.text(description)), 1_000L)
    Assume.assumeNotNull(element)
    repeat(repetitions) {
        element!!.fling(Direction.DOWN)
        waitForIdle(300)
    }
}
