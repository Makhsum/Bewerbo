package de.bewerbo.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.LegalOperator
import de.bewerbo.app.ui.TermNote
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.ScreenHeader
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.germanTerm
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.Space

/**
 * The three legal pages, as destinations.
 *
 * They are not places on the bar and not steps of the flow — they are pages of the settings, so
 * they keep their own routes and are pushed on top of it. [termSlug] is the kept German word the row
 * that leads here carries, explained where the user first meets it; LegalPagesTest is the gate that
 * fails the build if one of the three goes missing or loses its text in a language.
 */
enum class LegalPage(
    val route: String,
    val tag: String,
    @StringRes val label: Int,
    val termSlug: String,
) {
    Terms("agb", "legal_agb", R.string.legal_terms, "agb"),
    Privacy("datenschutz", "legal_datenschutz", R.string.legal_privacy, "dsgvo"),
    Imprint("impressum", "legal_impressum", R.string.legal_imprint, "impressum"),
}

/**
 * One legal page, read rather than acted on.
 *
 * The prose is in the app's own resources, in every language the interface is offered in. Two things
 * are NOT: who operates this installation, and whether what the user types is handed to a model
 * outside it. Both come from GET /api/legal, because both belong to the deployment — an address
 * compiled into the app would be wrong for every deployment but one, and a privacy notice that
 * names the wrong processor is the one mistake on these pages that has consequences.
 */
@Composable
fun LegalScreen(page: LegalPage, state: AppState, viewModel: AppViewModel, onBack: () -> Unit) {
    // The pages that need the server fetch it themselves. The settings screen has usually done it
    // already, but a page is a destination of its own and must not depend on where it was opened
    // from — and this is also what makes the retry below work.
    LaunchedEffect(page) { if (state.legal == null) viewModel.loadSettings() }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("${page.tag}_screen"),
    ) {
        ScreenHeader(
            title = stringResource(page.label),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s),
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.m, end = Space.m, bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item { TermNote(germanTerm(page.termSlug)) }
            when (page) {
                LegalPage.Terms -> item { TermsCard() }
                LegalPage.Privacy -> item { PrivacyCard(state, viewModel) }
                LegalPage.Imprint -> item { ImprintCard(state, viewModel) }
            }
        }
    }
}

@Composable
private fun TermsCard() {
    BewerboCard(Modifier.testTag("legal_agb_body")) {
        Paragraphs(
            R.string.legal_terms_scope,
            R.string.legal_terms_service,
            R.string.legal_terms_yours,
            R.string.legal_terms_check,
            R.string.legal_terms_liability,
            R.string.legal_terms_end,
            R.string.legal_terms_law,
        )
    }
}

/**
 * The privacy notice, whose sentences all have to be true of THIS installation.
 *
 * The paragraph about the language model is chosen by what the server reports rather than written
 * once and hoped over: with no model configured the rule-based writer runs and nothing the user
 * typed leaves the server, and saying otherwise would be a false statement about where their data
 * goes. The host comes from the endpoint the API actually calls.
 */
@Composable
private fun PrivacyCard(state: AppState, viewModel: AppViewModel) {
    val legal = state.legal

    BewerboCard(Modifier.testTag("legal_datenschutz_body")) {
        Paragraphs(
            R.string.legal_privacy_controller,
            R.string.legal_privacy_stored,
            R.string.legal_privacy_files,
            R.string.legal_privacy_why,
        )

        if (legal == null) {
            Unreachable(state, viewModel, "legal_datenschutz")
            return@BewerboCard
        }

        Text(
            if (legal.modelProcessor.isBlank()) {
                stringResource(R.string.legal_privacy_model_none)
            } else {
                stringResource(R.string.legal_privacy_model, legal.modelProcessor)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(top = Space.m)
                .testTag("legal_privacy_model"),
        )
        Paragraphs(R.string.legal_privacy_kept, R.string.legal_privacy_rights)
    }
}

/**
 * The Impressum — the one page that is nothing but what the installation says about itself.
 *
 * Where it has said nothing, the page says THAT. Empty lines under "Operator" read as an address
 * the reader failed to see, and a placeholder would be a false statement about who answers for the
 * service — which is the opposite of what an Impressum is for.
 */
@Composable
private fun ImprintCard(state: AppState, viewModel: AppViewModel) {
    val legal = state.legal

    BewerboCard(Modifier.testTag("legal_impressum_body")) {
        Text(
            stringResource(R.string.legal_imprint_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (legal == null) {
            Unreachable(state, viewModel, "legal_impressum")
            return@BewerboCard
        }

        val operator = legal.operatorDetails
        if (!operator.stated) {
            Callout(
                icon = BewerboIcons.Attention,
                title = stringResource(R.string.legal_imprint),
                body = stringResource(R.string.legal_imprint_missing),
                tone = PillTone.Attention,
                modifier = Modifier
                    .padding(top = Space.m)
                    .testTag("legal_impressum_missing"),
            )
            return@BewerboCard
        }

        Entry(R.string.legal_imprint_operator, operator.name, "legal_impressum_operator")
        Entry(R.string.legal_imprint_address, operator.address(), "legal_impressum_address")
        if (operator.represented.isNotBlank()) {
            Entry(R.string.legal_imprint_represented, operator.represented, "legal_impressum_represented")
        }
        if (operator.register.isNotBlank()) {
            Entry(R.string.legal_imprint_register, operator.register, "legal_impressum_register")
        }
        Entry(R.string.legal_imprint_contact, operator.email, "legal_impressum_contact")
    }
}

/// The postal address on the lines a German address stands on: street, then postcode and place, then
/// the country when one was given.
private fun LegalOperator.address(): String =
    listOf(street, listOf(postalCode, city).filter { it.isNotBlank() }.joinToString(" "), country)
        .filter { it.isNotBlank() }
        .joinToString("\n")

/// One labelled particular of the Impressum.
@Composable
private fun Entry(@StringRes label: Int, value: String, testTag: String) {
    Column(Modifier.padding(top = Space.m)) {
        SectionLabel(stringResource(label))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(top = Space.xs)
                .testTag(testTag),
        )
    }
}

/// The paragraphs of a page, spaced as paragraphs and not as list rows.
@Composable
private fun Paragraphs(@StringRes vararg text: Int) {
    text.forEach { paragraph ->
        Text(
            stringResource(paragraph),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.m),
        )
    }
}

/// What a page says when the fetch it needs has not landed — with the way to run it again, because
/// nothing else will. A legal page that waits for ever is a legal page that is not there.
@Composable
private fun Unreachable(state: AppState, viewModel: AppViewModel, tagPrefix: String) {
    if (state.busy == "settings") return

    Callout(
        icon = BewerboIcons.Attention,
        title = stringResource(R.string.overview_unreachable_title),
        body = stringResource(R.string.overview_unreachable_body),
        tone = PillTone.Attention,
        modifier = Modifier
            .padding(top = Space.m)
            .testTag("${tagPrefix}_unreachable"),
    )
    OutlinedButton(
        onClick = { viewModel.loadSettings() },
        modifier = Modifier
            .padding(top = Space.s)
            .fillMaxWidth()
            .testTag("${tagPrefix}_retry"),
    ) {
        Icon(BewerboIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.overview_retry), modifier = Modifier.padding(start = Space.s))
    }
}
