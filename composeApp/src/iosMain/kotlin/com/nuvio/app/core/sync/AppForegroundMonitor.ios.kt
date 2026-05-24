package com.nuvio.app.core.sync

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationStateActive

internal actual object AppForegroundMonitor {
    actual fun events(): Flow<Unit> = callbackFlow {
        if (UIApplication.sharedApplication.applicationState == UIApplicationStateActive) {
            trySend(Unit)
        }

        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            trySend(Unit)
        }

        awaitClose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
}
