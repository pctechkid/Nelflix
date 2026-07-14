package com.nuvio.app.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = TargetPackage,
    ) {
        val device = UiDevice.getInstance(instrumentation)
        pressHome()
        startActivityAndWait()
        device.waitForNelflix()
        SystemClock.sleep(1_000L)
        device.flingVertically(4)
    }
}
