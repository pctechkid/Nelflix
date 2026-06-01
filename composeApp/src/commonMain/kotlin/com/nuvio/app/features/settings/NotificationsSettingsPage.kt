package com.nuvio.app.features.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListScope
import coil3.compose.AsyncImage
import com.nuvio.app.features.notifications.EpisodeReleaseAlertPreview
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsUiState
import com.nuvio.app.features.watchprogress.CurrentDateProvider

private val NetflixRed = Color(0xFFE50914)
private val CalendarBackground = Color(0xFF050505)
private val CalendarCard = Color(0xFF151515)
private val CalendarCardAlt = Color(0xFF1C1C1C)

internal fun LazyListScope.notificationsSettingsContent(
    isTablet: Boolean,
    uiState: EpisodeReleaseNotificationsUiState,
) {
    item {
        ReleaseCalendarScreen(
            isTablet = isTablet,
            uiState = uiState,
        )
    }

    item {
        NotificationDeliveryCard(
            isTablet = isTablet,
            uiState = uiState,
        )
    }
}

@Composable
private fun ReleaseCalendarScreen(
    isTablet: Boolean,
    uiState: EpisodeReleaseNotificationsUiState,
) {
    val releases = remember(uiState.expectedAlerts) {
        uiState.expectedAlerts
            .mapNotNull(::toReleaseCalendarItem)
            .sortedWith(compareBy<ReleaseCalendarItem> { it.date.year }
                .thenBy { it.date.month }
                .thenBy { it.date.day }
                .thenBy { it.timeLabel })
    }
    val today = remember { parseIsoDate(CurrentDateProvider.todayIsoDate()) }
    val initialDate = today ?: releases.firstOrNull()?.date ?: CalendarDate(2026, 5, 1)
    var visibleMonth by remember { mutableStateOf(CalendarMonth(initialDate.year, initialDate.month)) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    LaunchedEffect(releases) {
        val nextInitialDate = today ?: releases.firstOrNull()?.date ?: selectedDate
        visibleMonth = CalendarMonth(nextInitialDate.year, nextInitialDate.month)
        selectedDate = nextInitialDate
    }

    val releasesByDate = remember(releases) { releases.groupBy { it.date } }
    val selectedReleases = releasesByDate[selectedDate].orEmpty()

    SettingsSection(
        title = "RELEASE CALENDAR",
        isTablet = isTablet,
    ) {
        val calendarModifier = if (isTablet) {
            Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        } else {
            Modifier.fillMaxWidth()
        }
        Surface(
            modifier = calendarModifier,
            shape = RoundedCornerShape(if (isTablet) 28.dp else 22.dp),
            color = CalendarBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF101010), CalendarBackground),
                        ),
                    )
                    .padding(if (isTablet) 24.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ReleaseCalendarHeader(
                    month = visibleMonth,
                    onPreviousMonth = {
                        visibleMonth = visibleMonth.previous()
                        selectedDate = releases.firstOrNull { it.date.year == visibleMonth.year && it.date.month == visibleMonth.month }?.date
                            ?: CalendarDate(visibleMonth.year, visibleMonth.month, 1)
                    },
                    onNextMonth = {
                        visibleMonth = visibleMonth.next()
                        selectedDate = releases.firstOrNull { it.date.year == visibleMonth.year && it.date.month == visibleMonth.month }?.date
                            ?: CalendarDate(visibleMonth.year, visibleMonth.month, 1)
                    },
                )

                MonthCalendar(
                    month = visibleMonth,
                    selectedDate = selectedDate,
                    today = today,
                    releaseDates = releasesByDate.keys,
                    onDateSelected = { selectedDate = it },
                )

                ReleaseScheduleList(
                    selectedDate = selectedDate,
                    releases = selectedReleases,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                )
            }
        }
    }
}

