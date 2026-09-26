package com.trippin.ai.ui.screens.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trippin.ai.data.model.EventType
import com.trippin.ai.data.model.TripEvent
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.ago
import com.trippin.ai.ui.theme.Trip

/** The shared feed — everything the group has done to the trip, newest first, nothing hidden. */
@Composable
fun ActivityTab(vm: TripViewModel, ui: TripUi) {
    if (ui.events.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            EmptyState("Nothing yet", "Every suggestion, vote and change to the plan shows up here as it happens.", modifier = Modifier.padding(top = 60.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(ui.events, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(eventEmoji(e.type) + "  ", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(e.text, style = MaterialTheme.typography.bodyLarge)
                    Kicker(ago(e.createdAt))
                }
            }
            HorizontalDivider(color = Trip.PaperDeep)
        }
    }
}

private fun eventEmoji(t: EventType): String = when (t) {
    EventType.TRIP_CREATED, EventType.TRIP_UPDATED -> "🧳"
    EventType.MEMBER_JOINED -> "👋"
    EventType.MEMBER_LEFT, EventType.MEMBER_REMOVED -> "🚪"
    EventType.INVITE_CREATED -> "🔗"
    EventType.INVITE_REVOKED -> "🔒"
    EventType.IDEA_CREATED -> "💡"
    EventType.VOTE_CAST -> "🗳"
    EventType.COMMENT_CREATED -> "💬"
    EventType.DESTINATION_SELECTED -> "📍"
    EventType.VOTING_STARTED -> "🗳"
    EventType.PREFERENCES_UPDATED -> "⚙️"
    EventType.ITINERARY_UPDATED -> "🗺"
    EventType.ITINERARY_CONFIRMED -> "✅"
    EventType.PROPOSAL_CREATED -> "✏️"
    EventType.PROPOSAL_APPROVED -> "✅"
    EventType.PROPOSAL_REJECTED -> "❌"
}
