package de.bewerbo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.EvidenceField
import de.bewerbo.app.data.PostingView
import de.bewerbo.app.ui.TermNote
import de.bewerbo.app.ui.germanTerm
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.BewerboDialog
import de.bewerbo.app.ui.components.EvidenceMark
import de.bewerbo.app.ui.components.EvidenceText
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.SegmentedControl
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space
import kotlinx.coroutines.launch

private val FIELD_ORDER = listOf("contact", "contactEmail", "company", "reference", "title", "start")
private val EMPLOYER_TYPES = listOf("Konzern", "Mittelstand", "Startup", "OeffentlicherDienst")

/**
 * Stellenanzeige — paste a posting, and see every field the app read shown against the words it
 * came from.
 *
 * Showing the source is not decoration: the fields go straight into a letter that is addressed to
 * a real person with a real reference number, and the user is the only one who can tell whether
 * the app read them right.
 */
@Composable
fun PostingScreen(state: AppState, viewModel: AppViewModel, onMatched: () -> Unit) {
    val colors = LocalSemanticColors.current
    val posting = state.posting
    var text by remember { mutableStateOf("") }
    var correcting by remember { mutableStateOf(false) }

    // The tie between a field and its passage: one marker number, selected from either end. A new
    // posting starts with nothing selected — the numbers alone already say which came from where.
    val marks = remember(posting) { posting?.let(::markerNumbers).orEmpty() }
    var selected by remember(posting?.id) { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /// Selecting shows the other side, which is only true if the other side is on screen: the two
    /// cards are far enough apart on a phone that one of them is always scrolled away.
    fun select(number: Int, show: Int) {
        if (selected == number) {
            selected = null
        } else {
            selected = number
            scope.launch { listState.animateScrollToItem(show) }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("posting_screen"),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column {
                Text(stringResource(R.string.nav_posting), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.posting_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        if (posting == null) {
            item {
                BewerboCard {
                    SectionLabel(stringResource(R.string.posting_paste_label))
                    Box(Modifier.padding(top = Space.s)) {
                        LabelledField(
                            label = stringResource(R.string.posting_paste_field),
                            value = text,
                            onValueChange = { text = it },
                            testTag = "posting_input_text",
                            singleLine = false,
                            minLines = 8,
                        )
                    }
                    Button(
                        onClick = { viewModel.parsePosting(text) },
                        enabled = text.isNotBlank() && state.busy == null,
                        modifier = Modifier
                            .padding(top = Space.m)
                            .fillMaxWidth()
                            .testTag("posting_btn_parse"),
                    ) {
                        Icon(BewerboIcons.Posting, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.posting_parse),
                            modifier = Modifier.padding(start = Space.s),
                        )
                    }
                }
            }
        } else {
            item {
                BewerboCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            BewerboIcons.Document, contentDescription = null,
                            tint = colors.muted, modifier = Modifier.size(18.dp),
                        )
                        SectionLabel(
                            stringResource(R.string.posting_source, posting.sourceText.length),
                            Modifier.padding(start = Space.s),
                        )
                    }
                    Box(Modifier.padding(top = Space.s)) {
                        EvidenceText(
                            source = posting.sourceText,
                            spans = marks.mapNotNull { (key, number) ->
                                posting.field(key)
                                    ?.let { EvidenceMark(it.spanStart, it.spanLength, number) }
                            },
                            selected = selected,
                            onSelect = { number -> select(number, FieldsItem) },
                            modifier = Modifier.testTag("posting_source_text"),
                        )
                    }
                    if (marks.isNotEmpty()) {
                        Text(
                            stringResource(R.string.posting_evidence_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            modifier = Modifier
                                .padding(top = Space.s)
                                .testTag("posting_evidence_hint"),
                        )
                    }
                }
            }

            // All five fields in one card, and one way to correct them. Five cards each carrying
            // their own "Correct" link filled two screens with five links that did one job.
            item {
                BewerboCard(Modifier.testTag("posting_fields")) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(stringResource(R.string.posting_read_from))
                        StatusPill("${posting.fields.size}", PillTone.Neutral)
                    }
                    FIELD_ORDER.forEach { key ->
                        val number = marks[key]
                        PostingFieldRow(
                            key = key,
                            field = posting.field(key),
                            number = number,
                            selected = number != null && number == selected,
                            onSelect = number?.let { { select(it, SourceItem) } },
                        )
                    }
                    TextButton(
                        onClick = { correcting = true },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("posting_btn_correct"),
                    ) {
                        Icon(
                            BewerboIcons.Rewrite, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.posting_correct_fields),
                            modifier = Modifier.padding(start = Space.xs),
                        )
                    }
                }
            }

            item {
                BewerboCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel(stringResource(R.string.posting_employer_type))
                        Text(
                            stringResource(R.string.posting_employer_type_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    Box(Modifier.padding(top = Space.s)) {
                        SegmentedControl(
                            options = EMPLOYER_TYPES,
                            selectedIndex = EMPLOYER_TYPES.indexOf(posting.employerType)
                                .coerceAtLeast(0),
                            onSelect = { viewModel.setEmployerType(EMPLOYER_TYPES[it]) },
                            modifier = Modifier.testTag("posting_employer_type_selector"),
                            tagPrefix = "posting_employer_type",
                            label = { stringResource(employerTypeLabel(it)) },
                        )
                    }
                    // Photo advice follows from the type. It is never a requirement: the AGG means
                    // no employer may demand one.
                    Text(
                        stringResource(photoAdvice(posting.employerType)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier
                            .padding(top = Space.s)
                            .testTag("posting_photo_advice"),
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.matchRequirements()
                        onMatched()
                    },
                    enabled = state.busy == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("posting_btn_match_requirements"),
                ) {
                    Icon(BewerboIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.posting_match),
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.clearPosting() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("posting_btn_new"),
                ) {
                    Text(stringResource(R.string.posting_new))
                }
            }
        }
    }

    if (correcting && posting != null) {
        PostingCorrectionDialog(
            posting = posting,
            onDismiss = { correcting = false },
            onSave = { values ->
                viewModel.correctFields(values)
                correcting = false
            },
        )
    }
}

