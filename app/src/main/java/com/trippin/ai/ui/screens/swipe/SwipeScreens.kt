package com.trippin.ai.ui.screens.swipe

import androidx.compose.animation.core.Animatable
import com.trippin.ai.ui.brand.Art
import com.trippin.ai.ui.icons.TripIcons
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaStatus
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.Member
import com.trippin.ai.data.model.TripStatus
import com.trippin.ai.ui.components.Avatar
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.NumberBadge
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.Segmented
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.categoryIcon
import com.trippin.ai.ui.components.categoryLabel
import com.trippin.ai.ui.components.dateRange
import com.trippin.ai.ui.components.seedBrush
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.screens.trip.SearchSheet
import com.trippin.ai.ui.screens.trip.TripUi
import com.trippin.ai.ui.screens.trip.TripViewModel
import com.trippin.ai.ui.screens.trips.TripsViewModel
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.group.AgreementLevel
import com.trippin.intelligence.group.Consensus
import com.trippin.intelligence.group.GroupConsensus
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Geo
import com.trippin.intelligence.model.Place
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SwipeMode { DECK, MATCHES, TIEBREAK }

private const val COMMIT_X = 240f
private const val COMMIT_UP = 260f

// ---------------------------------------------------------------------------------------------
// Hub: the centre tab. Pick which trip to swipe on.
// ---------------------------------------------------------------------------------------------

