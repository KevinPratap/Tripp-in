package com.trippin.ai.ui.screens.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.TripStatus
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.ago
import com.trippin.ai.ui.components.countdown
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.group.VoteKind

/**
 * Suggestions and votes — deliberately separate from the plan itself. Before a destination is
 * decided this shows destination candidates; afterwards it shows place ideas for the itinerary.
 */
@Composable
fun IdeasTab(vm: TripViewModel, ui: TripUi) {
    val trip = ui.trip ?: return
    var searching by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<Idea?>(null) }
    var showDismissed by remember { mutableStateOf(false) }

    val votingDestination = trip.status == TripStatus.DECIDING_DESTINATION
    val list = if (votingDestination) ui.destinationIdeas else ui.placeIdeas

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (votingDestination) {
                    Kicker("Choosing where to go")
                    trip.destinationVoteClosesAt?.let { Text(countdown(it), color = Trip.Muted) }
                } else {
                    Kicker("Ideas for ${trip.destination?.label ?: "the trip"}")
                }
            }

            if (list.isEmpty()) {
                item {
                    EmptyState(
                        if (votingDestination) "No destinations yet" else "No ideas yet",
                        "Tap + to suggest ${if (votingDestination) "a city" else "a place"}. Everyone's votes count separately.",
                    )
                }
            }

            items(list, key = { it.id }) { idea ->
                IdeaCard(
                    idea = idea,
                    myVote = idea.votes[vm.me.uid],
                    onVote = { k -> vm.vote(idea, k) },
                    onComment = { commentsFor = idea },
                    onDismiss = if (vm.canManage && !votingDestination) { { vm.dismiss(idea) } } else null,
                    onDecide = if (vm.canManage && votingDestination && list.size > 1) { { vm.decide(idea) } } else null,
                )
            }

            if (!votingDestination && ui.dismissedIdeas.isNotEmpty()) {
                item {
                    SmallButton(if (showDismissed) "Hide dismissed (${ui.dismissedIdeas.size})" else "Show dismissed (${ui.dismissedIdeas.size})", onClick = { showDismissed = !showDismissed })
                }
                if (showDismissed) {
                    items(ui.dismissedIdeas, key = { "d-" + it.id }) { idea ->
                        IdeaCard(idea, idea.votes[vm.me.uid], onVote = {}, onComment = { commentsFor = idea }, onRestore = if (vm.canManage) { { vm.restore(idea) } } else null, dismissed = true)
                    }
                }
            }
        }

        if (vm.canContribute) {
            FloatingActionButton(
                onClick = { searching = true },
                containerColor = Trip.Ink, contentColor = Trip.Paper,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = if (votingDestination) "Suggest a destination" else "Suggest a place") }
        }
    }

    if (searching) {
        if (votingDestination) {
            SearchSheet(
                title = "Suggest a destination", hint = "Search a city",
                search = { vm.searchDestinations(it) },
                onPick = { hit -> vm.suggest(IdeaType.DESTINATION, hit); searching = false },
                onDismiss = { searching = false },
            )
        } else {
            SearchSheet(
                title = "Suggest a place", hint = "Search near ${trip.destination?.name ?: "your trip"}",
                search = { vm.searchPlaces(it) },
                onPick = { hit -> vm.suggest(IdeaType.PLACE, hit); searching = false },
                onDismiss = { searching = false },
            )
        }
    }

    commentsFor?.let { idea ->
        CommentsSheet(
            title = idea.title,
            comments = ui.commentsFor(idea.id),
            myUid = vm.me.uid,
            canModerate = vm.canManage,
            canPost = vm.canContribute,
            onPost = { text -> vm.comment(idea, text) },
            onDelete = { vm.deleteComment(it) },
            onDismiss = { commentsFor = null },
        )
    }
}

@Composable
private fun IdeaCard(
    idea: Idea,
    myVote: VoteKind?,
    onVote: (VoteKind) -> Unit,
    onComment: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDecide: (() -> Unit)? = null,
    dismissed: Boolean = false,
) {
    Panel(background = if (dismissed) Trip.PaperDeep else Trip.Card) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(idea.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(idea.subtitle, idea.country).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Trip.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Suggested by ${idea.createdByName} · ${ago(idea.createdAt)}", style = MaterialTheme.typography.labelSmall, color = Trip.Muted)
            }
            if (onRestore != null) IconButton(onClick = onRestore) { Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = Trip.Muted) }
            else if (onDismiss != null) IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Trip.Muted) }
        }

        if (!dismissed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoteChip("Must", VoteKind.MUST, myVote, idea.musts, Trip.Signal) { onVote(VoteKind.MUST) }
                VoteChip("👍", VoteKind.UP, myVote, idea.ups - idea.musts, Trip.Mint) { onVote(VoteKind.UP) }
                VoteChip("👎", VoteKind.DOWN, myVote, idea.downs, Trip.Danger) { onVote(VoteKind.DOWN) }
                Box(Modifier.weight(1f))
                IconButton(onClick = onComment) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Comments", tint = Trip.Muted)
                        if (idea.commentCount > 0) Text(" ${idea.commentCount}", color = Trip.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (onDecide != null) SignalButton("Pick this — ${idea.score} pts", onClick = onDecide, container = Trip.Cobalt)
    }
}

@Composable
private fun VoteChip(label: String, kind: VoteKind, mine: VoteKind?, count: Int, color: Color, onClick: () -> Unit) {
    val on = mine == kind
    Box(Modifier.clickable(onClick = onClick)) {
        Pill(
            "$label${if (count > 0) " $count" else ""}",
            background = if (on) color else Trip.PaperDeep,
            foreground = if (on) Color.White else Trip.Ink,
        )
    }
}
