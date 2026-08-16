package com.nuvio.app.features.player

internal class PlayerSurfaceSourceGate<T> {
    private var surfaceAttached = false
    private var pendingSource: T? = null

    @Synchronized
    fun offer(source: T): T? {
        if (surfaceAttached) return source
        pendingSource = source
        return null
    }

    @Synchronized
    fun onSurfaceAttached(): T? {
        surfaceAttached = true
        return pendingSource.also { pendingSource = null }
    }

    @Synchronized
    fun onSurfaceDetached() {
        surfaceAttached = false
    }
}
