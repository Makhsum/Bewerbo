package de.bewerbo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import de.bewerbo.app.data.Education
import de.bewerbo.app.data.Experience
import de.bewerbo.app.data.LanguageSkill
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.LanguageSelector
import de.bewerbo.app.ui.components.labelOf
import de.bewerbo.app.ui.components.exposeTestTags
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.components.Timeline
import de.bewerbo.app.ui.TermNote
import de.bewerbo.app.ui.germanTerm
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space
import kotlinx.coroutines.delay

private val SECTIONS = listOf("person", "berufserfahrung", "ausbildung", "sprachen", "anlagen")

/// How long the typing has to stand still before the German wording for a gap is fetched. Long
/// enough that a word is not looked up letter by letter, short enough that the sentence is there
/// by the time the user has finished reading what they wrote.
private const val GAP_PREVIEW_DELAY_MS = 350L

/// The languages the product is for. German is on the list because somebody already fluent may
/// still want the DIN 5008 layout and the Abgleich.
private val INPUT_LANGUAGES = listOf(
    "ru" to "Русский",
    "uk" to "Українська",
    "tr" to "Türkçe",
    "en" to "English",
    "de" to "Deutsch",
)

/**
 * Profil — everything the user enters, in their own language, with the coaching a German reader
 * would want applied to it.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(state: AppState, viewModel: AppViewModel) {
    val profile = state.profile
    val colors = LocalSemanticColors.current
    var section by remember { mutableStateOf("berufserfahrung") }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column {
                Text(stringResource(R.string.nav_profile), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.profile_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        // The INPUT language — the one the user writes their profile in, which is profile data and
        // saved with it. The language of the screens themselves is the app's own preference and it
        // stands in the settings; the sentence below says so, because the two invite exactly one
        // mistake and naming only one of them here is what used to cause it.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                // FlowRow for the reason the section rail below uses one: two buttons and the pill
                // never fit one phone-width row, and an affordance past the right edge is one
                // nobody knows about.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    LanguageSelector(
                        label = stringResource(
                            R.string.profile_input_language,
                            labelOf(INPUT_LANGUAGES, profile?.person?.inputLanguage),
                        ),
                        icon = BewerboIcons.Languages,
                        options = INPUT_LANGUAGES,
                        testTagPrefix = "profile_language",
                        onPick = { code ->
                            profile?.let { viewModel.savePerson(it.person.copy(inputLanguage = code)) }
                        },
                    )
                    StatusPill(
                        stringResource(R.string.profile_complete, profile?.completeness ?: 0),
                        if ((profile?.completeness ?: 0) >= 80) PillTone.Success else PillTone.Attention,
                        // The pill is half the height of the buttons it shares the line with, and
                        // a FlowRow lays its children out from the top of the line.
                        Modifier
                            .align(Alignment.CenterVertically)
                            .testTag("profile_completeness_pill"),
                    )
                }
                Text(
                    stringResource(R.string.profile_languages_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.testTag("profile_languages_hint"),
                )
            }
        }

        // The section rail. It WRAPS, it does not scroll sideways: five sections never fit one
        // phone-width row, and the two that fell off the right edge — Sprachen and Anlagen — had
        // nothing at all announcing them. A rail whose only clue that it continues is a button cut
        // in half is a rail the user does not know continues, and the language level a German
        // posting asks for is behind exactly one of those two. Wrapping also survives a long
        // locale and a raised font scale, where a fixed five-across row would not.
        item {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .testTag("profile_section_rail"),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                SECTIONS.forEach { name ->
                    val selected = section == name
                    OutlinedButton(
                        onClick = { section = name },
                        modifier = Modifier.testTag("profile_section_rail_$name"),
                        // The default 24 dp of horizontal padding on five buttons is a whole row
                        // of empty space; at 16 dp the five land on two lines on a phone.
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Space.m, vertical = Space.s,
                        ),
                        colors = if (selected) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(stringResource(sectionLabel(name)))
                    }
                }
            }
        }

        // The Zeitstrahl. It is shown on every section because it is the thing the whole profile
        // adds up to, and a gap found while editing languages is still a gap. An empty profile has
        // nothing to draw, though: the card came up as the largest block on the screen, headed
        // "Zeitstrahl 2026 – 2026" — the backend falls back to today's year for both ends when
        // there are no periods — with three legend dots and not one bar. Until the first period
        // exists the same slot asks for it instead.
        item {
            if (state.timeline.periods.isEmpty()) {
                Callout(
                    icon = BewerboIcons.Timeline,
                    title = stringResource(R.string.profile_timeline_empty_title),
                    body = stringResource(R.string.profile_timeline_empty_body),
                    modifier = Modifier.testTag("profile_timeline_empty"),
                )
            } else BewerboCard(Modifier.testTag("profile_timeline")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            BewerboIcons.Timeline, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(
                                R.string.profile_timeline,
                                state.timeline.firstYear, state.timeline.lastYear,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = Space.s),
                        )
                    }
                    // The count is of gaps still OPEN. Counting every gap meant the pill stayed
                    // amber at "1 GAP" after the user had answered it, on the same screen where
                    // the answer is shown in green underneath — and the Übersicht's own meter for
                    // the same fact had already gone to 1 / 1. Nothing told them they were done.
                    val openGaps = state.timeline.gaps.count { !it.explained }
                    if (openGaps > 0) {
                        StatusPill(
                            pluralStringResource(R.plurals.profile_gap_count, openGaps, openGaps),
                            PillTone.Attention,
                            Modifier.testTag("profile_open_gap_count"),
                        )
                    }
                }
                Box(Modifier.padding(top = Space.s)) {
                    Timeline(
                        state.timeline.firstYear,
                        state.timeline.lastYear,
                        state.timeline.periods,
                        state.timeline.gaps.filter { !it.explained },
                    )
                }
            }
        }

        // Every gap gets asked about inline, where the user is already looking.
        state.timeline.gaps.forEachIndexed { index, gap ->
            item {
                GapCard(gap, index, state.gapWording[gap.from], viewModel)
            }
        }

        when (section) {
            "person" -> item { PersonSection(state, viewModel) }
            "berufserfahrung" -> {
                item { SectionLabel(stringResource(R.string.profile_section_experience)) }
                // Every position below carries a Zeugnis switch, so the word is explained once
                // here rather than on each card.
                item { TermNote(germanTerm("zeugnis")) }
                profile?.experience?.forEachIndexed { index, entry ->
                    item { ExperienceCard(entry, index, profile.experience, viewModel) }
                }
                item { AddExperienceButton(state, viewModel) }
            }
            "ausbildung" -> {
                item { SectionLabel(stringResource(R.string.profile_section_education)) }
                // Same again for anabin: each qualification offers the lookup, the name is
                // explained once above them.
                item { TermNote(germanTerm("anabin")) }
                profile?.education?.forEachIndexed { index, entry ->
                    item { EducationCard(entry, index, profile.education, state, viewModel) }
                }
                item { AddEducationButton(state, viewModel) }
            }
            "sprachen" -> {
                item { SectionLabel(stringResource(R.string.profile_section_languages)) }
                profile?.languages?.forEachIndexed { index, skill ->
                    item { LanguageCard(skill, index, profile.languages, viewModel) }
                }
                item { AddLanguageButton(state, viewModel) }
            }
            "anlagen" -> {
                item { SectionLabel(stringResource(R.string.profile_section_attachments)) }
                profile?.documents?.forEachIndexed { index, document ->
                    item {
                        BewerboCard(Modifier.testTag("profile_entry_document_$index")) {
                            Text(document.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                document.note.ifBlank {
                                    stringResource(documentKindLabel(document.kind))
                                },
                                style = MaterialTheme.typography.bodySmall, color = colors.muted,
                            )
                        }
                    }
                }
            }
        }

        // The screen ends here, with the action that leads out of it. There is no separate
        // template button beside this one either: the Vorlage used to be picked one row BELOW
        // this button, which asked a user still filling in a profile to choose between
        // Klassisch, Modern and Fachlich with nothing to judge them by, and asked it after the
        // button that had already produced the file. It is chosen on the Bewerbung screen now,
        // beside the document it changes.
        item {
            Column {
                Button(
                    onClick = { viewModel.generateCv() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_generate_cv"),
                ) {
                    Icon(
                        BewerboIcons.Document,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.profile_generate_cv),
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
                // The button names the Lebenslauf, and the Profil screen is where the user reaches
                // it first — the Übersicht only mentions it once the profile is filled.
                TermNote(germanTerm("lebenslauf"))
            }
        }
    }
}

/**
 * The inline gap field. The German wording the user will actually see in the Lebenslauf is shown
 * under the input — not to reassure them, but because that sentence is what a recruiter reads and
 * they should be the one to approve it.
 *
 * [preview] is what the server proposes for the reason currently in the field, fetched while the
 * user is still typing, and it falls back to the wording already stored — so the line reads the
 * same before a save as after one, and the sentence is never first met in the finished document.
 */
