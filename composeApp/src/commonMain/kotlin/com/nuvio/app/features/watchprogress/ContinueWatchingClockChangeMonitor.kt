package com.nuvio.app.features.watchprogress

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal object ContinueWatchingClockChangeMonitor {
    private val mutableEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val events: SharedFlow<Unit> = mutableEvents.asSharedFlow()

    fun notifyClockChanged() {
        mutableEvents.tryEmit(Unit)
    }
}