@Composable
fun SwipeHubScreen(container: AppContainer, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val me = LocalSession.current
    val vm: TripsViewModel = viewModel(key = "swipehub-${me.uid}") { TripsViewModel(container, me.uid) }
    val trips by vm.trips.collectAsStateWithLifecycle()
    val active = trips?.filterNot { it.isPast() }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Headline("Swipe") }
        item {
            Text(
                "Pick a trip. Swipe right on what you want, left on what you don't. Your group sees where you all agree.",
                style = MaterialTheme.typography.bodyLarge, color = Trip.Muted,
            )
        }
        if (trips != null && active.isEmpty()) {
            item {
                EmptyState(
                    "No trips to swipe on",
                    "Plan a trip with friends, add a few ideas, then swipe on them together.",
                    action = { SignalButton("Plan a trip", onCreate) },
                )
            }
        }
        items(active, key = { it.id }) { t ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(seedBrush(t.id)).clickable { onOpen(t.id) }.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(dateRange(t.startDate, t.days), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                Text(t.destination?.label ?: t.title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (t.status == TripStatus.DECIDING_DESTINATION) "Vote on where to go" else "Vote on places to visit",
                    style = MaterialTheme.typography.titleMedium, color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Pill("Start swiping", Color.White, Trip.Ink, Modifier.align(Alignment.Start))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The deck for one trip
// ---------------------------------------------------------------------------------------------

@Composable
fun SwipeScreen(container: AppContainer, tripId: String, onBack: () -> Unit, onPlan: () -> Unit) {
    val me = LocalSession.current
    val vm: TripViewModel = viewModel(key = "trip-$tripId-${me.uid}") { TripViewModel(container, tripId, me) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val trip = ui.trip

    if (trip == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = onBack) { Icon(TripIcons.Back, contentDescription = "Back", tint = Trip.Ink) }
            if (!ui.loading) EmptyState("Trip not available", "It may have been deleted, or you're no longer a member.", modifier = Modifier.padding(20.dp))
        }
        return
    }

    var mode by rememberSaveable { mutableStateOf(SwipeMode.DECK) }
    var searching by remember { mutableStateOf(false) }
    val swiped = remember { mutableStateListOf<String>() }
    val history = remember { mutableStateListOf<Pair<String, VoteKind?>>() }
    var pinned by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf<VoteKind?>(null) }
    var announced by remember { mutableStateOf(false) }
    var tiebreak by remember { mutableStateOf<List<String>>(emptyList()) }

    val votingDestination = trip.status == TripStatus.DECIDING_DESTINATION
    val pool: List<Idea> = if (votingDestination) ui.destinationIdeas else ui.placeIdeas
    val byId = pool.associateBy { it.id }
    val memberCount = ui.members.size.coerceAtLeast(1)
    val cons = pool.map { GroupConsensus.of(it.id, it.votes, memberCount) }
    val openPool = pool.filter { it.status == IdeaStatus.OPEN }

    val deckIds: List<String> =
        if (mode == SwipeMode.TIEBREAK) {
            tiebreak.filter { it !in swiped && it in byId }
        } else {
            GroupConsensus.deckOrder(
                cons.filter { byId[it.ideaId]?.status == IdeaStatus.OPEN },
                alreadyVotedByMe = pool.filter { it.votes[me.uid] != null }.map { it.id }.toSet(),
            ).filter { it !in swiped }
        }
    val myInterests = ui.prefsOf(me.uid)?.takeIf { it.updatedAt > 0 }?.interests.orEmpty()
    val vibeOrdered = if (myInterests.isEmpty()) deckIds else deckIds.sortedByDescending { id -> if (byId[id]?.category?.let { it in myInterests } == true) 1 else 0 }
    val ordered = pinned?.takeIf { it in deckIds }?.let { p -> listOf(p) + vibeOrdered.filter { it != p } } ?: vibeOrdered
    val deck = ordered.mapNotNull { byId[it] }

    val progress = GroupConsensus.memberProgress(
        ui.members.map { it.uid }, openPool.map { it.id }, openPool.associate { it.id to it.votes },
    )

    LaunchedEffect(deck.isEmpty(), swiped.size) {
        if (mode != SwipeMode.MATCHES && deck.isEmpty() && swiped.isNotEmpty() && !announced) {
            announced = true
            vm.finishSwiping(swiped.size)
        }
    }

    fun onSwiped(idea: Idea, kind: VoteKind) {
        history.add(idea.id to idea.votes[me.uid])
        swiped.add(idea.id)
        if (pinned == idea.id) pinned = null
        request = null
        vm.swipe(idea, kind)
    }

    fun undo() {
        val last = history.lastOrNull() ?: return
        history.removeAt(history.lastIndex)
        val idea = byId[last.first] ?: return
        swiped.remove(last.first)
        pinned = last.first
        vm.swipe(idea, last.second)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(TripIcons.Back, contentDescription = "Back", tint = Trip.Ink) }
            Column(Modifier.weight(1f)) {
                Text(if (votingDestination) "Where to go" else "What to do", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(trip.destination?.label ?: trip.title, style = MaterialTheme.typography.bodyMedium, color = Trip.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (vm.canContribute) {
                IconButton(onClick = { searching = true }) {
                    Icon(TripIcons.Add, contentDescription = if (votingDestination) "Suggest a destination" else "Suggest a place", tint = Trip.Ink)
                }
            }
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Segmented(
                options = listOf(SwipeMode.DECK, SwipeMode.MATCHES),
                selected = if (mode == SwipeMode.MATCHES) SwipeMode.MATCHES else SwipeMode.DECK,
                label = { if (it == SwipeMode.DECK) "Swipe" else "Matches" },
                onSelect = { mode = it },
            )
        }
        MemberStrip(ui.members, progress)

        if (mode == SwipeMode.MATCHES) {
            MatchesPane(
                vm = vm, ui = ui, pool = pool, cons = cons, votingDestination = votingDestination,
                onTiebreak = {
                    tiebreak = GroupConsensus.splitRound(cons).map { it.ideaId }
                    swiped.clear(); history.clear(); announced = false
                    mode = SwipeMode.TIEBREAK
                },
                onPlan = onPlan,
            )
        } else if (!vm.canContribute) {
            EmptyState("View only", "Viewers can't vote. Open Matches to see how the group is leaning.", modifier = Modifier.padding(20.dp))
        } else {
            if (mode == SwipeMode.TIEBREAK) {
                Text(
                    "Tiebreak round. The group is split on these, so everyone votes again.",
                    style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (deck.isEmpty()) {
                    DoneState(
                        empty = pool.isEmpty(),
                        tiebreak = mode == SwipeMode.TIEBREAK,
                        onMatches = { mode = SwipeMode.MATCHES },
                        onSuggest = { searching = true },
                    )
                } else {
                    val shown = deck.take(3)
                    for (i in shown.indices.reversed()) {
                        val idea = shown[i]
                        key(idea.id) {
                            val face: @Composable (() -> Offset) -> Unit = { offsetOf ->
                                CardFace(
                                    idea = idea, offsetOf = offsetOf, memberCount = memberCount,
                                    centreLat = trip.destination?.lat, centreLng = trip.destination?.lng,
                                    myVote = idea.votes[me.uid],
                                    vibeMatch = idea.category?.let { it in myInterests } == true,
                                )
                            }
                            if (i == 0) {
                                DraggableCard(request = request, onSwiped = { onSwiped(idea, it) }, content = face)
                            } else {
                                Box(
                                    Modifier.fillMaxSize().graphicsLayer {
                                        val s = 1f - 0.05f * i
                                        scaleX = s; scaleY = s
                                        translationY = 16.dp.toPx() * i
                                    },
                                ) { face { Offset.Zero } }
                            }
                        }
                    }
                }
            }
            if (deck.isNotEmpty()) {
                if (swiped.isEmpty()) {
                    Text(
                        "Swipe right to like, left to pass, up for a must.",
                        style = MaterialTheme.typography.labelMedium, color = Trip.Muted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                ActionRow(
                    canUndo = history.isNotEmpty() && request == null,
                    onUndo = { undo() },
                    onVote = { if (request == null) request = it },
                )
            }
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
}

// ---------------------------------------------------------------------------------------------
// Deck pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun MemberStrip(members: List<Member>, progress: Map<String, Double>) {
    val done = members.count { (progress[it.uid] ?: 0.0) >= 1.0 }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        members.take(6).forEach { m ->
            val finished = (progress[m.uid] ?: 0.0) >= 1.0
            Box {
                Avatar(m.name, m.uid, 32.dp, Modifier.alpha(if (finished) 1f else 0.4f))
                if (finished) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(Trip.Mint),
                        contentAlignment = Alignment.Center,
                    ) { Icon(TripIcons.Check, contentDescription = "Finished voting", tint = Color.White, modifier = Modifier.size(10.dp)) }
                }
            }
        }
        Text("$done of ${members.size} done", style = MaterialTheme.typography.labelMedium, color = Trip.Muted)
    }
}

@Composable
private fun DraggableCard(request: VoteKind?, onSwiped: (VoteKind) -> Unit, content: @Composable (() -> Offset) -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val haptic = LocalHapticFeedback.current
    val latestOnSwiped by rememberUpdatedState(onSwiped)

    suspend fun fling(kind: VoteKind) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val target = when (kind) {
            VoteKind.UP -> Offset(1500f, offset.value.y - 80f)
            VoteKind.DOWN -> Offset(-1500f, offset.value.y - 80f)
            VoteKind.MUST -> Offset(offset.value.x, -2200f)
        }
        offset.animateTo(target, tween(240))
        latestOnSwiped(kind)
    }

    LaunchedEffect(request) { request?.let { fling(it) } }

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
            .graphicsLayer { rotationZ = (offset.value.x / 40f).coerceIn(-14f, 14f) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val o = offset.value
                        val kind = when {
                            -o.y > COMMIT_UP && -o.y > abs(o.x) -> VoteKind.MUST
                            o.x > COMMIT_X -> VoteKind.UP
                            o.x < -COMMIT_X -> VoteKind.DOWN
                            else -> null
                        }
                        scope.launch {
                            if (kind == null) offset.animateTo(Offset.Zero, spring(dampingRatio = 0.65f)) else fling(kind)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(Offset.Zero, spring()) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag) }
                }
            },
    ) { content { offset.value } }
}

