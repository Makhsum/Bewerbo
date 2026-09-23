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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.Meter
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.RequirementRow
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.SegmentedControl
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
    navigate: (String) -> Unit,
    onGenerated: () -> Unit,
) {
    val colors = LocalSemanticColors.current
    val match = state.match
    var tone by remember { mutableIntStateOf(1) }

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

        item {
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
                        // "Nachweis hochladen" leads to the Mappe, which is where a Nachweis is
                        // recorded — the same place the Übersicht's own next step for a missing
                        // Sprachzertifikat points at. It used to be a tappable chip with an empty
                        // lambda, so the one row on this screen offering a way forward was inert.
                        RequirementRow(
                            requirement,
                            match.requirements.indexOf(requirement),
                            onAction = { navigate("mappe") },
                        )
                    }
                }
            }
        }

        if (nicht.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.match_not_proven, nicht.size)) }
            item {
                BewerboCard(Modifier.testTag("match_group_nicht_belegt")) {
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
}
