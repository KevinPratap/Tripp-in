package com.trippin.feature.group

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.common.InterestWords
import com.trippin.core.common.TravellerPaceChoices
import com.trippin.core.design.CardListSkeleton
import com.trippin.core.design.MessageState
import com.trippin.core.design.PillTone
import com.trippin.core.design.SectionLabel
import com.trippin.core.design.StatusPill
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTextField
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.currencySymbol
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.parseStatedAmount
import com.trippin.core.network.PerTravellerCostDto
import com.trippin.core.network.TravellerDto
import com.trippin.core.network.TripOptionDto

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupScreen(
    tripId: String,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    var editor by remember { mutableStateOf<EditorTarget?>(null) }

    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            viewModel.consumeShareUrl()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join the group on Tripp'in: $url")
            }
            context.startActivity(Intent.createChooser(send, "Invite the group"))
        }
    }

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = "Group",
                subtitle = "Who is going, and what each of them wants",
                actions = {
                    TrippinIconButton(Icons.Default.Share, "Invite", tint = colors.accent, onClick = viewModel::createShareLink)
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CardListSkeleton(rows = 2, label = "Loading the group")
                state.travellers.isEmpty() && state.error != null -> MessageState(
                    icon = Icons.Default.GroupAdd,
                    title = "Could not load the group",
                    body = state.error ?: "",
                    actionLabel = "Retry",
                    onAction = { viewModel.refresh() },
                    titleColor = colors.danger
                )
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.options.isNotEmpty()) {
                            item { SectionLabel("Cost options") }
                            items(state.options, key = { it.id }) { option -> OptionCard(option) }
                        }

                        item { SectionLabel("Travellers", Modifier.padding(top = 4.dp)) }
                        if (state.travellers.isEmpty()) {
                            item {
                                Text("No one has been added yet. Add the people going, or share an invite link.", style = TrippinType.Body, color = colors.inkMuted)
                            }
                        }
                        items(state.travellers, key = { it.id }) { traveller ->
                            TravellerCard(
                                traveller = traveller,
                                cost = state.perTravellerCost.firstOrNull { it.travellerId == traveller.id },
                                currency = state.currency,
                                onEdit = { editor = EditorTarget(traveller) },
                                onDelete = { viewModel.deleteTraveller(traveller.id) }
                            )
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            TrippinButton(
                                text = "Add a traveller",
                                onClick = { editor = EditorTarget(null) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Default.PersonAdd,
                                enabled = !state.busy
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    editor?.let { target ->
        TravellerEditor(
            target = target,
            currency = state.currency,
            busy = state.busy,
            onDismiss = { editor = null },
            onSave = { name, cap, interests, dislikes, pace ->
                if (target.existing == null) {
                    viewModel.addTraveller(name, cap, interests, dislikes, pace)
                } else {
                    viewModel.updateTraveller(target.existing.id, name, cap, interests, dislikes, pace)
                }
                editor = null
            }
        )
    }
}

private data class EditorTarget(val existing: TravellerDto?)

@Composable
private fun OptionCard(option: TripOptionDto) {
    val colors = TrippinTheme.colors
    TrippinCard {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(option.objective.replaceFirstChar { it.uppercase() }, style = TrippinType.Label, color = colors.ink)
                if (option.isFloor) StatusPill("Floor", PillTone.NEUTRAL)
            }
            if (option.headline.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(option.headline, style = TrippinType.Body, color = colors.inkMuted)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatStatedAmount(option.totalMin, option.currency)} to ${formatStatedAmount(option.totalMax, option.currency)}",
                style = TrippinType.NumericSmall,
                color = colors.ink
            )
        }
    }
}

@Composable
private fun TravellerCard(
    traveller: TravellerDto,
    cost: PerTravellerCostDto?,
    currency: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = TrippinTheme.colors
    TrippinCard {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(traveller.name, style = TrippinType.Heading, color = colors.ink, modifier = Modifier.weight(1f))
                TrippinIconButton(Icons.Default.Edit, "Edit ${traveller.name}", onEdit)
                TrippinIconButton(Icons.Default.Delete, "Remove ${traveller.name}", onDelete, tint = colors.danger)
            }
            traveller.budgetCap?.let { cap ->
                Text("Cap ${formatStatedAmount(cap, currency)}", style = TrippinType.Caption, color = colors.inkMuted)
            }
            if (cost != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Their share ${formatStatedAmount(cost.shareMin, currency)} to ${formatStatedAmount(cost.shareMax, currency)}",
                        style = TrippinType.Label,
                        color = if (cost.overCap) colors.warn else colors.ink
                    )
                    if (cost.overCap) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill("Over cap", PillTone.WARN)
                    }
                }
            }
            if (traveller.interests.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(traveller.interests.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }, style = TrippinType.Caption, color = colors.inkMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravellerEditor(
    target: EditorTarget,
    currency: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, cap: Double?, interests: List<String>, dislikes: List<String>, pace: String?) -> Unit
) {
    val colors = TrippinTheme.colors
    val existing = target.existing
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var cap by remember { mutableStateOf(existing?.budgetCap?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    val interests = remember { mutableStateListOf<String>().apply { addAll(existing?.interests.orEmpty()) } }
    val dislikes = remember { mutableStateListOf<String>().apply { addAll(existing?.dislikes.orEmpty()) } }
    var pace by remember { mutableStateOf(existing?.pace) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = colors.panel,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
        title = { Text(if (existing == null) "Add a traveller" else "Edit ${existing.name}", style = TrippinType.Heading) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TrippinTextField(name, { name = it }, Modifier.fillMaxWidth(), label = "Name")
                Spacer(Modifier.height(10.dp))
                TrippinTextField(
                    value = cap,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' || c == ',' }) cap = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Budget cap (${currencySymbol(currency)})",
                    placeholder = "Optional",
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(Modifier.height(12.dp))
                Text("Interests", style = TrippinType.Caption, color = colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InterestWords.forEach { word ->
                        TrippinChoiceChip(word.replaceFirstChar { it.uppercase() }, word in interests) {
                            if (word in interests) interests.remove(word) else interests.add(word)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Pace", style = TrippinType.Caption, color = colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TravellerPaceChoices.forEach { (code, label) ->
                        TrippinChoiceChip(label, pace == code) { pace = if (pace == code) null else code }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank(),
                onClick = { onSave(name, parseStatedAmount(cap), interests.toList(), dislikes.toList(), pace) }
            ) { Text(if (busy) "Saving..." else "Save", style = TrippinType.Label, color = colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", style = TrippinType.Label, color = colors.inkMuted) }
        }
    )
}