@Composable
private fun CardFace(idea: Idea, offsetOf: () -> Offset, memberCount: Int, centreLat: Double?, centreLng: Double?, myVote: VoteKind?, vibeMatch: Boolean) {
    val shape = RoundedCornerShape(28.dp)
    val facts = buildList {
        idea.tags.forEach { t ->
            when (t) {
                Place.TAG_VEGETARIAN -> add("Vegetarian options")
                Place.TAG_VEGAN -> add("Vegan options")
                Place.TAG_WHEELCHAIR -> add("Wheelchair access")
                Place.TAG_ALCOHOL -> add("Serves alcohol")
                else -> Unit
            }
        }
    }
    val distance = if (centreLat != null && centreLng != null && idea.lat != null && idea.lng != null) {
        "%.1f km from the centre".format(Geo.haversineKm(centreLat, centreLng, idea.lat, idea.lng))
    } else null

    Box(
        Modifier.fillMaxSize()
            .shadow(8.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(shape).background(seedBrush(idea.id)),
    ) {
        Icon(
            categoryIcon(idea.category), contentDescription = null, tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp).size(150.dp),
        )
        Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(categoryLabel(idea.category), Color.White.copy(alpha = 0.22f), Color.White)
            myVote?.let { Pill("You: " + voteWord(it), Color.White, Trip.Ink) }
            if (vibeMatch) Pill("Matches your vibe", Trip.Gold, Trip.Ink)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(start = 20.dp, end = 20.dp, top = 80.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(idea.title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(idea.subtitle, idea.country).joinToString(", ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            distance?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f)) }
            if (facts.isNotEmpty()) Text(facts.joinToString("  |  "), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            Text(
                "Suggested by ${idea.createdByName}. ${idea.votes.size} of $memberCount have voted.",
                style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f),
            )
        }
        // Stamps fade in with the drag. They read the offset inside the graphics layer, so dragging
        // never recomposes the card.
        Stamp("LIKE", Trip.Mint, -12f, { (offsetOf().x / COMMIT_X).coerceIn(0f, 1f) }, Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 64.dp))
        Stamp("PASS", Trip.Danger, 12f, { (-offsetOf().x / COMMIT_X).coerceIn(0f, 1f) }, Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 64.dp))
        Stamp("MUST", Trip.Gold, 0f, { if (-offsetOf().y > abs(offsetOf().x)) (-offsetOf().y / COMMIT_UP).coerceIn(0f, 1f) else 0f }, Modifier.align(Alignment.Center))
    }
}

