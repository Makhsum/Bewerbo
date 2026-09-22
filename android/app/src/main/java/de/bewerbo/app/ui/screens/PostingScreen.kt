package de.bewerbo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.EvidenceField
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.EvidenceText
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.exposeTestTags
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.SegmentedControl
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

private val FIELD_ORDER = listOf("contact", "company", "reference", "title", "start")
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
    var editing by remember { mutableStateOf<EvidenceField?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("posting_screen"),
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
                            spans = posting.fields.mapIndexed { index, field ->
                                Triple(field.spanStart, field.spanLength, index)
                            },
                            modifier = Modifier.testTag("posting_source_text"),
                        )
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(stringResource(R.string.posting_read_from))
                    StatusPill("${posting.fields.size}", PillTone.Neutral)
                }
            }

            FIELD_ORDER.forEachIndexed { index, key ->
                val field = posting.field(key)
                item {
                    BewerboCard(Modifier.testTag("posting_field_$key")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Icon(
                                fieldIcon(key), contentDescription = null,
                                tint = colors.muted, modifier = Modifier.size(20.dp),
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = Space.s),
                            ) {
                                Text(
                                    stringResource(fieldLabel(key)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                                Text(
                                    field?.value ?: stringResource(R.string.posting_field_missing),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (field != null && field.spanStart < 0 && field.quote.isNotBlank()) {
                                    // Said out loud rather than shown as a highlight over the wrong
                                    // words: the quote did not appear verbatim in the posting.
                                    Text(
                                        stringResource(R.string.posting_no_span),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.attention,
                                        modifier = Modifier.testTag("posting_field_nospan_$key"),
                                    )
                                }
                                if (key == "reference" && field != null) {
                                    Text(
                                        stringResource(R.string.posting_reference_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusPill(
                                    stringResource(
                                        if (field == null) {
                                            R.string.posting_confidence_missing
                                        } else if (field.confidence == "sicher") {
                                            R.string.posting_confidence_sure
                                        } else R.string.posting_confidence_check,
                                    ),
                                    when {
                                        field == null -> PillTone.Danger
                                        field.confidence == "sicher" -> PillTone.Success
                                        else -> PillTone.Attention
                                    },
                                )
                                TextButton(
                                    onClick = {
                                        editing = field ?: EvidenceField(key = key, value = "")
                                    },
                                    modifier = Modifier.testTag("posting_field_edit_$key"),
                                ) {
                                    Icon(
                                        BewerboIcons.Rewrite, contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.action_correct),
                                        modifier = Modifier.padding(start = Space.xs),
                                    )
                                }
                            }
                        }
                    }
                }
                @Suppress("UNUSED_EXPRESSION") index
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

    editing?.let { field ->
        var value by remember(field.key) { mutableStateOf(field.value) }
        AlertDialog(
            onDismissRequest = { editing = null },
            // A dialog is its own window: without its own flag nothing inside it has a resource-id.
            modifier = Modifier.exposeTestTags().testTag("posting_edit_dialog"),
            title = { Text(stringResource(fieldLabel(field.key))) },
            text = {
                LabelledField(
                    label = stringResource(fieldLabel(field.key)),
                    value = value,
                    onValueChange = { value = it },
                    testTag = "posting_edit_input",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.correctField(field.key, value)
                        editing = null
                    },
                    modifier = Modifier.testTag("posting_edit_confirm"),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { editing = null },
                    modifier = Modifier.testTag("posting_edit_cancel"),
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun fieldIcon(key: String) = when (key) {
    "contact" -> BewerboIcons.Person
    "company" -> BewerboIcons.Employer
    "reference" -> BewerboIcons.Reference
    "title" -> BewerboIcons.Experience
    else -> BewerboIcons.Period
}

private fun fieldLabel(key: String) = when (key) {
    "contact" -> R.string.posting_field_contact
    "company" -> R.string.posting_field_company
    "reference" -> R.string.posting_field_reference
    "title" -> R.string.posting_field_title
    else -> R.string.posting_field_start
}

private fun photoAdvice(type: String) = when (type) {
    "Konzern", "Mittelstand" -> R.string.posting_photo_expected
    "OeffentlicherDienst" -> R.string.posting_photo_public
    else -> R.string.posting_photo_unusual
}
