package de.bewerbo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.AssistantProposalChoice
import de.bewerbo.app.data.AssistantTurn
import de.bewerbo.app.data.ProposalDecision
import de.bewerbo.app.data.hasAssistant
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.IconRow
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.CardElevation
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/// The openers offered on an empty screen, in the order they are drawn. Three, because what they
/// are for is showing the SHAPE of an answer — a short line about one station of a life — and a
/// fourth teaches nothing the third did not.
private val EXAMPLES = listOf(
    R.string.assistant_example_1,
    R.string.assistant_example_2,
    R.string.assistant_example_3,
)

/**
 * Assistant — the first minutes of Bewerbo, as a conversation instead of a form.
 *
 * A user arrives with a life and no idea which of its parts a German employer reads. The form asks
 * for that life field by field, which is the right way to CORRECT it and the wrong way to begin:
 * it presumes the user already knows what a Lebenslauf wants. Here they write three sentences in
 * their own language, or photograph the Lebenslauf they already have, and what comes back names
 * what was understood and what is still missing.
 *
 * Nothing here writes into the profile. The assistant produces prose, and the form remains the one
 * place a profile is written — which is also why this screen sends the user there where no model is
 * configured: a chat backed by regular expressions would be the exact dishonesty this screen is the
 * answer to. See [AppState.hasAssistant].
 *
 * [onOpenProfile] goes to the profile the way the bar goes there, because it is a place and not a
 * step — see MainActivity.
 */
