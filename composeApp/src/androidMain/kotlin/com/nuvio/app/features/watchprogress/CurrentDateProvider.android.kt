package com.nuvio.app.features.watchprogress

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual object CurrentDateProvider {
    actual fun todayIsoDate(): String = LocalDate.now().toString()
    actual fun currentLocalIsoDateTime(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

