package com.trippin.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trippin.core.design.MessageState
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.feature.itinerary.ActivityCard
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.TripDetailsDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The stops for today, with the one happening now called out. Uses the trip's own dates to decide
 * whether today is inside them; when it is not, it says so rather than showing day one as if it were
 * today. Shares the [ActivityCard] with the day-by-day plan so a stop reads identically in both.
 */
@Composable
fun TodayContent(
    details: TripDetailsDto?,
    visited: Set<String>,
    onToggleVisited: (String) -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TrippinTheme.colors
    val today = LocalDate.now()
    val destinationName = details?.trip?.destination?.takeIf { it.isNotBlank() }

    val todaysDay = details?.itinerary?.days?.firstOrNull { day ->
        runCatching { LocalDate.parse(day.date.take(10)) }.getOrNull() == today
    }

    if (details == null) {
        MessageState(Icons.Default.Event, "No plan loaded", "Open the plan first, then Today shows the stops for the day.", modifier)
        return
    }
    if (todaysDay == null) {
        MessageState(
            icon = Icons.Default.Event,
            title = "Not today",
            body = "Today is not one of this trip's days. Today shows the current day's stops once the trip is underway.",
            modifier = modifier
        )
        return
    }

    val activities = todaysDay.activities
    val nowStop = currentStop(activities)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TrippinCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Today", style = TrippinType.Title, color = colors.ink)
                    val formattedDate = runCatching {
                        LocalDate.parse(todaysDay.date.take(10)).format(todayHeaderFormat)
                    }.getOrDefault(todaysDay.date)
                    Text(formattedDate, style = TrippinType.Caption, color = colors.inkMuted)
                    if (nowStop != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Now: ${nowStop.title}", style = TrippinType.Label, color = colors.ink)
                        }
                    }
                }
            }
        }
        items(activities.size) { i ->
            val act = activities[i]
            ActivityCard(
                activity = act,
                index = i + 1,
                visited = act.id in visited,
                onToggleVisited = { onToggleVisited(act.id) },
                destinationName = destinationName,
                dayDate = todaysDay.date
            )
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val todayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.US)

/** The stop whose window contains the current time, if any. */
private fun currentStop(activities: List<ActivityDto>): ActivityDto? {
    val now = LocalTime.now()
    return activities.firstOrNull { act ->
        val start = runCatching { LocalTime.parse(act.startTime.take(5), timeFormat) }.getOrNull()
        val end = runCatching { LocalTime.parse(act.endTime.take(5), timeFormat) }.getOrNull()
        start != null && end != null && !now.isBefore(start) && now.isBefore(end)
    }
}
