package com.trippin.feature.itinerary

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trippin.core.design.PlacePlate
import com.trippin.core.design.TrippinSurface
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.isStreetLevelAddress
import com.trippin.core.design.photoCredit
import com.trippin.core.design.rememberCommitHaptic
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.VerificationCheckDto
import java.util.Locale

/** An amount together with the currency the stop declares, or null when it may not be shown. */
internal data class StatedMoney(val value: Double, val currency: String)

internal fun ActivityDto.statedMoney(): StatedMoney? {
    val amount = estimatedCost ?: return null
    if (amount <= 0.0) return null
    val code = currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
    return StatedMoney(amount, code)
}

/**
 * One stop, as a card. Shows the real photograph when there is one and a named plate when there is
 * not, the time window, the amount only in the currency the stop declares, why it is here, an address
 * only when it is navigable, and the verification receipt: the provenance of every claim on the card.
 */
@Composable
fun ActivityCard(
    activity: ActivityDto,
    index: Int,
    visited: Boolean,
    onToggleVisited: () -> Unit,
    destinationName: String?,
    modifier: Modifier = Modifier
) {
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberCommitHaptic()

    val photoUrl = activity.effectivePhotoUrl
    val rawAddress = activity.place?.formattedAddress.orEmpty()
    val address = rawAddress.takeIf { isStreetLevelAddress(it) }
    val coordinates = activity.place?.location?.let { p ->
        if (p.latitude == 0.0 && p.longitude == 0.0) null
        else String.format(Locale.US, "%.5f, %.5f", p.latitude, p.longitude)
    }
    val canNavigate = address != null || coordinates != null

    Column(modifier) {
        if (activity.travelTimeFromPreviousMinutes > 0) {
            Row(
                Modifier.padding(start = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(colors.line, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${activity.travelTimeFromPreviousMinutes} min from the stop before",
                    style = TrippinType.Caption,
                    color = colors.inkMuted
                )
            }
        }

        TrippinSurface(
            shape = TrippinTheme.shapes.card,
            background = if (visited) colors.goodSurface else colors.panel
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = activity.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                                .clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp))
                        )
                        photoCredit(photoUrl)?.let { credit ->
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                    .background(colors.ink.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(credit, style = TrippinType.Caption, color = colors.paper)
                            }
                        }
                    } else {
                        PlacePlate(title = activity.title, category = activity.type, height = 150.dp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.line))

                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .border(1.5.dp, colors.line, RoundedCornerShape(4.dp))
                                    .background(colors.accent, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(String.format(Locale.US, "%02d", index), style = TrippinType.Caption, color = colors.onAccent)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${activity.startTime} - ${activity.endTime}", style = TrippinType.NumericSmall, color = colors.ink)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            activity.statedMoney()?.let { money ->
                                Text(formatStatedAmount(money.value, money.currency), style = TrippinType.NumericSmall, color = colors.ink)
                                Spacer(Modifier.width(8.dp))
                            }
                            VisitedToggle(visited) { haptic(); onToggleVisited() }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(activity.title, style = TrippinType.Heading, color = colors.ink)

                    if (!activity.reason.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(activity.reason, style = TrippinType.Body, color = colors.ink)
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            address != null -> address
                            coordinates != null -> "No street address in the map data. It sits at $coordinates."
                            else -> "No street address in the map data for this venue."
                        },
                        style = TrippinType.Body,
                        color = colors.inkMuted
                    )

                    if (activity.checks.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        VerificationReceipt(activity.checks)
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        val copyable = address ?: coordinates
                        if (copyable != null) {
                            com.trippin.core.design.TrippinIconButton(
                                icon = Icons.Default.ContentCopy,
                                contentDescription = "Copy address",
                                onClick = {
                                    clipboard.setText(AnnotatedString(copyable))
                                    haptic()
                                    Toast.makeText(context, if (address != null) "Address copied" else "Coordinates copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (canNavigate) {
                            com.trippin.core.design.TrippinOutlineButton(
                                text = "Take me there",
                                onClick = { launchMaps(context, activity, destinationName) },
                                leadingIcon = Icons.Default.Directions,
                                modifier = Modifier.width(180.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitedToggle(visited: Boolean, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    Box(
        Modifier
            .border(1.dp, if (visited) colors.good else colors.line, RoundedCornerShape(6.dp))
            .background(if (visited) colors.goodSurface else colors.panel, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (visited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Mark visited",
                tint = if (visited) colors.good else colors.ink,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(if (visited) "Visited" else "Mark visited", style = TrippinType.Caption, color = if (visited) colors.good else colors.ink)
        }
    }
}

/**
 * The verification receipt: one line per check the deterministic validator attached, so a person can
 * see where each claim came from and whether it was confirmed, estimated, or left unchecked.
 */
@Composable
fun VerificationReceipt(checks: List<VerificationCheckDto>) {
    val colors = TrippinTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, colors.line, RoundedCornerShape(8.dp))
            .background(colors.panelAlt, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("VERIFICATION", style = TrippinType.Caption, color = colors.inkMuted)
        checks.forEach { check ->
            val (icon, tint) = statusVisual(check.status, colors.good, colors.warn, colors.inkMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = check.status, tint = tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(check.label, style = TrippinType.Label, color = colors.ink)
                    val detail = listOfNotNull(check.source.takeIf { it.isNotBlank() }, check.details?.takeIf { it.isNotBlank() })
                        .joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(detail, style = TrippinType.Caption, color = colors.inkMuted)
                    }
                }
            }
        }
    }
}

private fun statusVisual(status: String, good: Color, warn: Color, muted: Color): Pair<ImageVector, Color> =
    when (status.lowercase()) {
        "confirmed" -> Icons.Default.Verified to good
        "estimated" -> Icons.Default.WarningAmber to warn
        else -> Icons.Default.HelpOutline to muted
    }

private fun launchMaps(context: android.content.Context, activity: ActivityDto, destinationName: String?) {
    val point = activity.place?.location
    val exact = point?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
        ?.let { String.format(Locale.US, "%f,%f", it.latitude, it.longitude) }
    val query = Uri.encode(exact ?: listOfNotNull(activity.title, destinationName).joinToString(" "))
    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
