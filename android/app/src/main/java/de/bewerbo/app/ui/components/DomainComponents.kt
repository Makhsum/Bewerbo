package de.bewerbo.app.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.Gap
import de.bewerbo.app.data.Requirement
import de.bewerbo.app.data.TimelinePeriod
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/**
 * The Zeitstrahl: education and work as two lanes, with unexplained gaps drawn as hatched blocks.
 *
 * Drawing it is the point. A gap written as a sentence is a fact the user can skip past; a gap
 * drawn as a hole between two bars is a thing they want to fill.
 */
@Composable
fun Timeline(
    firstYear: Int,
    lastYear: Int,
    periods: List<TimelinePeriod>,
    gaps: List<Gap>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSemanticColors.current
    val education = MaterialTheme.colorScheme.secondary
    val work = MaterialTheme.colorScheme.primary
    val gapColor = colors.attention
    val axis = MaterialTheme.colorScheme.outline

    val span = (lastYear - firstYear).coerceAtLeast(1).toFloat()

    fun yearOf(date: String): Float {
        // "2023-09-01" — the fraction of the year matters, a bar that snaps to January is a lie.
        val year = date.take(4).toIntOrNull() ?: firstYear
        val month = date.drop(5).take(2).toIntOrNull() ?: 1
        return (year + (month - 1) / 12f - firstYear) / span
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(66.dp),
        ) {
            val laneHeight = 20.dp.toPx()
            val laneGap = 6.dp.toPx()
            val radius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())

            fun laneTop(kind: String) = if (kind == "ausbildung") 0f else laneHeight + laneGap

            periods.forEach { period ->
                val x0 = yearOf(period.from) * size.width
                val x1 = yearOf(period.to) * size.width
                drawRoundRect(
                    color = if (period.kind == "ausbildung") education else work,
                    topLeft = Offset(x0, laneTop(period.kind)),
                    size = Size((x1 - x0).coerceAtLeast(6.dp.toPx()), laneHeight),
                    cornerRadius = radius,
                )
            }

            // Gaps are hatched rather than filled: a solid block would read as another period.
            gaps.forEach { gap ->
                val x0 = yearOf(gap.from) * size.width
                val x1 = yearOf(gap.to) * size.width
                val width = (x1 - x0).coerceAtLeast(6.dp.toPx())
                val top = laneHeight + laneGap
                drawRoundRect(
                    color = gapColor.copy(alpha = 0.22f),
                    topLeft = Offset(x0, top),
                    size = Size(width, laneHeight),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = gapColor,
                    topLeft = Offset(x0, top),
                    size = Size(width, laneHeight),
                    cornerRadius = radius,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                        ),
                    ),
                )
            }

            val axisY = (laneHeight * 2) + laneGap + 8.dp.toPx()
            drawLine(axis, Offset(0f, axisY), Offset(size.width, axisY), strokeWidth = 1.dp.toPx())
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$firstYear", style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Text("$lastYear", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }

        Row(Modifier.padding(top = Space.s)) {
            // These three were Kotlin string literals, which put them outside strings.xml
            // altogether: not translatable, and invisible to EmojiFreeStringsTest, which only
            // scans values*/strings.xml. They stay German — they are the words the Zeitstrahl
            // labels in the document — but they are now declared where every other string is.
            LegendDot(education, stringResource(R.string.timeline_legend_education))
            Box(Modifier.size(Space.m))
            LegendDot(work, stringResource(R.string.timeline_legend_work))
            Box(Modifier.size(Space.m))
            LegendDot(gapColor, stringResource(R.string.timeline_legend_gap))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, MaterialTheme.shapes.extraSmall),
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LocalSemanticColors.current.muted,
            modifier = Modifier.padding(start = Space.xs),
        )
    }
}

/**
 * The posting's text with the spans the parser read underlined, each carrying its marker number.
 *
 * A span is only drawn where the server found the quote verbatim. Where it did not, the field is
 * simply shown without a highlight — a wrong highlight would tell the user the value came from
 * words it did not come from, which is worse than telling them nothing.
 */
@Composable
fun EvidenceText(
    source: String,
    spans: List<Triple<Int, Int, Int>>,
    modifier: Modifier = Modifier,
) {
    val accent = LocalSemanticColors.current.accent
    val highlight = LocalSemanticColors.current.accentTint

    val text = buildAnnotatedString {
        append(source)
        spans.forEach { (start, length, _) ->
            if (start >= 0 && start + length <= source.length) {
                addStyle(
                    SpanStyle(
                        background = highlight,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                    start, start + length,
                )
            }
        }
    }

    Text(text, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

/**
 * One requirement of the Anforderungsabgleich, in one of exactly three states.
 *
 * "nicht belegt" is stated plainly rather than hidden, because that is the decision the screen
 * exists to make visible: what is listed here as not proven does not appear in the letter.
 */
@Composable
fun RequirementRow(
    requirement: Requirement,
    index: Int,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSemanticColors.current
    val (icon, tint) = when (requirement.state) {
        "belegt" -> BewerboIcons.Covered to colors.success
        "offen" -> BewerboIcons.Attention to colors.attention
        else -> BewerboIcons.NotClaimed to colors.muted
    }

    Column(
        modifier
            .fillMaxWidth()
            .testTag("match_requirement_$index")
            .padding(vertical = Space.s),
    ) {
        Row {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = Space.s)) {
                Text(requirement.text, style = MaterialTheme.typography.titleMedium)
                val detail = requirement.evidence.ifBlank { requirement.action }
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (requirement.state == "offen" && requirement.action.isNotBlank() && onAction != null) {
                    Box(
                        Modifier
                            .padding(top = Space.s)
                            .testTag("match_requirement_action_$index")
                            .clickable(onClick = onAction)
                            .background(colors.attentionTint, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = Space.s, vertical = Space.xs),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                BewerboIcons.Anlagen, contentDescription = null,
                                tint = colors.attention, modifier = Modifier.size(16.dp),
                            )
                            Text(
                                requirement.action,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.attention,
                                modifier = Modifier.padding(start = Space.xs),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The DIN 5008 inspector drawn over the letter preview: the Anschriftenfeld at 45 mm, the
 * Faltmarke at 87 mm, the right-aligned date band.
 *
 * It makes the norm checkable instead of taken on trust. The same measurements are the ones the
 * renderer uses — see Rendering/DocumentTheme.cs — so what is drawn here is what is on the page.
 */
@Composable
fun DinOverlay(modifier: Modifier = Modifier) {
    val accent = LocalSemanticColors.current.accent

    Canvas(modifier.fillMaxWidth()) {
        val pageHeight = size.height
        val pageWidth = size.width
        fun mmY(mm: Float) = pageHeight * (mm / 297f)
        fun mmX(mm: Float) = pageWidth * (mm / 210f)

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))

        // Anschriftenfeld: 45 mm from the top, 85 mm wide, 45 mm tall.
        drawRect(
            color = accent,
            topLeft = Offset(mmX(24.1f), mmY(45f)),
            size = Size(mmX(85f), mmY(45f)),
            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
        )

        // Faltmarke 87 mm, Lochmarke 148.5 mm, Faltmarke 192 mm.
        listOf(87f, 148.5f, 192f).forEach { mm ->
            drawLine(
                color = accent,
                start = Offset(mmX(4f), mmY(mm)),
                end = Offset(mmX(14f), mmY(mm)),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // The Informationsblock band, right-aligned.
        drawRect(
            color = accent.copy(alpha = 0.5f),
            topLeft = Offset(mmX(130f), mmY(98f)),
            size = Size(mmX(56f), mmY(8f)),
            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
        )
    }
}
