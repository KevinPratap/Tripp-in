package com.trippin.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * One rounding scale for the whole app. A card, a button, a chip and a field each name the role they
 * play here rather than repeating a `RoundedCornerShape(8.dp)` literal at every call site, which is
 * how two controls that should match quietly drift apart.
 */
@Immutable
data class TrippinShapes(
    val card: RoundedCornerShape = RoundedCornerShape(14.dp),
    val button: RoundedCornerShape = RoundedCornerShape(12.dp),
    val field: RoundedCornerShape = RoundedCornerShape(12.dp),
    val chip: RoundedCornerShape = RoundedCornerShape(10.dp),
    val badge: RoundedCornerShape = RoundedCornerShape(6.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
)
