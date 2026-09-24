package de.bewerbo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.Destination
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.ui.TermNote
import de.bewerbo.app.ui.germanTerm
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.IconRow
import de.bewerbo.app.ui.components.Meter
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.components.applicationStatusLabel
import de.bewerbo.app.ui.components.nextStepDetail
import de.bewerbo.app.ui.components.nextStepTitle
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/**
 * Übersicht — what the Bewerbungsmappe still needs, what it is made of, and every application the
 * user has started with what each of them still needs.
 *
 * It opens with ONE first step, because the screen a user sees before they have entered anything is
 * the screen that has to say where to begin. Which step that is depends on the profile: without a
 * Berufserfahrung there is no Lebenslauf to produce, so the first step is the profile and the flow
 * is offered beside it rather than as the thing to do — see `canStartApplication`.
 *
 * Under it stands what is outstanding. That used to be a readiness score out of 100 over three
 * meters, and a score says nothing a user can act on: each "Nächster Schritt" deep-links to the
 * screen that closes it, and the meters stay as the counted things they are. An application's own
 * steps work the same way, and so does the application itself — this is the one screen from which a
 * user with several employers gets back into an unfinished one.
 *
 * It is also the ONE way into the flow that produces an application, now that its steps are no
 * longer on the bottom bar: [flowLabel] says whether the path is being begun or picked up,
 * [onOpenFlow] leads to the step the user actually got to, and [onBeginAnother] — null while there
 * is nothing under way — begins the next employer's application beside it.
 */