@Composable
private fun ReleaseCalendarHeader(
    month: CalendarMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Release Calendar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "See what's coming next",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthNavButton(label = "<", onClick = onPreviousMonth)
                MonthNavButton(label = ">", onClick = onNextMonth)
            }
        }
        Text(
            text = "${monthName(month.month)} ${month.year}",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MonthNavButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MonthCalendar(
    month: CalendarMonth,
    selectedDate: CalendarDate,
    today: CalendarDate?,
    releaseDates: Set<CalendarDate>,
    onDateSelected: (CalendarDate) -> Unit,
) {
    val days = remember(month) { buildMonthCells(month) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.48f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                week.forEach { date ->
                    CalendarDayCell(
                        modifier = Modifier.weight(1f),
                        date = date,
                        isSelected = date == selectedDate,
                        isToday = date != null && date == today,
                        hasRelease = date != null && date in releaseDates,
                        onClick = {
                            if (date != null) onDateSelected(date)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    date: CalendarDate?,
    isSelected: Boolean,
    isToday: Boolean,
    hasRelease: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isToday -> NetflixRed
            isSelected -> Color.White.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
    )
    val borderColor = if (isSelected && !isToday) Color.White.copy(alpha = 0.22f) else Color.Transparent

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(if (hasRelease) 5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else NetflixRed),
                )
            }
        }
    }
}

@Composable
private fun ReleaseScheduleList(
    selectedDate: CalendarDate,
    releases: List<ReleaseCalendarItem>,
    isLoading: Boolean,
    errorMessage: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Coming on ${monthName(selectedDate.month)} ${selectedDate.day}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
        )

        when {
            isLoading -> ReleaseCalendarMessage("Loading upcoming releases...")
            errorMessage != null -> ReleaseCalendarMessage(errorMessage)
            releases.isEmpty() -> EmptyScheduleState()
            else -> releases.forEach { release ->
                ReleaseScheduleCard(release = release)
            }
        }
    }
}

@Composable
private fun ReleaseScheduleCard(release: ReleaseCalendarItem) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = release.deepLinkUrl != null) {
                release.deepLinkUrl?.let { url ->
                    runCatching { uriHandler.openUri(url) }
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = CalendarCard,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(CalendarCardAlt, CalendarCard),
                    ),
                )
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = release.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(104.dp),
                    contentScale = ContentScale.Crop,
                )
                if (release.imageUrl.isNullOrBlank()) {
                    Text(
                        text = "NEW",
                        color = NetflixRed,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReleaseBadge(release.typeLabel)
                    if (release.badgeLabel != null) {
                        ReleaseBadge(release.badgeLabel, filled = true)
                    }
                }
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = release.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = release.timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = NetflixRed,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ReleaseBadge(
    text: String,
    filled: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (filled) NetflixRed else Color.Transparent,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, NetflixRed.copy(alpha = 0.8f)),
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) Color.White else NetflixRed,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyScheduleState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.06f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No releases scheduled for this day",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Check nearby dates for upcoming episodes and premieres.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun ReleaseCalendarMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.72f),
    )
}

@Composable
private fun NotificationDeliveryCard(
    isTablet: Boolean,
    uiState: EpisodeReleaseNotificationsUiState,
) {
    SettingsSection(
        title = "NOTIFICATION DELIVERY",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Episode release alerts are enabled automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${uiState.scheduledCount} upcoming alerts scheduled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!uiState.permissionGranted) {
                    Text(
                        text = "System notifications are disabled for Nelflix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = EpisodeReleaseNotificationsRepository::openExactAlarmSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Open alarm permission")
                }
            }
        }
    }
}

private data class ReleaseCalendarItem(
    val alert: EpisodeReleaseAlertPreview,
    val date: CalendarDate,
    val timeLabel: String,
    val title: String,
    val description: String,
    val typeLabel: String,
    val badgeLabel: String?,
    val imageUrl: String?,
    val deepLinkUrl: String?,
)

private data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

