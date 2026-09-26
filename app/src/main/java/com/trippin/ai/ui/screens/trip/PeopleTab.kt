package com.trippin.ai.ui.screens.trip

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.trippin.ai.data.model.Member
import com.trippin.ai.data.model.Role
import com.trippin.ai.data.repository.TripRepository
import com.trippin.ai.ui.components.Avatar
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.Segmented
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.ago
import com.trippin.ai.ui.components.countdown
import com.trippin.ai.ui.theme.Trip

/** Who's on the trip, their role, the invite link, and the (never silent) ways to leave. */
@Composable
fun PeopleTab(vm: TripViewModel, ui: TripUi, onPrefs: () -> Unit, onDeleted: () -> Unit) {
    val trip = ui.trip ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmRemove by remember { mutableStateOf<Member?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionTitle("On the trip (${ui.members.size})")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ui.members.forEachIndexed { i, m ->
                    MemberRow(
                        m = m, isMe = m.uid == vm.me.uid, canManage = vm.canManage,
                        onSetRole = { r -> vm.setRole(m, r) },
                        onRemove = { confirmRemove = m },
                    )
                    if (i < ui.members.lastIndex) HorizontalDivider(color = Trip.PaperDeep)
                }
            }
        }

        item {
            SectionTitle("Invite")
            if (trip.inviteActive()) {
                Panel {
                    Text(TripRepository.inviteLink(trip.inviteCode!!), style = MaterialTheme.typography.bodyLarge)
                    Text(countdown(trip.inviteExpiresAt ?: 0), color = Trip.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Copy", onClick = { clipboard.setText(AnnotatedString(TripRepository.inviteLink(trip.inviteCode!!))) })
                        SmallButton("Share", onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join us on Trippin' — \"${trip.title}\": ${TripRepository.inviteLink(trip.inviteCode!!)}")
                            }
                            context.startActivity(Intent.createChooser(send, "Invite to ${trip.title}"))
                        })
                        if (vm.canManage) SmallButton("Turn off", onClick = { vm.revokeInvite() }, color = Trip.Danger)
                    }
                }
            } else if (vm.canManage) {
                Panel {
                    Text("No active invite link.", color = Trip.Muted)
                    SmallButton(if (trip.isGroup) "Create invite link" else "Invite friends", onClick = { vm.createInvite() })
                }
            } else {
                Panel { Text("Ask an owner or admin for an invite link.", color = Trip.Muted) }
            }
        }

        item {
            SectionTitle("Your preferences")
            Panel(onClick = onPrefs) {
                Text("Diet, pace, walking limit and more — the planner reads these for you specifically.", color = Trip.Muted)
            }
        }

        item {
            SectionTitle("Leave or delete")
            if (ui.roleOf(vm.me.uid) == Role.OWNER) {
                SmallButton("Delete this trip", onClick = { confirmDelete = true }, color = Trip.Danger)
            } else {
                SmallButton("Leave this trip", onClick = { confirmLeave = true }, color = Trip.Danger)
            }
        }
    }

    confirmRemove?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${m.name}?") },
            text = { Text("They'll lose access to this trip and will need a new invite to rejoin.") },
            confirmButton = { TextButton(onClick = { vm.remove(m); confirmRemove = null }) { Text("Remove", color = Trip.Danger) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this trip?") },
            text = { Text("You'll need a new invite to rejoin.") },
            confirmButton = { TextButton(onClick = { vm.leave(); confirmLeave = false; onDeleted() }) { Text("Leave", color = Trip.Danger) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trip?") },
            text = { Text("This removes it for everyone — ideas, votes, the plan, everything. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDeleted) }) { Text("Delete", color = Trip.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MemberRow(m: Member, isMe: Boolean, canManage: Boolean, onSetRole: (Role) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(m.name, m.uid)
            Column(Modifier.weight(1f)) {
                Text((m.name) + (if (isMe) " (you)" else ""), style = MaterialTheme.typography.titleMedium)
                Text("Joined ${ago(m.joinedAt)}", style = MaterialTheme.typography.labelSmall, color = Trip.Muted)
            }
            Pill(m.role.label.uppercase(), if (m.role == Role.OWNER) Trip.Signal else Trip.PaperDeep, if (m.role == Role.OWNER) Trip.Ink else Trip.Muted)
            if (canManage && !isMe && m.role != Role.OWNER) {
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove ${m.name}", tint = Trip.Danger) }
            }
        }
        if (canManage && !isMe && m.role != Role.OWNER) {
            Segmented(
                options = listOf(Role.ADMIN, Role.MEMBER, Role.VIEWER),
                selected = m.role,
                label = { it.label },
                onSelect = onSetRole,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
