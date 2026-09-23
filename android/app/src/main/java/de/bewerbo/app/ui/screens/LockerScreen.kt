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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.DemandedDocument
import de.bewerbo.app.data.StoredDocument
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.IconRow
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.SegmentedControl
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

private val KINDS = listOf("Arbeitszeugnis", "Zertifikat", "Sprachnachweis", "AnabinAuszug")

/**
 * Mappe — the documents the Anlagenverzeichnis refers to.
 *
 * What is stored here is the RECORD of a document: its title, its kind, how many pages. The scan
 * itself stays on the device. That is a narrower promise than server-side storage of somebody's
 * Zeugnisse, and it is one that can be kept without a data-protection argument.
 *
 * Above the list stands what the posting asks to see, so the Mappe answers the question the user
 * actually arrives with — not "what have I got" but "what is still missing for this application".
 */
@Composable
fun LockerScreen(state: AppState, viewModel: AppViewModel) {
    val colors = LocalSemanticColors.current
    val documents = state.profile?.documents.orEmpty()
    // Which documents are demanded is a property of the posting, so it is only known once one has
    // been read. With no Abgleich behind it the Mappe stays a plain list of what is on file rather
    // than inventing demands it cannot know about.
    val demands = state.match?.documents.orEmpty()
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("locker_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column {
                Text(stringResource(R.string.nav_locker), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.locker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        if (demands.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.locker_demand_title, demands.size)) }
            item {
                BewerboCard(Modifier.testTag("locker_demand_group")) {
                    demands.forEachIndexed { index, demand -> DemandRow(demand, index) }
                }
            }
        }

        itemsIndexed(documents) { index, document ->
            BewerboCard(Modifier.testTag("locker_item_$index")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Icon(
                        kindIcon(document.kind), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = Space.s),
                    ) {
                        Text(document.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                document.note.ifBlank { null },
                                // A count next to a number needs its plural rule: a one-page
                                // Zeugnis read "1 pages", which the Anlagenverzeichnis itself has
                                // always got right ("1 Seite").
                                pluralStringResource(
                                    R.plurals.locker_page_count,
                                    document.pageCount,
                                    document.pageCount,
                                ),
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill(stringResource(documentKindLabel(document.kind)), PillTone.Success)
                        TextButton(
                            onClick = { document.id?.let { viewModel.deleteDocument(it) } },
                            modifier = Modifier.testTag("locker_item_delete_$index"),
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }

        if (documents.isEmpty()) {
            item {
                Callout(
                    icon = BewerboIcons.Anlagen,
                    title = stringResource(R.string.locker_empty_title),
                    body = stringResource(R.string.locker_empty_body),
                )
            }
        }

        item {
            if (!adding) {
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("locker_btn_add"),
                ) {
                    Icon(BewerboIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.locker_add),
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
            } else {
                AddDocumentCard(viewModel) { adding = false }
            }
        }

        item {
            Callout(
                icon = BewerboIcons.EuStorage,
                title = stringResource(R.string.locker_storage_title),
                body = stringResource(R.string.locker_storage_body),
                modifier = Modifier.testTag("locker_storage_notice"),
            )
        }
    }
}

/**
 * One document the posting asks to see, with whether the Mappe can produce it.
 *
 * The pill fires only on an outstanding row. A satisfied demand has nothing for the user to do,
 * and a marker on every row is what taught users to stop reading the markers on the
 * Stellenanzeige. The state is still said in words on both kinds of row, so it does not rest on
 * the icon tint alone. The detail line is the posting's own sentence: the row has to be checkable
 * against the advert, not just asserted.
 */
@Composable
private fun DemandRow(demand: DemandedDocument, index: Int) {
    val onFile = stringResource(R.string.locker_demand_on_file)

    IconRow(
        icon = kindIcon(demand.kind),
        // The kind in the user's language, then what the server appended to it — the language and
        // level of a Sprachnachweis, which read the same in every language.
        title = (listOf(stringResource(documentKindLabel(demand.kind))) + demand.titleArgs)
            .joinToString(" "),
        detail = if (demand.onFile) {
            listOfNotNull(onFile, demand.quote.ifBlank { null }).joinToString("  ·  ")
        } else {
            demand.quote.ifBlank { null }
        },
        tone = if (demand.onFile) PillTone.Success else PillTone.Attention,
        trailing = if (demand.onFile) {
            null
        } else {
            {
                StatusPill(
                    stringResource(R.string.locker_demand_outstanding),
                    PillTone.Attention,
                    Modifier.testTag("locker_demand_outstanding_$index"),
                )
            }
        },
        modifier = Modifier.testTag("locker_demand_$index"),
    )
}

@Composable
private fun AddDocumentCard(viewModel: AppViewModel, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("1") }
    var kind by remember { mutableIntStateOf(0) }

    BewerboCard(Modifier.testTag("locker_add_card")) {
        SectionLabel(stringResource(R.string.locker_add))
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.locker_field_title), title, { title = it },
            testTag = "locker_input_title")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.locker_field_note), note, { note = it },
            testTag = "locker_input_note")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.locker_field_pages), pages, { pages = it },
            testTag = "locker_input_pages")
        Box(Modifier.padding(top = Space.s))
        SegmentedControl(
            options = KINDS,
            selectedIndex = kind,
            onSelect = { kind = it },
            modifier = Modifier.testTag("locker_kind_selector"),
            tagPrefix = "locker_kind",
            label = { stringResource(documentKindLabel(it)) },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Button(
                onClick = {
                    viewModel.addDocument(
                        StoredDocument(
                            title = title, kind = KINDS[kind], note = note,
                            pageCount = pages.toIntOrNull() ?: 1,
                        ),
                    )
                    onDone()
                },
                modifier = Modifier.testTag("locker_btn_save"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.testTag("locker_btn_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

private fun kindIcon(kind: String) = when (kind) {
    "Arbeitszeugnis" -> BewerboIcons.Document
    "Zertifikat" -> BewerboIcons.Check
    "Sprachnachweis" -> BewerboIcons.Languages
    "AnabinAuszug" -> BewerboIcons.Anabin
    else -> BewerboIcons.Anlagen
}

/// What a kind is called on screen. The value keeps the backend's spelling. Two of the four are
/// German on purpose — Arbeitszeugnis and Sprachnachweis are words a posting uses, and they are
/// explained where the user first meets them; the other two are not, so they are translated.
fun documentKindLabel(kind: String) = when (kind) {
    "Arbeitszeugnis" -> R.string.kind_arbeitszeugnis
    "Zertifikat" -> R.string.kind_zertifikat
    "Sprachnachweis" -> R.string.kind_sprachnachweis
    else -> R.string.kind_anabin_auszug
}
