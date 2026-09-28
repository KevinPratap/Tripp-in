package com.trippin.feature.explore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.CardListSkeleton
import com.trippin.core.design.MessageState
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinOutlineButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.rememberCommitHaptic
import com.trippin.core.map.MapPlace
import com.trippin.core.map.MapStop
import com.trippin.core.map.PlaceKind
import com.trippin.core.map.TrippinMap
import com.trippin.core.map.currentLocation
import com.trippin.core.map.distanceLabel
import com.trippin.core.map.hasLocationPermission
import com.trippin.core.map.walkLabel
import com.trippin.core.network.ExplorePlaceDto
import com.trippin.core.network.GeoPointDto
import kotlinx.coroutines.launch

@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onSwapped: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberCommitHaptic()
    var confirmSwap by remember { mutableStateOf<ExplorePlaceDto?>(null) }

    val locate: () -> Unit = {
        viewModel.locating()
        scope.launch { viewModel.useLocation(currentLocation(context)) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else viewModel.useLocation(null)
    }

    LaunchedEffect(state.swap) {
        if (state.swap == SwapState.DONE) {
            haptic()
            viewModel.clearSwap()
            onSwapped()
        }
    }

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = "Around ${state.centerTitle}",
                subtitle = when {
                    state.loading -> "Looking within ${distanceLabel(state.radius)}"
                    state.places.isNotEmpty() -> "${state.places.size} places within ${distanceLabel(state.radius)}, from OpenStreetMap"
                    else -> "Within ${distanceLabel(state.radius)}"
                },
                onBack = onBack,
                actions = {
                    TrippinIconButton(
                        icon = Icons.Default.MyLocation,
                        contentDescription = "Around me",
                        onClick = {
                            if (hasLocationPermission(context)) locate()
                            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().weight(0.9f)) {
                TrippinMap(
                    modifier = Modifier.fillMaxSize(),
                    stops = if (state.aroundMe) emptyList() else listOf(MapStop(state.center, "")),
                    places = state.shown.map { MapPlace(it.id, GeoPointDto(it.latitude, it.longitude), PlaceKind.of(it.kind).color) },
                    selectedPlaceId = state.selectedId,
                    me = state.me,
                    focus = state.selected?.let { GeoPointDto(it.latitude, it.longitude) },
                    fitKey = "${state.center}:${state.radius}:${state.kind}:${state.places.size}",
                    onPlaceTap = { id -> if (id != null) viewModel.select(id) }
                )
                if (state.locating || state.locateFailed) {
                    Text(
                        if (state.locating) "Finding where you are" else "Could not get your location",
                        style = TrippinType.Label,
                        color = colors.ink,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .shadow(4.dp, RoundedCornerShape(50))
                            .background(colors.paper, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Column(
                Modifier
                    .weight(1.1f)
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(colors.paper, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            ) {
                if (state.kinds.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrippinChoiceChip("All", state.kind == null) { viewModel.setKind(null) }
                        state.kinds.forEach { k ->
                            val count = state.places.count { PlaceKind.of(it.kind) == k }
                            TrippinChoiceChip("${k.plural} $count", state.kind == k, leadingIcon = k.icon) { viewModel.setKind(k) }
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                when {
                    state.loading -> CardListSkeleton(label = "Finding places nearby")
                    state.failed -> MessageState(
                        Icons.Default.SearchOff,
                        "Could not look around",
                        "OpenStreetMap did not answer. Check the connection and try again.",
                        actionLabel = "Try again",
                        onAction = viewModel::load
                    )
                    state.places.isEmpty() -> MessageState(
                        Icons.Default.SearchOff,
                        "Nothing named nearby",
                        "OpenStreetMap lists no cafes, food, sights or parks within ${distanceLabel(state.radius)}.",
                        actionLabel = if (state.radius < 2000) "Look further" else null,
                        onAction = if (state.radius < 2000) viewModel::widen else null
                    )
                    else -> PlaceList(
                        places = state.shown,
                        selectedId = state.selectedId,
                        canSwap = viewModel.canSwap && !state.aroundMe,
                        swapping = state.swap == SwapState.WORKING,
                        showWiden = state.radius < 2000,
                        onSelect = viewModel::select,
                        onDirections = { openWalking(context, it) },
                        onSwap = { confirmSwap = it },
                        onWiden = viewModel::widen
                    )
                }
            }
        }
    }

    confirmSwap?.let { place ->
        AlertDialog(
            onDismissRequest = { confirmSwap = null },
            title = { Text("Swap in ${place.name}?") },
            text = {
                Text(
                    "It takes the place of ${state.centerTitle} at the same time. The plan is checked again after the change, " +
                        "so opening hours and travel times stay honest."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.swapIn(place); confirmSwap = null }) { Text("Swap it in") }
            },
            dismissButton = { TextButton(onClick = { confirmSwap = null }) { Text("Keep the plan") } }
        )
    }
    if (state.swap == SwapState.FAILED) {
        AlertDialog(
            onDismissRequest = viewModel::clearSwap,
            title = { Text("Could not change the plan") },
            text = { Text("Nothing was changed. You can try again, or open directions and go anyway.") },
            confirmButton = { TextButton(onClick = viewModel::clearSwap) { Text("OK") } }
        )
    }
}

@Composable
private fun PlaceList(
    places: List<ExplorePlaceDto>,
    selectedId: String?,
    canSwap: Boolean,
    swapping: Boolean,
    showWiden: Boolean,
    onSelect: (String) -> Unit,
    onDirections: (ExplorePlaceDto) -> Unit,
    onSwap: (ExplorePlaceDto) -> Unit,
    onWiden: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) {
        val i = places.indexOfFirst { it.id == selectedId }
        if (i >= 0) listState.animateScrollToItem(i)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(places, key = { it.id }) { place ->
            PlaceRow(
                place = place,
                selected = place.id == selectedId,
                canSwap = canSwap,
                swapping = swapping,
                onClick = { onSelect(place.id) },
                onDirections = { onDirections(place) },
                onSwap = { onSwap(place) }
            )
        }
        if (showWiden) {
            item {
                TrippinOutlineButton(
                    "Look further, up to 2 km",
                    onWiden,
                    Modifier.fillMaxWidth(),
                    contentColor = TrippinTheme.colors.ink
                )
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: ExplorePlaceDto,
    selected: Boolean,
    canSwap: Boolean,
    swapping: Boolean,
    onClick: () -> Unit,
    onDirections: () -> Unit,
    onSwap: () -> Unit
) {
    val colors = TrippinTheme.colors
    val kind = PlaceKind.of(place.kind)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.panelRaised else colors.panel)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.ink else colors.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(kind.color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(kind.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = TrippinType.Heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(place.cuisine?.replaceFirstChar { it.uppercase() } ?: kind.label, distanceLabel(place.distanceMeters))
                        .joinToString(" · "),
                    style = TrippinType.Caption,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(walkLabel(place.distanceMeters).removePrefix("About "), style = TrippinType.Label, color = colors.ink)
        }
        AnimatedVisibility(visible = selected) {
            Column(Modifier.padding(top = 12.dp)) {
                Text(
                    place.openingHours?.let { "Hours as listed on OpenStreetMap: $it" } ?: "OpenStreetMap lists no opening hours for this place.",
                    style = TrippinType.Body,
                    color = colors.inkMuted
                )
                Text(
                    "${walkLabel(place.distanceMeters)}, estimated from the distance.",
                    style = TrippinType.Caption,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (canSwap) {
                        TrippinOutlineButton(
                            if (swapping) "Changing the plan" else "Swap into plan",
                            onSwap,
                            Modifier.weight(1f),
                            enabled = !swapping,
                            leadingIcon = Icons.Default.SwapHoriz,
                            contentColor = colors.ink
                        )
                    }
                    TrippinButton(
                        text = "Walk there",
                        onClick = onDirections,
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Default.Directions
                    )
                }
            }
        }
    }
}

private fun openWalking(context: Context, place: ExplorePlaceDto) {
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}&travelmode=walking"
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
