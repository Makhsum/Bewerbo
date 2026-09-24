package de.bewerbo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.AtsFinding
import de.bewerbo.app.data.ErrorMessage
import de.bewerbo.app.data.Gap
import de.bewerbo.app.data.NextStep
import de.bewerbo.app.data.Requirement
import de.bewerbo.app.data.ReviewCheck
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

    // The axis runs to the END of lastYear, not to its January. Dividing by the difference of the
    // two years put every bar that stops later than January of the last one past 1.0 — a position
    // held until 09/2026 in a 2019 – 2026 chart landed at 1.10 and was drawn off the right edge of
    // the card, clipped.
    val span = (lastYear - firstYear + 1).coerceAtLeast(1).toFloat()

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
            // scans values*/strings.xml. They are declared where every other string is now, and
            // translated with them: "Ausbildung", "Beruf" and "Lücke" are words for a timeline,
            // not words a posting uses, so the rule in ui/GermanTerms.kt does not keep them.
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

/// One passage of the posting and the number that ties it to the field it produced. The number is
/// handed out by the screen, not by the server: it is a reading aid for this one list of fields.
data class EvidenceMark(val start: Int, val length: Int, val number: Int)

/**
 * The posting's text with the passages the parser read underlined, each carrying its marker number.
 *
 * The number is the whole point: a highlight on its own says "something was read here", and the
 * user still cannot tell which of the six fields below came out of it. Tapping a passage names its
 * field; the field, selected, is what makes the other passages step back.
 *
 * A passage is only drawn where the server found the quote verbatim. Where it did not, the field is
 * simply shown without a highlight — a wrong highlight would tell the user the value came from
 * words it did not come from, which is worse than telling them nothing.
 */
@Composable
fun EvidenceText(
    source: String,
    spans: List<EvidenceMark>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /// Where each passage sits, in pixels from the top of this text. The screen needs it to scroll
    /// to the passage itself; only the laid-out text knows which line a passage ended up on.
    onPassagesLaidOut: (Map<Int, Int>) -> Unit = {},
) {
    val colors = LocalSemanticColors.current
    val accent = colors.accent
    val highlight = colors.accentTint
    val muted = colors.muted

    val marked = remember(source, spans) { markUp(source, spans) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val text = buildAnnotatedString {
        append(marked.text)
        // Words first and markers after, in two passes rather than one per passage: a passage that
        // sits inside another would otherwise have the outer one's style laid back over its number.
        // Within the first pass the selected passage goes last, so it keeps its colours where the
        // two overlap.
        marked.passages.sortedBy { it.number == selected }.forEach { passage ->
            // With one passage selected the rest step back rather than vanish: the user is checking
            // one field, not losing sight of what else was read.
            val faded = selected != null && passage.number != selected
            addStyle(
                SpanStyle(
                    background = if (faded) highlight.copy(alpha = 0.4f) else highlight,
                    color = if (faded) muted else accent,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (faded) TextDecoration.None else TextDecoration.Underline,
                ),
                passage.words.first, passage.words.last + 1,
            )
        }
        marked.passages.forEach { passage ->
            val faded = selected != null && passage.number != selected
            addStyle(
                SpanStyle(
                    background = Color.Transparent,
                    color = if (faded) muted else accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = MarkerSize,
                    baselineShift = BaselineShift.Superscript,
                    textDecoration = TextDecoration.None,
                ),
                passage.marker.first, passage.marker.last + 1,
            )
        }
    }

    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        onTextLayout = { result ->
            layout = result
            onPassagesLaidOut(
                marked.passages.associate { it.number to result.getBoundingBox(it.words.first).top.toInt() },
            )
        },
        // Keyed on the marked-up text rather than on the selection: the tappable ranges move only
        // when the posting or its passages change, and restarting the gesture detector on every
        // selection would drop the tap that caused it.
        modifier = modifier.pointerInput(marked) {
            detectTapGestures { position ->
                val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                // The innermost passage wins where they are nested: tapping the employer's name
                // inside the headline means the employer, not the headline that contains it.
                marked.passages
                    .filter { offset >= it.words.first && offset <= it.marker.last }
                    .minByOrNull { it.marker.last - it.words.first }
                    ?.let { onSelect(it.number) }
            }
        },
    )
}

