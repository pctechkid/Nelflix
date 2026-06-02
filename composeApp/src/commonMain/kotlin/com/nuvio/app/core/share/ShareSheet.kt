package com.nuvio.app.core.share

expect object ShareSheet {
    fun shareText(
        title: String,
        text: String,
    )
}
