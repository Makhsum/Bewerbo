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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.Requirement
import de.bewerbo.app.data.StoredDocument
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.Meter
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.RequirementRow
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.SegmentedControl
import de.bewerbo.app.ui.components.exposeTestTags
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

private val TONES = listOf("Klassisch", "Sachlich", "Modern")

/**
 * Abgleich — the posting's requirements against what the profile can prove, one row each.
 *
 * This screen is where the decision is taken that the letter then has to live with: what is listed
 * as "nicht belegt" here is not written into the Anschreiben. Putting that guard on this screen
 * rather than inside the generator is the point — it is a decision the user can see and argue with.
 */
@Composable
fun MatchScreen(
    state: AppState,
    viewModel: AppViewModel,
    onGenerated: () -> Unit,
) {
    val colors = LocalSemanticColors.current
    val match = state.match
    var tone by remember { mutableIntStateOf(1) }
    var filing by remember { mutableStateOf<Requirement?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("match_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column {
                Text(stringResource(R.string.match_title), style = MaterialTheme.typography.headlineLarge)
                Text(
                    listOfNotNull(
                        match?.company?.ifBlank { null },
                        match?.reference?.ifBlank { null },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        if (match == null) {
            item {
                Callout(
                    icon = BewerboIcons.Posting,
                    title = stringResource(R.string.match_none_title),
                    body = stringResource(R.string.match_none_body),
                )
            }
            return@LazyColumn
        }

        // Nothing read is not the same as nothing proven, and the meter cannot tell them apart:
        // "0 of 0 requirements proven, 0 %" over an empty track is what a profile that covers none
        // of them looks like, so an advert whose requirements could not be read accused the user of
        // a gap that was never theirs. The empty state of a section is a Callout here as everywhere.
        item {
            if (match.total == 0) {
                Callout(
                    icon = BewerboIcons.Attention,
                    title = stringResource(R.string.match_none_read_title),
                    body = stringResource(R.string.match_none_read_body),
                    tone = PillTone.Attention,
                    modifier = Modifier.testTag("match_none_read"),
                )
            } else {
                BewerboCard {
                    Meter(
                        label = stringResource(R.string.match_covered, match.covered, match.total),
                        value = "${match.percent} %",
                        fraction = match.percent / 100f,
                        tone = if (match.percent >= 60) PillTone.Success else PillTone.Attention,
                        modifier = Modifier.testTag("match_score_meter"),
                    )
                }
            }
        }

        val belegt = match.requirements.filter { it.state == "belegt" }
        val offen = match.requirements.filter { it.state == "offen" }
        val nicht = match.requirements.filter { it.state == "nicht_belegt" }

        if (belegt.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.match_proven, belegt.size)) }
            item {
                BewerboCard(Modifier.testTag("match_group_belegt")) {
                    belegt.forEach { requirement ->
                        RequirementRow(requirement, match.requirements.indexOf(requirement))
                    }
                }
            }
        }

        if (offen.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.match_open, offen.size)) }
            item {
                BewerboCard(Modifier.testTag("match_group_offen")) {
                    offen.forEach { requirement ->
                        // "Nachweis hochladen" files the document HERE. It used to navigate to the
                        // Mappe, which left the user to work out which document had been meant —
                        // and filing it there closed nothing, because the Abgleich reads the
                        // language's own certificateOnFile flag and the Mappe never set it.
                        RequirementRow(
                            requirement,
                            match.requirements.indexOf(requirement),
                            onAction = { filing = requirement },
                        )
                    }
                }
            }
        }

        if (nicht.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.match_not_proven, nicht.size)) }
            item {
                BewerboCard(Modifier.testTag("match_group_nicht_belegt")) {
                    // What holds for the whole group is said here once. It used to be printed under
                    // every row, so five requirements carried five copies of the same sentence.
                    Text(
                        stringResource(R.string.match_not_proven_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.testTag("match_not_proven_note"),
                    )
                    nicht.forEach { requirement ->
                        RequirementRow(requirement, match.requirements.indexOf(requirement))
                    }
                }
            }
        }

        // The Anschreiben / Motivationsschreiben guard, where the decision is actually taken.
        item {
            Callout(
                icon = BewerboIcons.Check,
                title = stringResource(R.string.match_guard_title),
                body = stringResource(R.string.match_guard_body),
                modifier = Modifier.testTag("match_anschreiben_guard"),
            )
        }

        item {
            BewerboCard {
                Row(Modifier.fillMaxWidth()) {
                    Icon(
                        BewerboIcons.Rewrite, contentDescription = null,
                        tint = colors.muted, modifier = Modifier.size(18.dp),
                    )
                    SectionLabel(
                        stringResource(R.string.match_tone),
                        Modifier.padding(start = Space.s),
                    )
                }
                Box(Modifier.padding(top = Space.s)) {
                    SegmentedControl(
                        options = TONES,
                        selectedIndex = tone,
                        onSelect = { tone = it },
                        modifier = Modifier.testTag("match_tone_selector"),
                        tagPrefix = "match_tone",
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.generateLetter(TONES[tone])
                    onGenerated()
                },
                enabled = state.busy == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("match_btn_generate_letter"),
            ) {
                Icon(BewerboIcons.Rewrite, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.match_generate_letter),
                    modifier = Modifier.padding(start = Space.s),
                )
            }
        }
    }

    filing?.let { requirement ->
        FileCertificateDialog(
            language = requirement.language,
            level = state.profile?.languages
                .orEmpty()
                .firstOrNull { it.language == requirement.language }
                ?.level
                .orEmpty(),
            onDismiss = { filing = null },
            onSave = { document ->
                viewModel.fileCertificate(requirement.language, document)
                filing = null
            },
        )
    }
}

