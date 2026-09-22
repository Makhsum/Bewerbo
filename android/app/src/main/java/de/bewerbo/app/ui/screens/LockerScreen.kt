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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.StoredDocument
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
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
 */
@Composable
fun LockerScreen(state: AppState, viewModel: AppViewModel) {
    val colors = LocalSemanticColors.current
    val documents = state.profile?.documents.orEmpty()
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
                                stringResource(R.string.locker_pages, document.pageCount),
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill(kindLabelText(document.kind), PillTone.Success)
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

/// The kinds keep their German names in every locale: they are the words that appear on the
/// Anlagenverzeichnis the recruiter reads, so learning them is part of the point.
private fun kindLabelText(kind: String) = kind
