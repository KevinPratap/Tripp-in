package com.trippin.ai.ui.screens.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.trippin.ai.data.model.EventType
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.ago
import com.trippin.ai.ui.components.avatarColor
import com.trippin.ai.ui.theme.Trip

/**
 * The shared feed: everything the group has done to the trip, newest first, nothing hidden.
 * Each row has a coloured icon bubble (the colour is the person's, so you can scan by who),
 * the sentence, and how long ago it happened.
 */
@Composable
fun ActivityTab(vm: TripViewModel, ui: TripUi) {
    if (ui.events.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            EmptyState("Nothing yet", "Every suggestion, vote and change to the plan shows up here as it happens.", modifier = Modifier.padding(top = 60.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(ui.events, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(avatarColor(e.actorId)),
                    contentAlignment = Alignment.Center,
                ) { Icon(eventIcon(e.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.text, style = MaterialTheme.typography.bodyLarge)
                    Text(ago(e.createdAt), style = MaterialTheme.typography.labelMedium, color = Trip.Muted)
                }
            }
        }
    }
}

private fun eventIcon(t: EventType): ImageVector = when (t) {
    EventType.TRIP_CREATED, EventType.TRIP_UPDATED -> Icons.Filled.Luggage
    EventType.MEMBER_JOINED -> Icons.Filled.Person
    EventType.MEMBER_LEFT, EventType.MEMBER_REMOVED -> Icons.Filled.Close
    EventType.INVITE_CREATED, EventType.IDEA_CREATED -> Icons.Filled.Add
    EventType.INVITE_REVOKED -> Icons.Filled.Lock
    EventType.VOTE_CAST, EventType.VOTING_STARTED -> Icons.Filled.ThumbUp
    EventType.COMMENT_CREATED -> Icons.Filled.ChatBubbleOutline
    EventType.DESTINATION_SELECTED -> Icons.Filled.Place
    EventType.PREFERENCES_UPDATED -> Icons.Filled.Settings
    EventType.ITINERARY_UPDATED -> Icons.Filled.Map
    EventType.ITINERARY_CONFIRMED, EventType.PROPOSAL_APPROVED -> Icons.Filled.Check
    EventType.PROPOSAL_CREATED -> Icons.Filled.Edit
    EventType.PROPOSAL_REJECTED -> Icons.Filled.Close
}