@Composable
private fun GapCard(
    gap: de.bewerbo.app.data.Gap,
    index: Int,
    preview: String?,
    viewModel: AppViewModel,
) {
    val colors = LocalSemanticColors.current
    var reason by remember(gap.from) { mutableStateOf(gap.reason.orEmpty()) }

    // One fetch per pause in the typing rather than one per keystroke: LaunchedEffect cancels the
    // previous delay whenever the reason changes, so only the last version typed is looked up.
    LaunchedEffect(reason) {
        delay(GAP_PREVIEW_DELAY_MS)
        viewModel.previewGapWording(gap, reason)
    }

    BewerboCard(Modifier.testTag("profile_timeline_gap_$index")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                BewerboIcons.Attention, contentDescription = null,
                tint = colors.attention, modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(
                    R.string.profile_gap_headline,
                    gap.from.take(7), gap.to.take(7), gap.months,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.attention,
                modifier = Modifier.padding(start = Space.s),
            )
        }
        Text(
            stringResource(R.string.profile_gap_explainer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Space.s),
        )
        Box(Modifier.padding(top = Space.s)) {
            LabelledField(
                label = stringResource(R.string.profile_gap_reason_label),
                value = reason,
                onValueChange = { reason = it },
                testTag = "profile_gap_reason_$index",
            )
        }
        // The sentence that will stand in the Lebenslauf, under the input that produces it. A
        // reason the writer has no German for is said out loud rather than left as a blank line:
        // the reason is stored either way, but the gap then stays unnamed in the document, and
        // reading that here is the difference between a choice and a surprise.
        val wording = preview ?: gap.germanWording
        if (reason.isNotBlank() && wording != null) {
            Text(
                if (wording.isBlank()) {
                    stringResource(R.string.profile_gap_wording_none)
                } else {
                    stringResource(R.string.profile_gap_in_cv, wording)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (wording.isBlank()) colors.muted else colors.success,
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("profile_gap_wording_$index"),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { viewModel.explainGap(gap, reason) },
                modifier = Modifier.testTag("profile_gap_save_$index"),
            ) {
                Text(stringResource(R.string.profile_gap_save))
            }
        }
    }
}

