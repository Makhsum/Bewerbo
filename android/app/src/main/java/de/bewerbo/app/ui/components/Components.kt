package de.bewerbo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bewerbo.app.ui.theme.CardElevation
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

// ---------------------------------------------------------------------------------------------
// The components the design system is actually made of. "Professional" is mostly these: a card
// with one elevation step, a status pill that is not a chip, a meter that reads as a measurement.
// ---------------------------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalSemanticColors.current.muted,
        modifier = modifier,
    )
}

@Composable
fun BewerboCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(Space.m), content = content)
    }
}

/// A status pill. Deliberately not a chip: a chip is something you tap, a pill is something the
/// app is telling you. Keeping them apart is half of why an interface reads as considered.
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val colors = LocalSemanticColors.current
    val (fg, bg) = when (tone) {
        PillTone.Success -> colors.success to colors.successTint
        PillTone.Attention -> colors.attention to colors.attentionTint
        PillTone.Danger -> colors.danger to colors.dangerTint
        PillTone.Accent -> colors.accent to colors.accentTint
        PillTone.Neutral -> colors.muted to MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
        )
    }
}

enum class PillTone { Success, Attention, Danger, Accent, Neutral }

/// A labelled row with an icon, a title and an optional trailing pill. The building block most of
/// the app's lists are made of.
@Composable
fun IconRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    tone: PillTone = PillTone.Accent,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalSemanticColors.current
    val tint = when (tone) {
        PillTone.Success -> colors.success
        PillTone.Attention -> colors.attention
        PillTone.Danger -> colors.danger
        PillTone.Accent -> colors.accent
        PillTone.Neutral -> colors.muted
    }
    val tintBg = when (tone) {
        PillTone.Success -> colors.successTint
        PillTone.Attention -> colors.attentionTint
        PillTone.Danger -> colors.dangerTint
        PillTone.Accent -> colors.accentTint
        PillTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = (if (onClick != null) modifier.clickable(onClick = onClick) else modifier)
            .fillMaxWidth()
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(tintBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = Space.s),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        trailing?.invoke()
    }
}

/// A horizontal meter. Used wherever a number is a proportion — completeness, gaps explained,
/// requirements covered — so the same quantity always looks the same.
@Composable
fun Meter(
    label: String,
    value: String,
    fraction: Float,
    tone: PillTone = PillTone.Success,
    modifier: Modifier = Modifier,
    /// True when there is genuinely nothing to measure yet — "0 of 0". Drawn as an empty track,
    /// because a full green bar over "0 / 0" tells the user they have finished something they have
    /// not started.
    empty: Boolean = false,
) {
    val colors = LocalSemanticColors.current
    val bar = when (tone) {
        PillTone.Attention -> colors.attention
        PillTone.Danger -> colors.danger
        PillTone.Accent -> colors.accent
        PillTone.Neutral -> colors.muted
        PillTone.Success -> colors.success
    }
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .padding(top = Space.xs)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (empty) 0f else fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(bar),
            )
        }
    }
}

/// The readiness ring: one number, and the arc that says how far along it is.
@Composable
fun ReadinessRing(value: Int, caption: String, modifier: Modifier = Modifier) {
    val colors = LocalSemanticColors.current
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier
            .size(96.dp)
            .semantics { contentDescription = "$value / 100" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(96.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ring, startAngle = -90f, sweepAngle = 360f * (value / 100f),
                useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.headlineLarge, color = ring)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

/// A segmented control. One choice out of a small fixed set, shown in full rather than hidden in
/// a dropdown — which is the point when the choice changes what the document says.
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(3.dp)) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shape = MaterialTheme.shapes.extraSmall,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (tagPrefix != null) {
                                Modifier.testTag("${tagPrefix}_${option.lowercase()}")
                            } else Modifier,
                        )
                        .clickable { onSelect(index) },
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            LocalSemanticColors.current.muted
                        },
                        modifier = Modifier.padding(vertical = Space.s),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/// A labelled field with its value and a focus ring — the app's text input, rather than a bare
/// OutlinedTextField with a floating label.
@Composable
fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

/// A callout — the app explaining something, with an icon and no action.
@Composable
fun Callout(
    icon: ImageVector,
    title: String,
    body: String,
    tone: PillTone = PillTone.Accent,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSemanticColors.current
    val (fg, bg) = when (tone) {
        PillTone.Success -> colors.success to colors.successTint
        PillTone.Attention -> colors.attention to colors.attentionTint
        PillTone.Danger -> colors.danger to colors.dangerTint
        else -> colors.accent to colors.accentTint
    }

    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.m)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = Space.s)) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}

@Composable
fun Gutter(height: androidx.compose.ui.unit.Dp = Space.m) {
    Box(Modifier.height(height).width(1.dp))
}

/**
 * Marks a subtree as exposing its test tags as Android resource-ids.
 *
 * The root sets this once for the app, but a dialog and a dropdown menu render in their OWN
 * windows and inherit nothing from it — so without this on each of them, nothing inside has a
 * resource-id and no driver can reach it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.exposeTestTags(): Modifier =
    this.semantics { testTagsAsResourceId = true }
