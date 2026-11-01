package com.trippin.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.design.CardListSkeleton
import com.trippin.core.design.MessageState
import com.trippin.core.design.PillTone
import com.trippin.core.design.StatusPill
import com.trippin.core.design.SurfaceTier
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinType
import com.trippin.core.network.ItineraryVersionDto
import com.trippin.core.network.TripVersionsDto
import com.trippin.core.repository.GroupRepository
import com.trippin.feature.home.cityName
import com.trippin.navigation.History as HistoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HistoryUiState(
    val versions: TripVersionsDto? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository
) : ViewModel() {
    private val tripId = savedStateHandle.toRoute<HistoryRoute>().tripId
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init { refresh(initial = true) }

    fun refresh(initial: Boolean = false) {
        _uiState.update { it.copy(refreshing = !initial, error = null) }
        viewModelScope.launch {
            when (val result = groupRepository.getVersions(tripId)) {
                is DataResult.Ok -> _uiState.update { HistoryUiState(versions = result.value, loading = false) }
                is DataResult.Fail -> _uiState.update { it.copy(loading = false, refreshing = false, error = result.error.message) }
            }
        }
    }
}

/** How a version's stop count moved from the version saved before it, or null for the first plan. */
internal fun stopDelta(versions: List<ItineraryVersionDto>, index: Int): String? {
    val older = versions.getOrNull(index + 1) ?: return null
    val diff = versions[index].activitiesCount - older.activitiesCount
    return when {
        diff > 0 -> "$diff more ${if (diff == 1) "stop" else "stops"} than before"
        diff < 0 -> "${-diff} fewer ${if (diff == -1) "stop" else "stops"} than before"
        else -> "Same number of stops as before"
    }
}

private val whenFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.US)

private fun readableWhen(iso: String): String? =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(whenFormat) }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val data = state.versions
    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = "Plan history",
                subtitle = data?.let { d ->
                    val n = d.versions.size
                    listOfNotNull(d.destination.takeIf { it.isNotBlank() }?.let(::cityName), "$n ${if (n == 1) "version" else "versions"} kept").joinToString(" · ")
                },
                onBack = onBack
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                data == null && state.loading -> CardListSkeleton(label = "Loading the plan history")
                data == null -> MessageState(
                    icon = Icons.Default.History,
                    title = "Could not load the history",
                    body = state.error ?: "Connect and try again.",
                    actionLabel = "Try again",
                    onAction = { viewModel.refresh() }
                )
                data.versions.isEmpty() -> MessageState(
                    icon = Icons.Default.History,
                    title = "No plan yet",
                    body = "Once the plan is built, every change to it is kept here."
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            "Every change to the plan is kept as a version. Older versions are a record of what changed; the current one is what the trip follows.",
                            style = TrippinType.Body,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp)
                        )
                    }
                    itemsIndexed(data.versions, key = { _, v -> v.version }) { index, version ->
                        VersionRow(version, delta = stopDelta(data.versions, index), last = index == data.versions.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(version: ItineraryVersionDto, delta: String?, last: Boolean) {
    val colors = TrippinTheme.colors
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .size(14.dp)
                    .background(if (version.isCurrent) colors.accent else colors.paper, CircleShape)
                    .border(2.dp, if (version.isCurrent) colors.accent else colors.ink, CircleShape)
            )
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(colors.hairline))
        }
        Spacer(Modifier.width(12.dp))
        TrippinCard(
            tier = if (version.isCurrent) SurfaceTier.RAISED else SurfaceTier.FLAT,
            modifier = Modifier.weight(1f).padding(bottom = 14.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${version.version}", style = TrippinType.Title, color = colors.ink)
                    val (label, tone) = statusOf(version.status)
                    StatusPill(label, tone)
                    if (version.isCurrent) Text("CURRENT", style = TrippinType.Eyebrow, color = colors.accent)
                }
                val headline = version.summary?.takeIf { it.isNotBlank() } ?: version.title?.takeIf { it.isNotBlank() }
                if (headline != null) Text(headline, style = TrippinType.Body, color = colors.ink, maxLines = 3)
                val meta = listOfNotNull(
                    readableWhen(version.createdAt),
                    "${version.activitiesCount} ${if (version.activitiesCount == 1) "stop" else "stops"}",
                    delta
                ).joinToString(" · ")
                Text(meta, style = TrippinType.Caption, color = colors.inkMuted)
                if (version.isLocked) Text("Locked", style = TrippinType.Caption, color = colors.good)
            }
        }
    }
}

/** A version's own status, stated plainly. A draft is a plan that relaxed a rule or kept a warning. */
private fun statusOf(status: String): Pair<String, PillTone> = when (status.uppercase()) {
    "VERIFIED" -> "Verified" to PillTone.GOOD
    "DRAFT" -> "Draft" to PillTone.WARN
    "ARCHIVED" -> "Archived" to PillTone.NEUTRAL
    else -> status.lowercase().replaceFirstChar { it.titlecase() } to PillTone.NEUTRAL
}
