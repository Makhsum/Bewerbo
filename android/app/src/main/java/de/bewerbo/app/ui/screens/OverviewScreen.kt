package de.bewerbo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.IconRow
import de.bewerbo.app.ui.components.Meter
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.ReadinessRing
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/**
 * Übersicht — how ready the Bewerbungsmappe is, and what would make it readier.
 *
 * The score is only useful because every point of it is attributable: the three meters below it
 * are what it is made of, and each "Nächster Schritt" deep-links to the screen that closes it.
 */
@Composable
fun OverviewScreen(state: AppState, viewModel: AppViewModel, navigate: (String) -> Unit) {
    val overview = state.overview
    val colors = LocalSemanticColors.current

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column {
                Text(stringResource(R.string.nav_overview), style = MaterialTheme.typography.headlineLarge)
                Text(
                    listOfNotNull(
                        overview.displayName.ifBlank { null },
                        overview.city.ifBlank { null },
                        pluralStringResource(
                            R.plurals.overview_application_count,
                            overview.applicationCount, overview.applicationCount,
                        ),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        item {
            BewerboCard(Modifier.testTag("overview_readiness_card")) {
                SectionLabel(stringResource(R.string.overview_mappe))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReadinessRing(
                        overview.readiness,
                        stringResource(R.string.overview_of_hundred),
                        Modifier.testTag("overview_readiness_ring"),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = Space.m),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        Meter(
                            stringResource(R.string.overview_profile_complete),
                            "${overview.profileCompleteness} %",
                            overview.profileCompleteness / 100f,
                            if (overview.profileCompleteness >= 80) PillTone.Success else PillTone.Attention,
                            Modifier.testTag("overview_meter_profile"),
                        )
                        Meter(
                            stringResource(R.string.overview_gaps_explained),
                            "${overview.gapsExplained} / ${overview.gapsTotal}",
                            if (overview.gapsTotal == 0) 1f else {
                                overview.gapsExplained.toFloat() / overview.gapsTotal
                            },
                            if (overview.gapsExplained == overview.gapsTotal) {
                                PillTone.Success
                            } else PillTone.Attention,
                            Modifier.testTag("overview_meter_gaps"),
                            empty = overview.gapsTotal == 0,
                        )
                        Meter(
                            stringResource(R.string.overview_evidence),
                            "${overview.evidenceOnFile} / ${overview.evidenceExpected}",
                            if (overview.evidenceExpected == 0) 1f else {
                                overview.evidenceOnFile.toFloat() / overview.evidenceExpected
                            },
                            if (overview.evidenceOnFile == overview.evidenceExpected) {
                                PillTone.Success
                            } else PillTone.Attention,
                            Modifier.testTag("overview_meter_evidence"),
                            empty = overview.evidenceExpected == 0,
                        )
                    }
                }
            }
        }

        if (overview.nextSteps.isNotEmpty()) {
            item {
                BewerboCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel(stringResource(R.string.overview_next_steps))
                        StatusPill("${overview.nextSteps.size}", PillTone.Neutral)
                    }
                    overview.nextSteps.forEachIndexed { index, step ->
                        IconRow(
                            icon = if (step.severity == "attention") {
                                BewerboIcons.Attention
                            } else BewerboIcons.Anabin,
                            title = step.title,
                            detail = step.detail,
                            tone = if (step.severity == "attention") PillTone.Attention else PillTone.Accent,
                            trailing = {
                                Icon(
                                    BewerboIcons.ChevronRight, contentDescription = null,
                                    tint = colors.muted, modifier = Modifier.size(20.dp),
                                )
                            },
                            modifier = Modifier.testTag("overview_next_step_$index"),
                            onClick = { navigate(step.target) },
                        )
                    }
                }
            }
        }

        if (overview.applications.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.overview_active)) }
            itemsIndexed(overview.applications) { index, application ->
                BewerboCard(Modifier.testTag("overview_application_$index")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(application.jobTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    application.company.ifBlank { null },
                                    application.reference.ifBlank { null },
                                    application.sentAt,
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        StatusPill(
                            application.status,
                            when (application.status) {
                                "Einladung" -> PillTone.Success
                                "Absage" -> PillTone.Danger
                                "Versendet" -> PillTone.Accent
                                else -> PillTone.Neutral
                            },
                        )
                    }
                }
            }
        }

        if (overview.documents.isNotEmpty()) {
            item {
                BewerboCard(onClick = { navigate("mappe") }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel(stringResource(R.string.overview_your_locker))
                        StatusPill("${overview.documents.size}", PillTone.Neutral)
                    }
                    overview.documents.take(3).forEach { document ->
                        IconRow(
                            icon = BewerboIcons.Document,
                            title = document.title,
                            detail = document.note,
                            tone = PillTone.Success,
                        )
                    }
                }
            }
        }

        item {
            Callout(
                icon = BewerboIcons.EuStorage,
                title = stringResource(R.string.overview_eu_title),
                body = stringResource(R.string.overview_eu_body),
                modifier = Modifier.testTag("overview_eu_notice"),
            )
        }

        // Where no model is configured, the app says so instead of shipping a half-German CV.
        val translation = state.profile?.translation
        if (translation != null && !translation.available && translation.pending > 0) {
            item {
                Callout(
                    icon = BewerboIcons.Languages,
                    title = stringResource(R.string.translation_pending_title),
                    body = stringResource(R.string.translation_pending_body, translation.pending),
                    tone = PillTone.Attention,
                    modifier = Modifier.testTag("overview_translation_notice"),
                )
            }
        }
    }
}