private fun voteWord(k: VoteKind) = when (k) { VoteKind.MUST -> "Must"; VoteKind.UP -> "Like"; VoteKind.DOWN -> "Pass" }

@Composable
private fun Stamp(text: String, color: Color, rotation: Float, strength: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .graphicsLayer { alpha = strength(); rotationZ = rotation }
            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .border(3.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 2.dp),
    ) { Text(text, style = MaterialTheme.typography.displaySmall, color = color) }
}

@Composable
private fun ActionRow(canUndo: Boolean, onUndo: () -> Unit, onVote: (VoteKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundAction(TripIcons.Restore, "Undo last vote", Trip.Muted, 48, canUndo, onUndo)
        RoundAction(TripIcons.Close, "Pass", Trip.Danger, 64, true) { onVote(VoteKind.DOWN) }
        RoundAction(TripIcons.Star, "Must do", Trip.Amber, 54, true) { onVote(VoteKind.MUST) }
        RoundAction(TripIcons.ThumbUp, "Like", Trip.Mint, 64, true) { onVote(VoteKind.UP) }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, size: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp)
            .shadow(if (Trip.dark) 0.dp else 4.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
            .clip(CircleShape).background(Trip.Card).border(1.5.dp, tint.copy(alpha = if (enabled) 0.55f else 0.2f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size((size * 0.45f).dp)) }
}

@Composable
private fun DoneState(empty: Boolean, tiebreak: Boolean, onMatches: () -> Unit, onSuggest: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Trip.Brand), contentAlignment = Alignment.Center) {
            Icon(TripIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Text(
            when {
                empty -> "Nothing to vote on yet"
                tiebreak -> "Tiebreak done"
                else -> "You're all caught up"
            },
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        )
        Text(
            when {
                empty -> "Suggest a few ideas, then swipe on them together."
                else -> "See where the group agrees, or add another idea."
            },
            style = MaterialTheme.typography.bodyLarge, color = Trip.Muted, textAlign = TextAlign.Center,
        )
        if (!empty) SignalButton("See group matches", onMatches)
        SmallButton("Suggest an idea", onSuggest)
    }
}

// ---------------------------------------------------------------------------------------------
// Matches
// ---------------------------------------------------------------------------------------------