@Composable
fun OverviewScreen(
    state: AppState,
    viewModel: AppViewModel,
    flowLabel: Int,
    onOpenFlow: () -> Unit,
    onBeginAnother: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    navigate: (String) -> Unit,
) {
    val overview = state.overview
    val colors = LocalSemanticColors.current

    // Opening an application means bringing its whole context back BEFORE the screen it leads to
    // is drawn — the Bewerbung reads the letter, the Abgleich reads the match, both of which
    // belong to this application and not to whichever one was open before.
    val open: (String, String) -> Unit = { applicationId, route ->
        viewModel.openApplication(applicationId)
        navigate(route)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            // The gear sits beside the headline of the first screen, which is the one place every
            // user passes and the only screen the settings are reached from — they are not a place
            // on the bar, so they need a door, and this is where an Android user looks for it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_overview), style = MaterialTheme.typography.headlineLarge)
                    if (overview != null) {
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
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("overview_btn_settings")) {
                    Icon(
                        BewerboIcons.Settings,
                        contentDescription = stringResource(R.string.settings_open),
                    )
                }
            }
        }

        // The start itself failed. bootstrap() is what sets the profile and clears loading, so no
        // profile once loading is done means the server was never reached — and nothing runs
        // bootstrap() a second time by itself, so "one moment" there is a wait that never ends. It
        // outlasted the server coming back, and the snackbar that said so is long gone.
        if (state.profile == null && !state.loading) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Callout(
                        icon = BewerboIcons.Attention,
                        title = stringResource(R.string.overview_unreachable_title),
                        body = stringResource(R.string.overview_unreachable_body),
                        tone = PillTone.Attention,
                        modifier = Modifier.testTag("overview_unreachable"),
                    )
                    OutlinedButton(
                        onClick = { viewModel.retryStart() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_btn_retry"),
                    ) {
                        Icon(BewerboIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.overview_retry),
                            modifier = Modifier.padding(start = Space.s),
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Everything below says what is outstanding and what to do first, and neither is known until
        // the Übersicht has been fetched. Drawn from an empty default they read as answers — "nothing
        // outstanding" over a profile with nothing in it, and a first step that then changes under
        // the user's finger. So until it arrives the screen says that it is fetching, which is the
        // one thing that is true: a blank first screen tells a new user no more than a wrong one.
        if (overview == null) {
            item {
                Callout(
                    icon = BewerboIcons.Refresh,
                    title = stringResource(R.string.overview_loading_title),
                    body = stringResource(R.string.overview_loading_body),
                    modifier = Modifier.testTag("overview_loading"),
                )
            }
            return@LazyColumn
        }

        // The one first step, above everything the user could read first. The steps used to be tabs,
        // so "where do I start" was answered by knowing the order of them; this is the answer on
        // screen. A profile with no Berufserfahrung in it cannot produce a Lebenslauf, so beginning
        // an application there leads to a path that cannot finish — the profile is the first step
        // then, and the flow stands beside it for the user who wants to read a posting first.
        item {
            val profileFirst = !overview.canStartApplication

            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (profileFirst) {
                    Button(
                        onClick = { navigate(Destination.Profile.route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_btn_profile"),
                    ) {
                        Icon(BewerboIcons.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.overview_first_profile),
                            modifier = Modifier.padding(start = Space.s),
                        )
                    }
                }

                // The flow keeps its place and its tag either way — it is the only way in, and a
                // user who wants to read a posting before filling anything in must still get there.
                // What changes is whether it is the step being offered or the one standing beside it.
                val flowContent: @Composable RowScope.() -> Unit = {
                    Icon(BewerboIcons.Posting, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(flowLabel), modifier = Modifier.padding(start = Space.s))
                }
                val flowModifier = Modifier
                    .fillMaxWidth()
                    .testTag("overview_btn_flow")

                if (profileFirst) {
                    OutlinedButton(onClick = onOpenFlow, modifier = flowModifier, content = flowContent)
                } else {
                    Button(onClick = onOpenFlow, modifier = flowModifier, content = flowContent)
                }

                // The second way in, and only once there is something to come back to: the action
                // above continues the application under way, this one begins the next employer's.
                // It stands beside it as the flow stands beside the profile step — secondary,
                // because continuing what is half-written is the likelier thing to want.
                if (onBeginAnother != null) {
                    OutlinedButton(
                        onClick = onBeginAnother,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_btn_flow_another"),
                    ) {
                        Icon(BewerboIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.overview_flow_another),
                            modifier = Modifier.padding(start = Space.s),
                        )
                    }
                }
            }
        }

        // What is outstanding, said before what it is measured out of. The card is announced even at
        // none, for the reason the application list is: a user has to be able to see that this is
        // where it would stand.
        if (overview.nextSteps.isEmpty()) {
            item {
                Callout(
                    icon = BewerboIcons.Anabin,
                    title = stringResource(R.string.overview_next_steps_empty_title),
                    body = stringResource(R.string.overview_next_steps_empty_body),
                    tone = PillTone.Success,
                    modifier = Modifier.testTag("overview_next_steps_empty"),
                )
            }
        } else {
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
                            title = nextStepTitle(step),
                            detail = nextStepDetail(step),
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

        // The three meters count how far along the Mappe is, and on a profile with no Berufserfahrung
        // and no Ausbildung there is nothing for them to count: "0 %" over an empty track and two
        // meters reading "0 / 0", which is the same unanswerable measure the readiness score was and
        // the same hole the Zeitstrahl had. Same condition it uses, off the same fetch.
        item {
            if (state.timeline.periods.isEmpty()) {
                Callout(
                    icon = BewerboIcons.Anlagen,
                    title = stringResource(R.string.overview_mappe_empty_title),
                    body = stringResource(R.string.overview_mappe_empty_body),
                    modifier = Modifier.testTag("overview_mappe_empty"),
                )
            } else BewerboCard(Modifier.testTag("overview_readiness_card")) {
                SectionLabel(stringResource(R.string.overview_mappe))
                // The Übersicht is the first screen, and this card's own label is the first
                // German word the user meets anywhere in the app.
                TermNote(germanTerm("bewerbungsmappe"))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.s),
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

        // The list of applications is always announced, even at none: a user who has started one
        // employer and not the next has to be able to see that this is where they would be.
        item { SectionLabel(stringResource(R.string.overview_active)) }

        if (overview.applications.isEmpty()) {
            item {
                Callout(
                    icon = BewerboIcons.Document,
                    title = stringResource(R.string.overview_applications_empty_title),
                    body = stringResource(R.string.overview_applications_empty_body),
                    modifier = Modifier.testTag("overview_applications_empty"),
                )
            }
        }

        itemsIndexed(overview.applications) { index, application ->
            BewerboCard(
                Modifier.testTag("overview_application_$index"),
                onClick = { open(application.id, "bewerbung") },
            ) {
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
                        stringResource(applicationStatusLabel(application.status)),
                        when (application.status) {
                            "Einladung" -> PillTone.Success
                            "Absage" -> PillTone.Danger
                            // Versendet and Wartend are the same fact to the eye — the Mappe is
                            // with the employer and nothing is owed here — so they read alike.
                            "Versendet", "Wartend" -> PillTone.Accent
                            else -> PillTone.Neutral
                        },
                    )
                    // The same chevron the next steps carry, for the same reason: it is what says
                    // in this app that a row leads somewhere.
                    Icon(
                        BewerboIcons.ChevronRight, contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier
                            .padding(start = Space.s)
                            .size(20.dp),
                    )
                }

                // What this one still needs, drawn as the Nächste Schritte above are — each step
                // opens the application first, because the screen it leads to reads the posting,
                // the Abgleich or the letter that belongs to it.
                application.openSteps.forEachIndexed { stepIndex, step ->
                    IconRow(
                        icon = if (step.severity == "attention") {
                            BewerboIcons.Attention
                        } else BewerboIcons.Document,
                        title = nextStepTitle(step),
                        detail = nextStepDetail(step),
                        tone = if (step.severity == "attention") PillTone.Attention else PillTone.Accent,
                        trailing = {
                            Icon(
                                BewerboIcons.ChevronRight, contentDescription = null,
                                tint = colors.muted, modifier = Modifier.size(20.dp),
                            )
                        },
                        modifier = Modifier.testTag("overview_application_${index}_step_$stepIndex"),
                        onClick = { open(application.id, step.target) },
                    )
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