private data class CalendarMonth(
    val year: Int,
    val month: Int,
) {
    fun previous(): CalendarMonth =
        if (month == 1) CalendarMonth(year - 1, 12) else CalendarMonth(year, month - 1)

    fun next(): CalendarMonth =
        if (month == 12) CalendarMonth(year + 1, 1) else CalendarMonth(year, month + 1)
}

private fun toReleaseCalendarItem(alert: EpisodeReleaseAlertPreview): ReleaseCalendarItem? {
    val parsed = parseReleaseLabel(alert.triggerTimeLabel) ?: return null
    val type = if (alert.body.contains("S", ignoreCase = true) && alert.body.contains("E", ignoreCase = true)) {
        "Episode"
    } else {
        "Movie"
    }
    val badge = when {
        type == "Movie" -> "Premiere"
        Regex("""S0?1E0?1""", RegexOption.IGNORE_CASE).containsMatchIn(alert.body) -> "Premiere"
        else -> "New"
    }
    return ReleaseCalendarItem(
        alert = alert,
        date = parsed.first,
        timeLabel = parsed.second,
        title = alert.title,
        description = alert.body,
        typeLabel = type,
        badgeLabel = badge,
        imageUrl = alert.imageUrl,
        deepLinkUrl = alert.deepLinkUrl,
    )
}

private fun parseReleaseLabel(label: String): Pair<CalendarDate, String>? {
    val trimmed = label.trim()
    val datePart = trimmed.substringBeforeLast(' ', missingDelimiterValue = "")
        .substringBeforeLast(' ', missingDelimiterValue = "")
        .trim()
    val meridiem = trimmed.substringAfterLast(' ', missingDelimiterValue = "").trim()
    val timeValue = trimmed.substringBeforeLast(' ', missingDelimiterValue = "")
        .substringAfterLast(' ', missingDelimiterValue = "")
        .trim()
    if (datePart.isBlank() || timeValue.isBlank() || meridiem.isBlank()) return null

    val monthAndDay = datePart.substringBefore(',', missingDelimiterValue = "").trim()
    val year = datePart.substringAfter(',', missingDelimiterValue = "").trim().toIntOrNull() ?: return null
    val monthName = monthAndDay.substringBefore(' ', missingDelimiterValue = "").trim()
    val day = monthAndDay.substringAfter(' ', missingDelimiterValue = "").trim().toIntOrNull() ?: return null
    val month = monthNumber(monthName) ?: return null
    return CalendarDate(year, month, day) to "$timeValue $meridiem"
}

private fun parseIsoDate(value: String): CalendarDate? {
    val parts = value.split("-")
    if (parts.size != 3) return null
    return CalendarDate(
        year = parts[0].toIntOrNull() ?: return null,
        month = parts[1].toIntOrNull() ?: return null,
        day = parts[2].toIntOrNull() ?: return null,
    )
}

private fun buildMonthCells(month: CalendarMonth): List<CalendarDate?> {
    val firstDayOffset = dayOfWeek(month.year, month.month, 1)
    val daysInMonth = daysInMonth(month.year, month.month)
    return buildList {
        repeat(firstDayOffset) { add(null) }
        for (day in 1..daysInMonth) {
            add(CalendarDate(month.year, month.month, day))
        }
        while (size % 7 != 0) add(null)
        while (size < 42) add(null)
    }
}

private fun dayOfWeek(year: Int, month: Int, day: Int): Int {
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    var y = year
    if (month < 3) y -= 1
    return (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + day) % 7
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 30
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun monthName(month: Int): String = MonthNames.getOrElse(month - 1) { "Month" }

private fun monthNumber(name: String): Int? =
    MonthNames.indexOfFirst { monthName ->
        monthName.equals(name, ignoreCase = true) ||
            monthName.take(3).equals(name.take(3), ignoreCase = true)
    }
        .takeIf { it >= 0 }
        ?.plus(1)

private val MonthNames = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)