@Composable
private fun MatchesPane(
    vm: TripViewModel,
    ui: TripUi,
    pool: List<Idea>,
    cons: List<Consensus>,
    votingDestination: Boolean,
    onTiebreak: () -> Unit,
    onPlan: () -> Unit,
) {
    val byId = pool.associateBy { it.id }
    val winners = GroupConsensus.winners(cons)
    val splits = GroupConsensus.splitRound(cons)
    val passed = GroupConsensus.rank(cons.filter { it.level == AgreementLevel.DISLIKED })
    val waiting = cons.count { it.level == AgreementLevel.UNVOTED }
    val agreement = GroupConsensus.overallAgreementPercent(cons)
    var confirmLock by remember { mutableStateOf(false) }

    val keep = winners.mapNotNull { byId[it.ideaId] }.filter { it.status != IdeaStatus.SELECTED }
    val drop = passed.mapNotNull { byId[it.ideaId] }.filter { it.status == IdeaStatus.OPEN }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { agreement / 100f }, modifier = Modifier.size(96.dp),
                            color = Trip.Signal, trackColor = Trip.PaperDeep, strokeWidth = 10.dp,
                        )
                        Text("$agreement%", style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Group agreement", style = MaterialTheme.typography.titleMedium)
                        Text("${winners.size} loved or liked", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
                        Text("${splits.size} split", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
                        Text("${passed.size} passed", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
                        if (waiting > 0) Text("$waiting waiting for votes", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
                    }
                }
            }
        }

        if (splits.isNotEmpty() && vm.canContribute) {
            item {
                Panel {
                    Text("Split decisions", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "The group is divided on ${splits.size} ${if (splits.size == 1) "idea" else "ideas"}. Everyone gets one more vote on just those.",
                        style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
                    )
                    SignalButton("Break the tie", onTiebreak, container = Trip.Cobalt, content = Color.White)
                }
            }
        }

        item { Text("Top picks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (winners.isEmpty()) {
            item { EmptyState("No matches yet", "Keep swiping. Picks the group agrees on show up here, ranked.", art = Art.CARDS) }
        }
        items(winners.size, key = { winners[it].ideaId }) { i ->
            val c = winners[i]
            byId[c.ideaId]?.let { MatchRow(i + 1, it, c, ui.members) }
        }

        if (passed.isNotEmpty()) {
            item { Text("Not for this group", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(passed.size, key = { "p-" + passed[it].ideaId }) { i ->
                val c = passed[i]
                byId[c.ideaId]?.let { MatchRow(null, it, c, ui.members) }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            when {
                !vm.canManage -> Text("The owner or an admin locks the picks into the plan.", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
                votingDestination -> {
                    val top = winners.firstOrNull()?.let { byId[it.ideaId] }
                    if (top != null && ui.destinationIdeas.size > 1) SignalButton("Pick ${top.title}", onClick = { vm.decide(top) })
                }
                keep.isNotEmpty() || drop.isNotEmpty() -> SignalButton(
                    if (keep.isNotEmpty()) "Lock ${keep.size} ${if (keep.size == 1) "pick" else "picks"} into the plan" else "Set aside ${drop.size} passed",
                    onClick = { confirmLock = true },
                )
            }
        }
    }

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text("Lock these picks?") },
            text = {
                Text(
                    "${keep.size} ${if (keep.size == 1) "pick goes" else "picks go"} into the plan" +
                        (if (drop.isNotEmpty()) " and ${drop.size} the group passed on ${if (drop.size == 1) "is" else "are"} set aside" else "") +
                        ". You can restore set-aside ideas from Ideas.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmLock = false; vm.lock(keep, drop); onPlan() }) { Text("Lock") } },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MatchRow(rank: Int?, idea: Idea, c: Consensus, members: List<Member>) {
    Panel(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rank != null) NumberBadge(rank)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(idea.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(idea.subtitle, idea.country).joinToString(", ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Trip.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    levelPill(c.level, idea.status == IdeaStatus.SELECTED)
                    VoteDots(idea, members)
                }
            }
            Text("${c.score}", style = MaterialTheme.typography.headlineSmall, color = Trip.Ink)
        }
    }
}

@Composable
private fun levelPill(level: AgreementLevel, locked: Boolean) {
    if (locked) { Pill("Locked in", Trip.Ink, Trip.Paper); return }
    when (level) {
        AgreementLevel.LOVED -> Pill("Loved", Trip.Signal, Color.White)
        AgreementLevel.LIKED -> Pill("Liked", Trip.Mint, Color.White)
        AgreementLevel.SPLIT -> Pill("Split", Trip.Amber, Color.White)
        AgreementLevel.DISLIKED -> Pill("Passed", Trip.Danger, Color.White)
        AgreementLevel.UNVOTED -> Pill("No votes", Trip.PaperDeep, Trip.Muted)
    }
}

/** One dot per member, coloured by their vote. The words are in the content description, so colour is never the only signal. */
@Composable
private fun VoteDots(idea: Idea, members: List<Member>) {
    val description = members.joinToString(", ") { m ->
        m.name + " " + (idea.votes[m.uid]?.let { voteWord(it).lowercase() } ?: "has not voted")
    }
    Row(Modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        members.take(8).forEach { m ->
            val color = when (idea.votes[m.uid]) {
                VoteKind.MUST -> Trip.Gold
                VoteKind.UP -> Trip.Mint
                VoteKind.DOWN -> Trip.Danger
                null -> Trip.PaperDeep
            }
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        }
    }
}