@Composable
private fun ExperienceCard(
    entry: Experience,
    index: Int,
    all: List<Experience>,
    viewModel: AppViewModel,
) {
    val colors = LocalSemanticColors.current

    BewerboCard(Modifier.testTag("profile_entry_experience_$index")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                BewerboIcons.Experience, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.s),
            ) {
                Text(entry.position, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        entry.employer.ifBlank { null },
                        entry.location.ifBlank { null },
                        "${entry.from.take(7)} – ${entry.to?.take(7) ?: "heute"}",
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        Row(
            Modifier.padding(top = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (entry.workload.isNotBlank()) StatusPill(entry.workload, PillTone.Neutral)
            StatusPill(
                stringResource(
                    if (entry.referenceOnFile) {
                        R.string.profile_reference_on_file
                    } else R.string.profile_reference_missing,
                ),
                if (entry.referenceOnFile) PillTone.Success else PillTone.Attention,
            )
        }

        // The Zeugnis switch, for the same reason the language card has one: "Nachweise on file"
        // counts one Arbeitszeugnis per finished job, and until this existed that count could
        // only ever go down as the user added experience.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.profile_reference_toggle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            Switch(
                checked = entry.referenceOnFile,
                onCheckedChange = { on ->
                    viewModel.saveExperience(
                        all.mapIndexed { i, other ->
                            if (i == index) other.copy(referenceOnFile = on) else other
                        },
                    )
                },
                modifier = Modifier.testTag("profile_experience_reference_$index"),
            )
        }

        entry.dutyLines.forEach { duty ->
            Row(Modifier.padding(top = Space.xs)) {
                Text("–", color = colors.muted)
                Text(
                    duty,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Space.s),
                )
            }
        }

        // "Tätigkeiten in Ergebnisse umformulieren" used to sit here: an accent-coloured row with
        // the Rewrite icon and no onClick at all, so it read as an action and answered no tap.
        // There is no endpoint behind it either, with or without a model, so the offer is not made
        // until there is something to carry it out.
    }
}

