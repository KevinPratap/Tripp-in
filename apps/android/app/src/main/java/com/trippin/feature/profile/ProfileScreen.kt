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
        topBar = { TrippinTopBar(title = "You", subtitle = "Your account and saved places") }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TrippinCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).background(colors.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (state.account?.displayName ?: "T").take(1).uppercase(),
                                style = TrippinType.Title,
                                color = colors.onAccent
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.account?.displayName ?: "Traveller", style = TrippinType.Title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state.account?.email ?: "", style = TrippinType.Body, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item {
                TrippinCard(onClick = onOpenTrips) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your trips", style = TrippinType.Heading, color = colors.ink, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.inkMuted)
                    }
                }
            }

            item { SectionLabel("Appearance", Modifier.padding(top = 6.dp)) }
            item {
                val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                TrippinSegmentedTabs(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = modes.indexOf(state.themeMode).coerceAtLeast(0),
                    onOptionSelected = { viewModel.setTheme(modes[it]) }
                )
            }

            item { SectionLabel("Saved places", Modifier.padding(top = 6.dp)) }
            if (state.savedSpots.isEmpty()) {
                item {
                    Text(
                        "Places you save from search appear here, so you can find them offline.",
                        style = TrippinType.Body,
                        color = colors.inkMuted
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
                Spacer(Modifier.height(8.dp))
                TrippinCard {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = colors.good, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Every plan here is checked against real opening hours and travel times. Nothing is invented.",
                            style = TrippinType.Caption,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                TrippinButton(
                    text = "Sign out",
                    onClick = viewModel::signOut,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Logout,
                    container = colors.panel,
                    onContainer = colors.danger
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