@Composable
fun AssistantScreen(state: AppState, viewModel: AppViewModel, onOpenProfile: () -> Unit) {
    val colors = LocalSemanticColors.current
    val turns = state.assistant
    val thinking = state.busy == AppViewModel.ASSISTANT
    val listState = rememberLazyListState()

    // The system photo picker, as the Stellenanzeige uses it: it asks for no storage permission and
    // hands back the one picture that was chosen. The words are read on the device and land in the
    // composer, where the user corrects them before anything is sent.
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        picked?.let(viewModel::readAssistantImage)
    }

    // The newest turn is the one being read, so it is the one on screen. The thinking row counts as
    // a change too: it appears below the turn that was just sent.
    LaunchedEffect(turns.size, thinking) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("assistant_screen"),
    ) {
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item {
                Column {
                    Text(
                        stringResource(R.string.nav_assistant),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        stringResource(R.string.assistant_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
            }

            // Who writes these answers, at the top and before the first one is read. A disclosure
            // under the thing it qualifies is a disclosure nobody meets — the same placement rule
            // the writer notice on the Bewerbung screen follows.
            if (state.hasAssistant) {
                item {
                    Column {
                        StatusPill(
                            stringResource(R.string.assistant_writer_pill),
                            PillTone.Accent,
                            Modifier.testTag("assistant_writer_pill"),
                        )
                        Text(
                            stringResource(R.string.assistant_writer_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
            } else {
                // No model, no assistant — and the form is not a lesser way in, it is the way in
                // that always works. Said here rather than by an empty screen the user waits at.
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        Callout(
                            icon = BewerboIcons.Attention,
                            title = stringResource(R.string.assistant_unavailable_title),
                            body = stringResource(R.string.assistant_unavailable_body),
                            tone = PillTone.Attention,
                            modifier = Modifier.testTag("assistant_unavailable"),
                        )
                        Button(
                            onClick = onOpenProfile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("assistant_btn_profile"),
                        ) {
                            Icon(
                                BewerboIcons.Person, contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.assistant_unavailable_action),
                                modifier = Modifier.padding(start = Space.s),
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            if (turns.isEmpty()) {
                item {
                    Callout(
                        icon = BewerboIcons.Assistant,
                        title = stringResource(R.string.assistant_empty_title),
                        body = stringResource(R.string.assistant_empty_body),
                        modifier = Modifier.testTag("assistant_empty"),
                    )
                }
                item {
                    BewerboCard {
                        SectionLabel(stringResource(R.string.assistant_examples))
                        EXAMPLES.forEachIndexed { index, example ->
                            val text = stringResource(example)
                            IconRow(
                                icon = BewerboIcons.Rewrite,
                                title = text,
                                tone = PillTone.Neutral,
                                modifier = Modifier.testTag("assistant_example_$index"),
                                // An example is an opener, not a message: it goes into the composer
                                // so the user changes it to their own life before sending it.
                                onClick = { viewModel.setAssistantDraft(text) },
                            )
                        }
                        Text(
                            stringResource(R.string.assistant_photo_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            modifier = Modifier
                                .padding(top = Space.s)
                                .testTag("assistant_photo_hint"),
                        )
                    }
                }
            }

            itemsIndexed(turns) { index, turn ->
                // What is still missing belongs to the LATEST answer only. An older one was answered
                // by the turns after it, and leaving it standing would have the screen ask twice for
                // something the user has since said.
                //
                // The proposals do NOT work that way: each is still undecided until it is answered,
                // and one on an older turn is a thing the user has not got round to rather than a
                // question that has been overtaken.
                AssistantTurnCard(index, turn, latest = index == turns.lastIndex, state, viewModel)
            }

            // An answer takes a model call, and a screen that shows nothing while it runs is a
            // screen the user taps again. The Übersicht's own "one moment" says it the same way.
            if (thinking) {
                item {
                    Callout(
                        icon = BewerboIcons.Refresh,
                        title = stringResource(R.string.assistant_thinking_title),
                        body = stringResource(R.string.assistant_thinking_body),
                        modifier = Modifier.testTag("assistant_thinking"),
                    )
                }
            }
        }

        // The composer stands still while the conversation scrolls past it, which is what a
        // conversation needs and a LazyColumn item cannot do. The raised surface is the one the
        // flow's rail uses, so a bar of controls looks the same wherever the app puts one.
        if (state.hasAssistant) {
            AssistantComposer(state, viewModel) {
                pickPicture.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }
}

/// One turn: who said it, what they said, what it proposes for the profile, and — on the latest
/// answer — what it says is missing.
@Composable
private fun AssistantTurnCard(
    index: Int,
    turn: AssistantTurn,
    latest: Boolean,
    state: AppState,
    viewModel: AppViewModel,
) {
    val colors = LocalSemanticColors.current

    BewerboCard(Modifier.testTag("assistant_turn_$index")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (turn.fromUser) BewerboIcons.Person else BewerboIcons.Assistant,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(16.dp),
            )
            SectionLabel(
                stringResource(if (turn.fromUser) R.string.assistant_you else R.string.assistant_it),
                Modifier.padding(start = Space.xs),
            )
        }
        Text(
            turn.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Space.xs),
        )

        if (turn.proposals.isNotEmpty()) {
            AssistantProposalSection(index, turn.proposals, state, viewModel)
        }

        if (!turn.fromUser && latest && turn.missing.isNotEmpty()) {
            Column(Modifier.padding(top = Space.s).testTag("assistant_missing")) {
                SectionLabel(stringResource(R.string.assistant_missing))
                turn.missing.forEach { line ->
                    IconRow(icon = BewerboIcons.Attention, title = line, tone = PillTone.Attention)
                }
            }
        }
    }
}

/**
 * What the assistant understood, as it would stand in the profile — each proposal beside the words
 * it was read from, and each answered on its own.
 *
 * This is the same bargain the experience card's duty rewrite strikes, moved to where the user
 * first meets it: the German is a SUGGESTION until it is taken, it is read next to the user's own
 * wording, and what is saved is the string that was on screen. It matters more here than there,
 * because the German the assistant writes is what an employer will read and the user cannot judge
 * it from a sentence of prose saying it was understood.
 *
 * A refused proposal keeps its card and says it was left out. Letting it disappear would read as
 * something having happened to the user's own words, and nothing has: they stand as they were
 * written, in the turn above.
 */
@Composable
private fun AssistantProposalSection(
    turn: Int,
    proposals: List<AssistantProposalChoice>,
    state: AppState,
    viewModel: AppViewModel,
) {
    val colors = LocalSemanticColors.current

    Column(
        Modifier
            .padding(top = Space.s)
            .testTag("assistant_proposals_$turn"),
    ) {
        SectionLabel(stringResource(R.string.assistant_proposals))
        Text(
            stringResource(R.string.assistant_proposals_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            modifier = Modifier.padding(top = Space.xs),
        )

        proposals.forEachIndexed { index, choice ->
            AssistantProposalCard(turn, index, choice, state, viewModel)
        }
    }
}

/// One proposal: which section it belongs in, the user's own words, the German proposed for them,
/// and the two answers — or, once it has been answered, which of the two was given.
@Composable
private fun AssistantProposalCard(
    turn: Int,
    index: Int,
    choice: AssistantProposalChoice,
    state: AppState,
    viewModel: AppViewModel,
) {
    val colors = LocalSemanticColors.current
    val proposal = choice.proposal
    val tag = "assistant_proposal_${turn}_$index"

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.m)
            .testTag(tag),
    ) {
        StatusPill(stringResource(sectionLabel(proposal.kind)), PillTone.Neutral)

        // The user's own words first, and the German under them. The order is the claim: this is
        // what you said, and this is what we would write for it — not the other way round.
        Text(
            stringResource(R.string.assistant_proposal_source),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            modifier = Modifier.padding(top = Space.s),
        )
        Text(
            proposal.source,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("${tag}_source"),
        )

        Text(
            stringResource(R.string.assistant_proposal_german),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            modifier = Modifier.padding(top = Space.s),
        )
        // Drawn exactly as the profile draws the entry it would become — the title on its own line
        // and the rest beneath it — so that "the profile shows what I read" is something the user
        // can see rather than take on trust.
        Text(
            proposal.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("${tag}_german"),
        )
        val detail = listOfNotNull(
            proposal.detail.ifBlank { null },
            proposal.from.ifBlank { null }?.let { from ->
                "${from.take(7)} – ${
                    proposal.to.ifBlank { null }?.take(7)
                        ?: stringResource(R.string.profile_entry_ongoing)
                }"
            },
        ).joinToString("  ·  ")
        if (detail.isNotEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.testTag("${tag}_detail"),
            )
        }

        when (choice.decision) {
            ProposalDecision.Pending -> Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Button(
                    onClick = { viewModel.acceptProposal(turn, index) },
                    // One save at a time, as the composer's own buttons are gated: two proposals
                    // accepted at once would each send the section as it stood before the other.
                    enabled = state.busy == null,
                    modifier = Modifier.testTag("${tag}_accept"),
                ) {
                    Text(stringResource(R.string.assistant_proposal_accept))
                }
                OutlinedButton(
                    onClick = { viewModel.refuseProposal(turn, index) },
                    modifier = Modifier.testTag("${tag}_keep"),
                ) {
                    Text(stringResource(R.string.assistant_proposal_keep))
                }
            }

            ProposalDecision.Accepted -> Text(
                stringResource(R.string.assistant_proposal_accepted),
                style = MaterialTheme.typography.bodySmall,
                color = colors.success,
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("${tag}_accepted"),
            )

            ProposalDecision.Kept -> Text(
                stringResource(R.string.assistant_proposal_kept),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("${tag}_kept"),
            )
        }
    }
}

/// The name of the profile section a proposal would land in, in the user's language — the same
/// string the Profil screen writes over that section, so the two agree on what it is called.
private fun sectionLabel(kind: String) = when (kind) {
    AppViewModel.EXPERIENCE_SECTION -> R.string.profile_section_experience
    AppViewModel.EDUCATION_SECTION -> R.string.profile_section_education
    else -> R.string.profile_section_languages
}

/// The composer: the one field, the picture beside it and the send button.
///
/// The picture is an [IconButton] rather than a second labelled button because both would then
/// share the width with a long word in it — the same problem the bottom bar's labels have — and its
/// contentDescription carries the label a driver and a screen reader need.
@Composable
private fun AssistantComposer(state: AppState, viewModel: AppViewModel, onChoosePicture: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = CardElevation) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.m),
        ) {
            LabelledField(
                label = stringResource(R.string.assistant_composer),
                value = state.assistantDraft,
                onValueChange = viewModel::setAssistantDraft,
                testTag = "assistant_composer",
                singleLine = false,
                minLines = 3,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onChoosePicture,
                    enabled = state.busy == null,
                    modifier = Modifier.testTag("assistant_btn_image"),
                ) {
                    Icon(
                        BewerboIcons.Document,
                        contentDescription = stringResource(R.string.assistant_photo),
                    )
                }
                Button(
                    onClick = { viewModel.askAssistant() },
                    enabled = state.assistantDraft.isNotBlank() && state.busy == null,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Space.s)
                        .testTag("assistant_btn_send"),
                ) {
                    Icon(BewerboIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.assistant_send),
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
            }
        }
    }
}
