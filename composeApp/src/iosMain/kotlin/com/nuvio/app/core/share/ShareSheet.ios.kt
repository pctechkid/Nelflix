package com.nuvio.app.core.share

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

actual object ShareSheet {
    actual fun shareText(
        title: String,
        text: String,
    ) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val presenter = root.topPresentedViewController()
        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}

private tailrec fun UIViewController.topPresentedViewController(): UIViewController =
    presentedViewController?.topPresentedViewController() ?: this
