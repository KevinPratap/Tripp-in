package com.trippin.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.SectionLabel
import com.trippin.core.design.ThemeMode
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinOutlineButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinSegmentedTabs
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType

@Composable
fun ProfileScreen(
    onOpenTrips: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors

    TrippinScaffold(
        topBar = {}
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).background(colors.ink, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (state.account?.displayName?.takeIf { it.isNotBlank() } ?: state.account?.email ?: "T").take(1).uppercase(),
                            style = TrippinType.Title,
                            color = colors.paper
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.account?.displayName?.takeIf { it.isNotBlank() } ?: "Traveller",
                            style = TrippinType.Display,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(state.account?.email ?: "", style = TrippinType.Body, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                val stats = state.stats
                TrippinCard(onClick = onOpenTrips) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf(
                            stats.trips to if (stats.trips == 1) "trip" else "trips",
                            stats.places to if (stats.places == 1) "place" else "places",
                            stats.daysPlanned to if (stats.daysPlanned == 1) "day planned" else "days planned"
                        ).forEachIndexed { i, (value, label) ->
                            if (i > 0) Box(Modifier.width(1.dp).height(64.dp).background(colors.hairline))
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text("$value", style = TrippinType.Title, color = colors.ink)
                                Text(label, style = TrippinType.Caption, color = colors.inkMuted)
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Appearance", Modifier.padding(top = 10.dp, start = 4.dp)) }
            item {
                val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                TrippinSegmentedTabs(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = modes.indexOf(state.themeMode).coerceAtLeast(0),
                    onOptionSelected = { viewModel.setTheme(modes[it]) }
                )
            }

            item { SectionLabel("Saved places", Modifier.padding(top = 10.dp, start = 4.dp)) }
            if (state.savedSpots.isEmpty()) {
                item {
                    Text(
                        "Places you save from search appear here, so you can find them offline.",
                        style = TrippinType.Body,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            } else {
                items(state.savedSpots, key = { it.id.ifBlank { it.name } }) { spot ->
                    TrippinCard {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bookmark, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(spot.name, style = TrippinType.Label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (spot.formattedAddress.isNotBlank()) {
                                    Text(spot.formattedAddress, style = TrippinType.Caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            TrippinIconButton(Icons.Default.Close, "Remove ${spot.name}", { viewModel.removeSaved(spot) })
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                TrippinOutlineButton(
                    text = "Sign out",
                    onClick = viewModel::signOut,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Logout,
                    contentColor = colors.danger
                )
            }

            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val version = androidx.compose.runtime.remember(context) {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                }
                Text(
                    listOfNotNull("Tripp'in", version?.let { "version $it" }).joinToString(" "),
                    style = TrippinType.Caption,
                    color = colors.inkMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