/// Where a passage sits in the marked-up text: the words themselves, and the number spliced in
/// behind them.
private data class MarkedPassage(val number: Int, val words: IntRange, val marker: IntRange)

private data class MarkedSource(val text: String, val passages: List<MarkedPassage>)

/**
 * Splices the marker numbers into the source text.
 *
 * It happens in one pass, before any styling, because every digit inserted moves everything after
 * it along: keeping the offsets in a single place is what stops a number being drawn over the
 * wrong words.
 *
 * Passages may sit inside one another, and an advert that names the employer inside its headline
 * — "Die Nordwind Pflege GmbH sucht eine Pflegefachkraft" — produces exactly that. So the source is
 * walked character by character rather than passage by passage: a passage that opens inside another
 * one still gets its own number, because a field the server DID locate and the screen then declines
 * to point at is the very thing this screen exists to prevent.
 */
private fun markUp(source: String, spans: List<EvidenceMark>): MarkedSource {
    val drawable = spans
        .filter { it.start >= 0 && it.length > 0 && it.start + it.length <= source.length }
        .sortedWith(compareBy({ it.start }, { it.number }))
    if (drawable.isEmpty()) return MarkedSource(source, emptyList())

    val opens = drawable.groupBy { it.start }
    val closes = drawable.groupBy { it.start + it.length }

    val out = StringBuilder(source.length + drawable.size * 2)
    val wordsFrom = mutableMapOf<Int, Int>()
    val passages = mutableListOf<MarkedPassage>()

    for (i in 0..source.length) {
        // Close before open, so the number of a passage that ends here is not swallowed by the
        // words of the next one, which begins at the very same character.
        closes[i]?.forEach { mark ->
            val markerFrom = out.length
            out.append(mark.number)
            passages += MarkedPassage(
                mark.number,
                (wordsFrom[mark.number] ?: markerFrom) until markerFrom,
                markerFrom until out.length,
            )
        }
        opens[i]?.forEach { wordsFrom[it.number] = out.length }
        if (i < source.length) out.append(source[i])
    }

    return MarkedSource(out.toString(), passages.sortedBy { it.words.first })
}