/**
 * A foreign degree with its anabin/ZAB block. The equivalence is never stated as fact until the
 * user has confirmed it — a wrong equivalence claim in a Lebenslauf is worse than none at all.
 */
@Composable
private fun EducationCard(
    entry: de.bewerbo.app.data.Education,
    index: Int,
    all: List<Education>,
    state: AppState,
    viewModel: AppViewModel,
) {
    val colors = LocalSemanticColors.current

    BewerboCard(Modifier.testTag("profile_entry_education_$index")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                BewerboIcons.Education, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.s),
            ) {
                Text(entry.degree, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        entry.institution.ifBlank { null },
                        entry.location.ifBlank { null },
                        "${entry.from.take(4)} – ${entry.to?.take(4) ?: ""}",
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        Row(
            Modifier.padding(top = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (entry.equivalenceConfirmed && !entry.germanEquivalent.isNullOrBlank()) {
                StatusPill(
                    stringResource(R.string.profile_anabin_rating, entry.anabinAssessment.orEmpty()),
                    PillTone.Success,
                    Modifier.testTag("profile_recognition_state_$index"),
                )
            } else {
                StatusPill(
                    stringResource(R.string.profile_anabin_open),
                    PillTone.Attention,
                    Modifier.testTag("profile_recognition_state_$index"),
                )
            }
        }

        if (entry.equivalenceConfirmed && !entry.germanEquivalent.isNullOrBlank()) {
            Text(
                stringResource(R.string.profile_equivalent_to, entry.germanEquivalent),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Space.s),
            )
            // Confirmed is not permanent. An equivalence the user later doubts has to be
            // retractable, or the only safe thing they could do with this feature is avoid it.
            OutlinedButton(
                onClick = {
                    viewModel.saveEducation(
                        all.mapIndexed { i, other ->
                            if (i == index) {
                                other.copy(
                                    equivalenceConfirmed = false,
                                    germanEquivalent = null,
                                    anabinAssessment = null,
                                )
                            } else other
                        },
                    )
                },
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("profile_recognition_withdraw_$index"),
            ) {
                Text(stringResource(R.string.profile_anabin_withdraw))
            }
        }

        OutlinedButton(
            onClick = { viewModel.lookUpDegrees(entry.country.ifBlank { null }) },
            modifier = Modifier
                .padding(top = Space.s)
                .testTag("profile_recognition_lookup"),
        ) {
            Icon(BewerboIcons.Anabin, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.profile_anabin_lookup),
                modifier = Modifier.padding(start = Space.s),
            )
        }

        // A lookup result is an OFFER, not a fact. The product rule is that no equivalence is
        // written into a document until the user has confirmed it, and until now there was no
        // control that could confirm one — the result was shown and could never be acted on, so
        // equivalenceConfirmed stayed false for the lifetime of every profile.
        if (state.degrees.isNotEmpty() && !entry.equivalenceConfirmed) {
            Text(
                stringResource(R.string.profile_anabin_confirm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.padding(top = Space.s),
            )
        }
        state.degrees.take(3).forEachIndexed { degreeIndex, degree ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.s)
                    .testTag("profile_recognition_result_$degreeIndex"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(degree.foreignDegree, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${degree.germanEquivalent}  ·  anabin ${degree.anabinRating}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
                Button(
                    onClick = {
                        viewModel.saveEducation(
                            all.mapIndexed { i, other ->
                                if (i == index) {
                                    other.copy(
                                        germanEquivalent = degree.germanEquivalent,
                                        anabinAssessment = degree.anabinRating,
                                        equivalenceConfirmed = true,
                                    )
                                } else other
                            },
                        )
                    },
                    modifier = Modifier.testTag("profile_recognition_confirm_$degreeIndex"),
                ) {
                    Text(stringResource(R.string.profile_anabin_confirm))
                }
            }
        }
    }
}

