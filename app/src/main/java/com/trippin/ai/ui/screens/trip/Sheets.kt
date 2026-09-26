package com.trippin.ai.ui.screens.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trippin.ai.data.model.Comment
import com.trippin.ai.data.model.ItineraryStop
import com.trippin.ai.data.model.PlaceHit
import com.trippin.ai.ui.components.Avatar
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.ProbabilityBar
import com.trippin.ai.ui.components.TextInput
import com.trippin.ai.ui.components.ago
import com.trippin.ai.ui.components.clock
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.model.Category
import kotlinx.coroutines.delay

/**
 * Search-as-you-type sheet for a city or a place (Photon / OpenStreetMap). Results come from the
 * network, so it shows loading, "no matches" and "offline" states instead of an empty list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    title: String,
    hint: String,
    search: suspend (String) -> Result<List<PlaceHit>>,
    onPick: (PlaceHit) -> Unit,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); failed = false; return@LaunchedEffect }
        delay(350) // debounce: don't hit the free API on every keystroke
        loading = true
        search(q).onSuccess { results = it; failed = false }.onFailure { failed = true }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Trip.Paper) {
        Column(Modifier.fillMaxHeight(0.9f).padding(horizontal = 20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            TextInput(q, { q = it }, hint, trailing = { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Trip.Ink) })
            when {
                failed -> Text("Can't search right now — check your connection.", color = MaterialTheme.colorScheme.error)
                !loading && q.trim().length >= 2 && results.isEmpty() -> Text("No matches. Try another spelling.", color = Trip.Muted)
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(results, key = { "${it.name}${it.lat}${it.lng}" }) { hit ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(hit) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = Trip.Signal)
                        Column(Modifier.weight(1f)) {
                            Text(hit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(hit.detail, hit.country).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Trip.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Filled.Add, contentDescription = "Add ${hit.name}")
                    }
                    HorizontalDivider(color = Trip.PaperDeep)
                }
                if (footer != null) item { footer() }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

/** One-level comment thread for an idea (or the whole trip when [title] is the trip). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    title: String,
    comments: List<Comment>,
    myUid: String,
    canModerate: Boolean,
    canPost: Boolean,
    onPost: (String) -> Unit,
    onDelete: (Comment) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Comment?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Trip.Paper) {
        Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker("Comments", color = Trip.Ink)
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (comments.isEmpty()) item { Text("No comments yet. Say what you think — cost, timing, who's in.", color = Trip.Muted) }
                items(comments, key = { it.id }) { cm ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(cm.name, cm.uid, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text("${cm.name} · ${ago(cm.createdAt)}", style = MaterialTheme.typography.labelMedium, color = Trip.Muted)
                            Text(cm.text, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (cm.uid == myUid || canModerate) {
                            IconButton(onClick = { confirm = cm }) { Icon(Icons.Filled.Delete, contentDescription = "Delete comment", tint = Trip.Muted) }
                        }
                    }
                }
            }
            if (canPost) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                    TextInput(text, { text = it.take(500) }, "Add a comment", singleLine = false, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onPost(text); text = "" }, enabled = text.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post comment", tint = if (text.isNotBlank()) Trip.Ink else Trip.Muted)
                    }
                }
            } else {
                Text("Viewers can read but not comment.", color = Trip.Muted, modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp))
            }
        }
    }
    confirm?.let { cm ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete this comment?") },
            text = { Text("“${cm.text.take(80)}”") },
            confirmButton = { TextButton(onClick = { onDelete(cm); confirm = null }) { Text("Delete", color = Trip.Danger) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Keep") } },
        )
    }
}

/** The Bayesian network's explanation for one stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhySheet(s: ItineraryStop, rainKnown: Boolean, onDismiss: () -> Unit, onLab: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Trip.Paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Kicker("Bayesian network · ${s.name}", color = Trip.Ink)
            Text("Why ${s.risk.lowercase()} risk?", style = MaterialTheme.typography.displaySmall)
            ProbabilityBar("Plan disrupted", s.pDisrupted, Trip.Signal)
            ProbabilityBar("Delayed on the way", s.pDelay)
            ProbabilityBar("Closed when you arrive", s.pClosed)
            ProbabilityBar("Rain was the cause, if it goes wrong", s.pRainGivenDisrupted, Trip.Cobalt)
            Text(
                buildString {
                    append("Evidence: arriving ${clock(s.arrive)}")
                    if (s.travelMinutes > 20) append(", a long ${s.travelMinutes}-min leg")
                    append(if (s.hoursEstimated) ", opening hours guessed from the type of place" else ", published opening hours")
                    append(if (rainKnown) ", and the live rain forecast." else ". The date is beyond the forecast, so rain is a neutral 30% guess.")
                    append(" The last bar reads the network backwards with Bayes' rule.")
                },
                style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
            )
            if (s.hoursEstimated) Text("Tip: check the opening hours before you go.", style = MaterialTheme.typography.titleMedium)
            Row { TextButton(onClick = onLab) { Text("See the model in the AI Lab", color = Trip.Ink) } }
        }
    }
}

fun categoryLabel(c: Category?): String = when (c) {
    Category.MUSEUM -> "Museum"
    Category.FOOD -> "Food"
    Category.PARK -> "Park"
    Category.LANDMARK -> "Sight"
    Category.SHOPPING -> "Market"
    Category.NIGHTLIFE -> "Nightlife"
    null -> "Place"
}

fun categoryEmoji(c: Category?): String = when (c) {
    Category.MUSEUM -> "🏛"
    Category.FOOD -> "🍽"
    Category.PARK -> "🌳"
    Category.LANDMARK -> "📍"
    Category.SHOPPING -> "🛍"
    Category.NIGHTLIFE -> "🌙"
    null -> "📍"
}