/// The marker rides above the line, so it has to be small enough not to open the line spacing.
private val MarkerSize = 9.sp

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
                // The action stands in as the detail line only where it is NOT a button: a
                // nicht_belegt row whose "action" is a fact about the profile ("Im Profil steht
                // B1") has nowhere else to say it. Where the action IS a button, printing it
                // above the button says the same words twice.
                val actionIsButton =
                    requirement.state == "offen" && requirement.action.isNotBlank() && onAction != null
                val detail = requirementEvidence(requirement)
                    .ifBlank { if (actionIsButton) "" else requirementAction(requirement) }
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (actionIsButton) {
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
                                requirementAction(requirement),
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

/// What an application's state is called on screen. The value keeps the backend's spelling — it is
/// what the status endpoint takes — so the Übersicht and the Bewerbung screen both have to look
/// the name up rather than print it. Same shape as `partLabel` on the Bewerbung screen; shared
/// because two screens draw the same five states.
///
/// Every state is named, and `else` is a NEUTRAL word rather than the last of them. It used to end
/// `else -> status_absage`, so a state this build does not know would have been shown to the user
/// as a rejection — the one label here that no application should ever carry by accident.
fun applicationStatusLabel(status: String) = when (status) {
    "Entwurf" -> R.string.status_entwurf
    "Versendet" -> R.string.status_versendet
    "Wartend" -> R.string.status_wartend
    "Einladung" -> R.string.status_einladung
    "Absage" -> R.string.status_absage
    else -> R.string.status_unbekannt
}

// ---------------------------------------------------------------------------------------------
// The outstanding steps. The server composes WHICH steps there are; it cannot compose the words,
// because it never learns the interface language — so it sends a kind and its arguments, and the
// sentence is written here. A kind this build does not know falls back to the German the server
// still carries, which is what the Übersicht showed for all of them until now.
// ---------------------------------------------------------------------------------------------

@Composable
fun nextStepTitle(step: NextStep): String = when (step.kind) {
    "person" -> stringResource(R.string.step_person_title)
    "gap" -> stringResource(R.string.step_gap_title, step.arg(0), step.arg(1))
    "anerkennung" -> stringResource(R.string.step_anerkennung_title)
    "anerkennung_offen" -> stringResource(R.string.step_anerkennung_offen_title)
    "sprachnachweis" -> stringResource(R.string.step_sprachnachweis_title, step.arg(1))
    "beruf" -> stringResource(R.string.step_beruf_title)
    "versand" -> stringResource(R.string.step_versand_title)
    "beleg" -> stringResource(R.string.step_beleg_title, step.arg(0))
    else -> step.title
}

@Composable
fun nextStepDetail(step: NextStep): String = when (step.kind) {
    "person" -> stringResource(R.string.step_person_detail, personItems(step.args))
    "gap" -> stringResource(R.string.step_gap_detail)
    "anerkennung" -> stringResource(R.string.step_anerkennung_detail, step.arg(0))
    "anerkennung_offen" -> stringResource(R.string.step_anerkennung_offen_detail, step.arg(0))
    "sprachnachweis" ->
        stringResource(R.string.step_sprachnachweis_detail, step.arg(0), step.arg(1))
    "beruf" -> stringResource(R.string.step_beruf_detail)
    "versand" -> stringResource(R.string.step_versand_detail)
    // A beleg step is raised for an "offen" requirement and only for that one — the requirement
    // stands in the profile and only the Nachweis is missing. The Abgleich says that once over the
    // group; on the Übersicht the row stands alone under an employer, so it says it for itself.
    // It used to fall through to step.detail, which carries the requirement's evidence — and for an
    // offen requirement that is empty by definition, so every one of these rows drew a blank line.
    "beleg" -> stringResource(R.string.step_beleg_detail)
    // The evidence of a requirement is quoted from the posting, so it is the one detail that is
    // German in every language on purpose.
    else -> step.detail
}

/// The missing parts of the Briefkopf, named in the user's language and joined as a list. The
/// server sends the keys rather than the words for exactly this reason.
@Composable
private fun personItems(args: List<String>): String {
    // A plain loop rather than joinToString: the lambda it takes is not composable, so the lookup
    // cannot happen inside one.
    val named = mutableListOf<String>()
    args.forEach { key ->
        named += when (key) {
            "name" -> stringResource(R.string.step_person_item_name)
            "anschrift" -> stringResource(R.string.step_person_item_anschrift)
            else -> stringResource(R.string.step_person_item_kontakt)
        }
    }
    return named.joinToString(", ")
}

/// What is still missing before the Anschreiben may be written, named in the user's language and
/// joined as a list. The first three keys are the ones [personItems] names — the Briefkopf is the
/// same set of fields — with the one position the letter needs something to say about added to
/// them. A plain loop for the same reason [personItems] uses one.
@Composable
fun letterBlockerItems(keys: List<String>): String {
    val named = mutableListOf<String>()
    keys.forEach { key ->
        named += when (key) {
            "name" -> stringResource(R.string.step_person_item_name)
            "anschrift" -> stringResource(R.string.step_person_item_anschrift)
            "kontakt" -> stringResource(R.string.step_person_item_kontakt)
            else -> stringResource(R.string.letter_blocked_item_beruf)
        }
    }
    return named.joinToString(", ")
}

/// The argument at [index], or an empty string — a step from an older server carries none, and a
/// missing one must not take the Übersicht down with it.
private fun NextStep.arg(index: Int): String = args.getOrElse(index) { "" }

// ---------------------------------------------------------------------------------------------
// The Abgleich, the Textprüfung and the Maschinenlesbarkeit. Same division as the steps above: the
// server finds WHAT is the case, the screen says it in the user's language. The requirement text
// itself, the Floskel list and the profile entries named as evidence are quoted rather than
// written, so they stay as they come.
// ---------------------------------------------------------------------------------------------

@Composable
fun requirementEvidence(requirement: Requirement): String = when (requirement.evidenceKind) {
    "beruf" -> {
        val period = if (requirement.arg(3).isBlank()) {
            stringResource(R.string.evidence_period_since, requirement.arg(2))
        } else {
            stringResource(R.string.evidence_period, requirement.arg(2), requirement.arg(3))
        }
        stringResource(R.string.evidence_beruf, requirement.arg(0), requirement.arg(1), period)
    }
    "ausbildung" -> stringResource(
        R.string.evidence_ausbildung, requirement.arg(0), requirement.arg(1),
    )
    "sprache_belegt" -> stringResource(
        R.string.evidence_sprache_belegt, requirement.arg(0), requirement.arg(1),
    )
    else -> requirement.evidence
}

@Composable
fun requirementAction(requirement: Requirement): String = when (requirement.actionKind) {
    "nachweis_ablegen" -> stringResource(R.string.action_file_nachweis)
    "niveau" -> stringResource(R.string.evidence_sprache_niveau, requirement.actionArg(0))
    else -> requirement.action
}

@Composable
fun reviewCheckTitle(check: ReviewCheck): String = when (check.key) {
    "floskeln" -> stringResource(
        if (check.detailKind == "floskeln_ok") R.string.review_floskeln_none
        else R.string.review_floskeln_found,
    )
    "maschinell" -> stringResource(R.string.review_maschinell)
    "form" -> stringResource(R.string.review_form)
    "perspektive" -> stringResource(R.string.review_perspektive)
    "anschreiben" -> stringResource(R.string.review_anschreiben)
    else -> check.title
}

@Composable
fun reviewCheckDetail(check: ReviewCheck): String = when (check.detailKind) {
    "floskeln_ok" -> stringResource(R.string.review_floskeln_ok_detail)
    "floskeln_gefunden" -> stringResource(R.string.review_floskeln_found_detail)
    "maschinell_niedrig" -> stringResource(R.string.review_maschinell_low)
    "maschinell_erhoeht" -> stringResource(R.string.review_maschinell_high, check.arg(0))
    "form_ok" -> stringResource(R.string.review_form_ok, check.arg(0), check.arg(1))
    "form_fehler" -> formFaults(check.detailArgs)
    "perspektive" -> stringResource(R.string.review_perspektive_detail, check.arg(0), check.arg(1))
    "anschreiben_ok" -> stringResource(R.string.review_anschreiben_ok)
    "anschreiben_fehler" -> stringResource(R.string.review_anschreiben_failed)
    else -> check.detail
}

@Composable
fun atsFindingLabel(finding: AtsFinding): String = when (finding.key) {
    "lesbar" -> stringResource(R.string.ats_lesbar)
    "name" -> stringResource(R.string.ats_name)
    "arbeitgeber" -> stringResource(R.string.ats_arbeitgeber)
    "zeitraeume" -> stringResource(R.string.ats_zeitraeume)
    "text" -> stringResource(R.string.ats_text)
    else -> finding.label
}

/// The checks the produced file FAILED, named in the user's language and joined as one line. A
/// plain loop rather than a joinToString for the same reason [personItems] uses one: the lambda it
/// takes is not composable, so [atsFindingLabel] cannot be called inside it.
@Composable
fun atsFailedLabels(findings: List<AtsFinding>): String {
    val named = mutableListOf<String>()
    findings.forEach { named += atsFindingLabel(it) }
    return named.joinToString("  ·  ")
}

/// The same for the Textprüfung — the checks of the letter itself that came back "fehler". Written
/// beside [atsFailedLabels] and in the same shape, because the export names both in one sentence.
@Composable
fun reviewFailedLabels(checks: List<ReviewCheck>): String {
    val named = mutableListOf<String>()
    checks.forEach { named += reviewCheckTitle(it) }
    return named.joinToString("  ·  ")
}

@Composable
fun atsFindingDetail(finding: AtsFinding): String = when (finding.detailKind) {
    // What WAS found is the user's own data: the name, the employers, the dates. It reads the
    // same in every language, so the kind says only that it was found.
    "gefunden" -> finding.arg(0)
    "fehlt" -> stringResource(R.string.ats_missing, finding.arg(0))
    "lesbar_fehler" -> stringResource(R.string.ats_lesbar_failed, finding.arg(0))
    "name_fehlt" -> stringResource(R.string.ats_name_missing)
    "name_keine" -> stringResource(R.string.ats_name_none, nameFields(finding.detailArgs))
    "arbeitgeber_keine" -> stringResource(R.string.ats_arbeitgeber_none)
    "zeitraeume_keine" -> stringResource(R.string.ats_zeitraeume_none)
    "zeitraeume_fehlt" -> stringResource(R.string.ats_zeitraeume_missing)
    "text_ok" -> stringResource(R.string.ats_text_ok, finding.arg(0))
    "text_fehlt" -> stringResource(R.string.ats_text_missing)
    else -> finding.detail
}

/// The halves of the name that are still empty, named in the user's language. A plain loop for the
/// same reason [personItems] uses one.
@Composable
private fun nameFields(keys: List<String>): String {
    val named = mutableListOf<String>()
    keys.forEach { key ->
        named += when (key) {
            "vorname" -> stringResource(R.string.ats_name_field_first)
            else -> stringResource(R.string.ats_name_field_last)
        }
    }
    return named.joinToString(", ")
}

/// The faults of the form check, named in the user's language and joined the way the German
/// sentence joined them. A plain loop for the same reason [personItems] uses one.
@Composable
private fun formFaults(keys: List<String>): String {
    val named = mutableListOf<String>()
    keys.forEach { key ->
        named += when (key) {
            "anrede" -> stringResource(R.string.review_form_anrede)
            "referenz" -> stringResource(R.string.review_form_referenz)
            "betreff" -> stringResource(R.string.review_form_betreff)
            "kurz" -> stringResource(R.string.review_form_short)
            else -> stringResource(R.string.review_form_long)
        }
    }
    return named.joinToString("  ·  ")
}

private fun Requirement.arg(index: Int): String = evidenceArgs.getOrElse(index) { "" }

private fun Requirement.actionArg(index: Int): String = actionArgs.getOrElse(index) { "" }

private fun ReviewCheck.arg(index: Int): String = detailArgs.getOrElse(index) { "" }

private fun AtsFinding.arg(index: Int): String = detailArgs.getOrElse(index) { "" }

/**
 * What the snackbar says about a failed call.
 *
 * Same division as the steps and the checks above: the server names the kind, the screen writes
 * the sentence. The German detail is the fallback for a kind this build does not know, which is
 * better than an empty snackbar — and better than what stood here before, the raw message of
 * whatever was thrown.
 */
@Composable
fun errorMessage(error: ErrorMessage): String = when (error.kind) {
    AppViewModel.UNREACHABLE -> stringResource(R.string.error_unreachable)
    AppViewModel.PHOTO_UNREADABLE -> stringResource(R.string.error_photo_unreadable)
    // The door's four refusals. The wrong-credentials one never says which of the two halves was
    // wrong, because the server does not say either — see AuthController for why.
    "email_invalid" -> stringResource(R.string.error_email_invalid)
    "password_too_short" -> stringResource(R.string.error_password_too_short)
    "email_taken" -> stringResource(R.string.error_email_taken)
    "credentials_rejected" -> stringResource(R.string.error_credentials_rejected)
    // Wrong, too old, or already spent — one sentence for all three, because the server refuses all
    // three the same way and the only thing the user can do about any of them is ask for a new code.
    "reset_code_rejected" -> stringResource(R.string.error_reset_code_rejected)
    // Three ways a link can fail and three things to do about it: correct the address, try again,
    // or paste the advert by hand after all.
    "link_not_an_address" -> stringResource(R.string.error_link_not_an_address)
    "link_unreachable" -> stringResource(R.string.error_link_unreachable)
    "link_no_text" -> stringResource(R.string.error_link_no_text)
    "profile_missing" -> stringResource(R.string.error_profile_missing)
    // The letter was asked for over a profile that cannot carry one. The Abgleich names the
    // fields; this is what the snackbar says when the request went out regardless.
    "profile_incomplete" -> stringResource(R.string.error_profile_incomplete)
    "posting_missing" -> stringResource(R.string.error_posting_missing)
    "application_missing" -> stringResource(R.string.error_application_missing)
    "document_missing" -> stringResource(R.string.error_document_missing)
    // The scan. Four refusals from the server and one raised on the device, and each names the
    // one thing the user can do about it — which is why they are five sentences and not one.
    AppViewModel.SCAN_UNREADABLE -> stringResource(R.string.error_scan_unreadable)
    "scan_consent_missing" -> stringResource(R.string.error_scan_consent_missing)
    "scan_type_unsupported" -> stringResource(R.string.error_scan_type_unsupported)
    "scan_too_large" -> stringResource(R.string.error_scan_too_large)
    "scan_too_many_pages" -> stringResource(R.string.error_scan_too_many_pages)
    "scan_missing" -> stringResource(R.string.error_scan_missing)
    else -> error.detail.ifBlank { stringResource(R.string.error_unknown) }
}