@Composable
private fun PersonSection(state: AppState, viewModel: AppViewModel) {
    val person = state.profile?.person ?: return
    var first by remember(person.firstName) { mutableStateOf(person.firstName) }
    var last by remember(person.lastName) { mutableStateOf(person.lastName) }
    var street by remember(person.street) { mutableStateOf(person.street) }
    var postal by remember(person.postalCode) { mutableStateOf(person.postalCode) }
    var city by remember(person.city) { mutableStateOf(person.city) }
    var email by remember(person.email) { mutableStateOf(person.email) }
    var phone by remember(person.phone) { mutableStateOf(person.phone) }

    BewerboCard(Modifier.testTag("profile_section_person")) {
        LabelledField(stringResource(R.string.person_first_name), first, { first = it },
            testTag = "profile_person_first_name")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.person_last_name), last, { last = it },
            testTag = "profile_person_last_name")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.person_street), street, { street = it },
            testTag = "profile_person_street")
        Box(Modifier.padding(top = Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.weight(0.4f)) {
                LabelledField(stringResource(R.string.person_postal_code), postal, { postal = it },
                    testTag = "profile_person_postal_code")
            }
            Box(Modifier.weight(0.6f)) {
                LabelledField(stringResource(R.string.person_city), city, { city = it },
                    testTag = "profile_person_city")
            }
        }
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.person_email), email, { email = it },
            testTag = "profile_person_email")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.person_phone), phone, { phone = it },
            testTag = "profile_person_phone")

        Button(
            onClick = {
                viewModel.savePerson(
                    person.copy(
                        firstName = first, lastName = last, street = street,
                        postalCode = postal, city = city, email = email, phone = phone,
                    ),
                )
            },
            modifier = Modifier
                .padding(top = Space.m)
                .testTag("profile_person_save"),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

/**
 * Adding an experience entry. The fields are the ones a German CV entry is made of — Pensum and
 * Branche included, because leaving them out is what makes a translated CV read as thin.
 */
@Composable
private fun AddExperienceButton(state: AppState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var workload by remember { mutableStateOf("") }
    var duties by remember { mutableStateOf("") }

    if (!open) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_btn_add_experience"),
        ) {
            Icon(BewerboIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.profile_add_experience),
                modifier = Modifier.padding(start = Space.s),
            )
        }
        return
    }

    BewerboCard(Modifier.testTag("profile_new_experience")) {
        SectionLabel(stringResource(R.string.profile_add_experience))
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.experience_position), position, { position = it },
            testTag = "experience_input_position")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.experience_employer), employer, { employer = it },
            testTag = "experience_input_employer")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.experience_location), location, { location = it },
            testTag = "experience_input_location")
        Box(Modifier.padding(top = Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.weight(1f)) {
                LabelledField(stringResource(R.string.experience_from), from, { from = it },
                    testTag = "experience_input_from")
            }
            Box(Modifier.weight(1f)) {
                LabelledField(stringResource(R.string.experience_to), to, { to = it },
                    testTag = "experience_input_to")
            }
        }
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.experience_workload), workload, { workload = it },
            testTag = "experience_input_workload")
        Box(Modifier.padding(top = Space.s))
        LabelledField(
            stringResource(R.string.experience_duties), duties, { duties = it },
            testTag = "experience_input_duties", singleLine = false, minLines = 3,
        )

        // Position, employer and a start date are what an entry IS — without them the server
        // rejects the save and the user loses everything they typed, which is exactly what happens
        // when the soft keyboard hides the date field and they hit Save anyway. Saying what is
        // missing costs one line and is the difference between a form and a trap.
        val missing = buildList {
            if (position.isBlank()) add(stringResource(R.string.experience_position))
            if (employer.isBlank()) add(stringResource(R.string.experience_employer))
            if (normaliseDate(from).length != 10) add(stringResource(R.string.experience_from))
        }
        if (missing.isNotEmpty()) {
            Text(
                stringResource(R.string.experience_missing, missing.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSemanticColors.current.attention,
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("experience_missing_hint"),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Button(
                onClick = {
                    val entry = Experience(
                        position = position, employer = employer, location = location,
                        from = normaliseDate(from), to = to.ifBlank { null }?.let { normaliseDate(it) },
                        workload = workload, duties = duties,
                    )
                    viewModel.saveExperience((state.profile?.experience ?: emptyList()) + entry)
                    open = false
                    position = ""; employer = ""; location = ""; from = ""; to = ""
                    workload = ""; duties = ""
                },
                enabled = missing.isEmpty(),
                modifier = Modifier.testTag("experience_btn_save"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(
                onClick = { open = false },
                modifier = Modifier.testTag("experience_btn_cancel"),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

/**
 * One language, with the switch that says whether its certificate is in the Mappe.
 *
 * That switch is not cosmetic: `certificateOnFile` is what turns an "offen" requirement into a
 * "belegt" one at the Abgleich and what the Übersicht counts under "Nachweise". Until it existed
 * the flag could never become true, so the app went on asking for a certificate the user had
 * already filed.
 */
@Composable
private fun LanguageCard(
    skill: LanguageSkill,
    index: Int,
    all: List<LanguageSkill>,
    viewModel: AppViewModel,
) {
    val colors = LocalSemanticColors.current

    BewerboCard(Modifier.testTag("profile_entry_language_$index")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(skill.language, style = MaterialTheme.typography.titleMedium)
            StatusPill(
                skill.level,
                if (skill.certificateOnFile) PillTone.Success else PillTone.Attention,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(
                    if (skill.certificateOnFile) {
                        R.string.profile_certificate_on_file
                    } else R.string.profile_certificate_missing,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            Switch(
                checked = skill.certificateOnFile,
                onCheckedChange = { on ->
                    viewModel.saveLanguages(
                        all.mapIndexed { i, entry ->
                            if (i == index) entry.copy(certificateOnFile = on) else entry
                        },
                    )
                },
                modifier = Modifier.testTag("profile_language_certificate_$index"),
            )
        }
    }
}

/**
 * Adding a language. Level is typed rather than picked from a list because the levels a posting
 * asks for are not only the CEFR ones — "Muttersprache" is the other half of this product's
 * audience, and the Abgleich reads it.
 */
@Composable
private fun AddLanguageButton(state: AppState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }

    if (!open) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_btn_add_language"),
        ) {
            Icon(BewerboIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.profile_add_language),
                modifier = Modifier.padding(start = Space.s),
            )
        }
        return
    }

    BewerboCard(Modifier.testTag("profile_new_language")) {
        SectionLabel(stringResource(R.string.profile_add_language))
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.language_name), language, { language = it },
            testTag = "language_input_name")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.language_level), level, { level = it },
            testTag = "language_input_level")

        val missing = buildList {
            if (language.isBlank()) add(stringResource(R.string.language_name))
            if (level.isBlank()) add(stringResource(R.string.language_level))
        }
        if (missing.isNotEmpty()) {
            Text(
                stringResource(R.string.experience_missing, missing.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSemanticColors.current.attention,
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("language_missing_hint"),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Button(
                onClick = {
                    val entry = LanguageSkill(language = language.trim(), level = level.trim())
                    viewModel.saveLanguages((state.profile?.languages ?: emptyList()) + entry)
                    open = false
                    language = ""; level = ""
                },
                enabled = missing.isEmpty(),
                modifier = Modifier.testTag("language_btn_save"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(
                onClick = { open = false },
                modifier = Modifier.testTag("language_btn_cancel"),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

/**
 * Adding an Ausbildung entry.
 *
 * The country is asked for because it is what decides whether the degree needs an anabin/ZAB
 * equivalence at all — a German Abschluss does not, and a foreign one is the whole reason this
 * product exists. Without this form the Ausbildung section was a list nobody could ever add to,
 * which also put the recognition block out of reach.
 */
@Composable
private fun AddEducationButton(state: AppState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    var degree by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    if (!open) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_btn_add_education"),
        ) {
            Icon(BewerboIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.profile_add_education),
                modifier = Modifier.padding(start = Space.s),
            )
        }
        return
    }

    BewerboCard(Modifier.testTag("profile_new_education")) {
        SectionLabel(stringResource(R.string.profile_add_education))
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.education_degree), degree, { degree = it },
            testTag = "education_input_degree")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.education_institution), institution, { institution = it },
            testTag = "education_input_institution")
        Box(Modifier.padding(top = Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.weight(0.6f)) {
                LabelledField(stringResource(R.string.education_location), location, { location = it },
                    testTag = "education_input_location")
            }
            Box(Modifier.weight(0.4f)) {
                LabelledField(stringResource(R.string.education_country), country, { country = it },
                    testTag = "education_input_country")
            }
        }
        Box(Modifier.padding(top = Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.weight(1f)) {
                LabelledField(stringResource(R.string.education_from), from, { from = it },
                    testTag = "education_input_from")
            }
            Box(Modifier.weight(1f)) {
                LabelledField(stringResource(R.string.education_to), to, { to = it },
                    testTag = "education_input_to")
            }
        }

        // The same contract the Berufserfahrung form keeps: say what is missing rather than
        // letting the server reject the save and lose everything the user typed.
        val missing = buildList {
            if (degree.isBlank()) add(stringResource(R.string.education_degree))
            if (institution.isBlank()) add(stringResource(R.string.education_institution))
            if (normaliseDate(from).length != 10) add(stringResource(R.string.education_from))
        }
        if (missing.isNotEmpty()) {
            Text(
                stringResource(R.string.experience_missing, missing.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSemanticColors.current.attention,
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("education_missing_hint"),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Button(
                onClick = {
                    val entry = Education(
                        degree = degree, institution = institution, location = location,
                        country = country.trim().uppercase(),
                        from = normaliseDate(from),
                        to = to.ifBlank { null }?.let { normaliseDate(it) },
                    )
                    viewModel.saveEducation((state.profile?.education ?: emptyList()) + entry)
                    open = false
                    degree = ""; institution = ""; location = ""; country = ""; from = ""; to = ""
                },
                enabled = missing.isEmpty(),
                modifier = Modifier.testTag("education_btn_save"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(
                onClick = { open = false },
                modifier = Modifier.testTag("education_btn_cancel"),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

/// "2019-04" and "04/2019" both mean the same thing to a person. The server wants an ISO date.
private fun normaliseDate(input: String): String {
    val trimmed = input.trim()
    return when {
        Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed) -> trimmed
        Regex("""^\d{4}-\d{2}$""").matches(trimmed) -> "$trimmed-01"
        Regex("""^\d{2}[./]\d{4}$""").matches(trimmed) -> {
            val (month, year) = trimmed.split('.', '/')
            "$year-$month-01"
        }
        Regex("""^\d{4}$""").matches(trimmed) -> "$trimmed-01-01"
        else -> trimmed
    }
}

private fun sectionLabel(name: String) = when (name) {
    "person" -> R.string.profile_section_person
    "berufserfahrung" -> R.string.profile_section_experience
    "ausbildung" -> R.string.profile_section_education
    "sprachen" -> R.string.profile_section_languages
    else -> R.string.profile_section_attachments
}