/**
 * One field read from the posting: its number, label, value, and a marker only where there is
 * something to do.
 *
 * The number leads the row because it is what the user follows back into the text — the same digit
 * rides above the words the value came from. Three states are told apart there and nowhere else: a
 * number (read, and the words are in the text), a dash (read, but those words are not in the text
 * as they stand) and an empty slot (the posting names nothing).
 *
 * Two notes can follow the value — that the quote was not found verbatim, and where the Referenz
 * ends up. Both are muted: they explain, they do not ask.
 */
@Composable
private fun PostingFieldRow(
    key: String,
    field: EvidenceField?,
    number: Int?,
    selected: Boolean,
    onSelect: (() -> Unit)?,
) {
    val colors = LocalSemanticColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .testTag("posting_field_$key")
            .padding(top = Space.s)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (selected) colors.accentTint else Color.Transparent)
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(MarkWidth)
                .testTag("posting_field_mark_$key"),
            contentAlignment = Alignment.Center,
        ) {
            if (number != null) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
            } else if (field != null) {
                Text(
                    stringResource(R.string.posting_field_no_passage),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
        Icon(
            fieldIcon(key), contentDescription = null,
            tint = colors.muted, modifier = Modifier.size(18.dp),
        )
        SectionLabel(
            stringResource(fieldLabel(key)),
            Modifier
                .padding(start = Space.s)
                .width(FieldLabelWidth),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = Space.s),
        ) {
            Text(
                field?.value ?: stringResource(R.string.posting_field_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = if (field == null) colors.muted else MaterialTheme.colorScheme.onSurface,
            )
            if (field != null && field.spanStart < 0 && field.quote.isNotBlank()) {
                // Said out loud rather than shown as a highlight over the wrong words: the quote
                // did not appear verbatim in the posting.
                Text(
                    stringResource(R.string.posting_no_span),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.testTag("posting_field_nospan_$key"),
                )
            }
            if (key == "reference" && field != null) {
                Text(
                    stringResource(R.string.posting_reference_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                // The note above names the Referenznummer, and the posting is where the user
                // first meets it — the Übersicht row only repeats it later.
                TermNote(germanTerm("referenznummer"))
            }
        }
        fieldMarker(key, field)?.let { (label, tone) ->
            StatusPill(
                stringResource(label), tone,
                Modifier
                    .padding(start = Space.s)
                    .testTag("posting_field_marker_$key"),
            )
        }
    }
}

/**
 * The one place a read field is corrected — all five at once.
 *
 * Only what the user actually changed is sent on: a correction drops the field's quote, so writing
 * back a value nobody touched would cost it its highlight in the source text for nothing.
 */
@Composable
private fun PostingCorrectionDialog(
    posting: PostingView,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    val entered = remember(posting.id) {
        mutableStateMapOf<String, String>().apply {
            FIELD_ORDER.forEach { key -> put(key, posting.field(key)?.value ?: "") }
        }
    }

    BewerboDialog(
        onDismissRequest = onDismiss,
        testTag = "posting_edit_dialog",
        title = { Text(stringResource(R.string.posting_correct_fields)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                FIELD_ORDER.forEach { key ->
                    LabelledField(
                        label = stringResource(fieldLabel(key)),
                        value = entered.getValue(key),
                        onValueChange = { entered[key] = it },
                        testTag = "posting_edit_input_$key",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        FIELD_ORDER
                            .filter { entered.getValue(it) != (posting.field(it)?.value ?: "") }
                            .associateWith { entered.getValue(it) },
                    )
                },
                modifier = Modifier.testTag("posting_edit_confirm"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("posting_edit_cancel"),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/// The marker column. Narrow on purpose: it takes its width off the value, and a single digit is
/// all it ever has to hold.
private val MarkWidth = 16.dp

/// The two cards of this screen as the list counts them — heading 0, source text 1, fields 2.
/// Selecting on one side scrolls to the other, and a LazyColumn is reached by index, not by key.
private const val SourceItem = 1
private const val FieldsItem = 2

/**
 * The numbers that tie a field to the passage it came from — one running number per field the
 * server located in the text.
 *
 * Handed out in the order the fields are listed, so the list reads 1, 2, 3 downwards and the text
 * carries them in whatever order the posting happens to mention them. A field whose quote was not
 * found gets none: there is no passage to point at, and a number pointing at nothing is the wrong
 * highlight this screen exists to avoid.
 */
private fun markerNumbers(posting: PostingView): Map<String, Int> {
    var next = 1
    return FIELD_ORDER
        .filter { key -> posting.field(key)?.let { it.spanStart >= 0 && it.spanLength > 0 } == true }
        .associateWith { next++ }
}

/// The label column, so the values line up under each other rather than starting wherever the
/// label happened to end. At font_scale 1.30 "UNTERNEHMEN" no longer fits it and wraps; that is
/// the deliberate side to give way. A column wide enough for the label at every scale takes the
/// width away from the value, and the value is the thing the user is here to check.
private val FieldLabelWidth = 112.dp

/// The fields the Anschreiben cannot be addressed without. A posting that names no Eintritt and no
/// Referenz is the ordinary case, not something to flag.
private val REQUIRED_FIELDS = setOf("contact", "company", "title")

/**
 * The screen's one marker rule: a pill appears only where the user has something to do.
 *
 * A value read straight out of the posting carries no pill at all — five green "Sicher" pills were
 * five things to read and nothing to act on, and a red "Missing" on the Eintritt most adverts never
 * name taught the user to ignore red.
 */
private fun fieldMarker(key: String, field: EvidenceField?): Pair<Int, PillTone>? = when {
    field == null ->
        if (key in REQUIRED_FIELDS) R.string.posting_field_add to PillTone.Attention else null
    field.confidence != "sicher" || (field.spanStart < 0 && field.quote.isNotBlank()) ->
        R.string.posting_confidence_check to PillTone.Attention
    else -> null
}

private fun fieldIcon(key: String) = when (key) {
    "contact" -> BewerboIcons.Person
    "contactEmail" -> BewerboIcons.Mail
    "company" -> BewerboIcons.Employer
    "reference" -> BewerboIcons.Reference
    "title" -> BewerboIcons.Experience
    else -> BewerboIcons.Period
}

private fun fieldLabel(key: String) = when (key) {
    "contact" -> R.string.posting_field_contact
    "contactEmail" -> R.string.posting_field_contact_email
    "company" -> R.string.posting_field_company
    "reference" -> R.string.posting_field_reference
    "title" -> R.string.posting_field_title
    else -> R.string.posting_field_start
}

/// The employer types keep the backend's spelling as their value; none of the four is a word a
/// posting puts in front of the user, so all four are written in the user's language.
private fun employerTypeLabel(type: String) = when (type) {
    "Konzern" -> R.string.employer_type_konzern
    "Mittelstand" -> R.string.employer_type_mittelstand
    "Startup" -> R.string.employer_type_startup
    else -> R.string.employer_type_oeffentlicher_dienst
}

private fun photoAdvice(type: String) = when (type) {
    "Konzern", "Mittelstand" -> R.string.posting_photo_expected
    "OeffentlicherDienst" -> R.string.posting_photo_public
    else -> R.string.posting_photo_unusual
}
