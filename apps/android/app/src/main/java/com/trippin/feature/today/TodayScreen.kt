package com.trippin.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.LoadingBlock
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenMap: (String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val destinationName = state.details?.trip?.destination?.takeIf { it.isNotBlank() }

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = destinationName?.let { "Today in $it" } ?: "Today",
                subtitle = "The stops for the day you are on",
                onBack = onBack,
                actions = {
                    TrippinIconButton(Icons.Default.Map, "Map", { onOpenMap(tripId) })
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingBlock("Loading today", Modifier.fillMaxSize().padding(padding))
        } else {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                TodayContent(
                    details = state.details,
                    visited = state.visited,
                    onToggleVisited = viewModel::toggleVisited,
                    onOpenMap = { onOpenMap(tripId) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