/**
 * Files the Nachweis an open requirement is waiting for, without leaving the Abgleich.
 *
 * The kind is not offered: a row asking for a Sprachnachweis is asking for a Sprachnachweis, and a
 * selector whose answer is already known is one more thing to read. The title is prefilled with the
 * language and the level the profile states, so the document the Anlagenverzeichnis will name says
 * which certificate it is — and it stays German in every locale, like the kinds in the Mappe,
 * because it is a line the recruiter reads.
 */
@Composable
private fun FileCertificateDialog(
    language: String,
    level: String,
    onDismiss: () -> Unit,
    onSave: (StoredDocument) -> Unit,
) {
    var title by remember(language) {
        mutableStateOf(
            listOfNotNull("Sprachnachweis", language.ifBlank { null }, level.ifBlank { null })
                .joinToString(" "),
        )
    }
    var note by remember(language) { mutableStateOf("") }
    var pages by remember(language) { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog is its own window: without its own flag nothing inside it has a resource-id.
        modifier = Modifier.exposeTestTags().testTag("match_file_dialog"),
        title = { Text(stringResource(R.string.match_file_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    stringResource(R.string.match_file_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSemanticColors.current.muted,
                )
                LabelledField(
                    label = stringResource(R.string.locker_field_title),
                    value = title,
                    onValueChange = { title = it },
                    testTag = "match_file_input_title",
                )
                LabelledField(
                    label = stringResource(R.string.locker_field_note),
                    value = note,
                    onValueChange = { note = it },
                    testTag = "match_file_input_note",
                )
                LabelledField(
                    label = stringResource(R.string.locker_field_pages),
                    value = pages,
                    onValueChange = { pages = it },
                    testTag = "match_file_input_pages",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        StoredDocument(
                            title = title, kind = "Sprachnachweis", note = note,
                            pageCount = pages.toIntOrNull() ?: 1,
                        ),
                    )
                },
                modifier = Modifier.testTag("match_file_confirm"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("match_file_cancel"),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
