package com.dsi.app.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val clock = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayHeading = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
private val dayHeadingWithYear = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

fun formatClock(timestamp: Timestamp?): String =
    timestamp?.toDate()?.let { clock.format(it) } ?: ""

fun formatDayHeading(date: Date): String {
    val today = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    val sameYear = today.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val dayDiff = daysBetween(target, today)
    return when {
        dayDiff == 0 -> "Today"
        dayDiff == 1 -> "Yesterday"
        sameYear -> dayHeading.format(date)
        else -> dayHeadingWithYear.format(date)
    }
}

fun formatLastSeen(lastSeen: Long): String {
    if (lastSeen <= 0L) return "Offline"
    val date = Date(lastSeen)
    val target = Calendar.getInstance().apply { time = date }
    return when (daysBetween(target, Calendar.getInstance())) {
        0 -> "Last seen ${clock.format(date)}"
        1 -> "Last seen yesterday"
        else -> "Last seen ${dayHeading.format(date)}"
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun daysBetween(from: Calendar, to: Calendar): Int {
    val a = from.clone() as Calendar
    val b = to.clone() as Calendar
    listOf(a, b).forEach {
        it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0)
        it.set(Calendar.SECOND, 0); it.set(Calendar.MILLISECOND, 0)
    }
    return ((b.timeInMillis - a.timeInMillis) / 86_400_000L).toInt()
}

private val URL_PATTERN: Pattern = Pattern.compile(
    "(?i)\\b((?:https?://|www\\.)[-\\w+&@#/%?=~|!:,.;]*[-\\w+&@#/%=~|])"
)

/**
 * Turns bare URLs in a message into tappable links. Emoji need no special handling —
 * they are ordinary text and render through the system font.
 */
@Composable
fun linkifiedText(raw: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val matcher = URL_PATTERN.matcher(raw)
    var cursor = 0
    while (matcher.find()) {
        append(raw.substring(cursor, matcher.start()))
        val shown = matcher.group()
        val href = if (shown.startsWith("www.", ignoreCase = true)) "https://$shown" else shown
        withLink(
            LinkAnnotation.Url(
                url = href,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) { append(shown) }
        cursor = matcher.end()
    }
    append(raw.substring(cursor))
}
