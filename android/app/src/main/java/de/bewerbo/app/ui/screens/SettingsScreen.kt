package de.bewerbo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.DataCategory
import de.bewerbo.app.ui.TermNote
import de.bewerbo.app.ui.UI_LANGUAGES
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.BewerboDialog
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.IconRow
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.LanguageSelector
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.ScreenHeader
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.labelOf
import de.bewerbo.app.ui.germanTerm
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/**
 * Einstellungen — the account, the language of the interface, what is held about the user, and the
 * legal pages.
 *
 * It is NOT on the bottom bar, and that is deliberate: the bar carries the three places a user
 * works in, and this is not one of them — see the rule in MainActivity. It is opened from the
 * Übersicht and it says how to leave, which is what [ScreenHeader] exists for.
 *
 * The three things the card asks for stand as three cards, in the order a user needs them: who you
 * are here, the language you read in, and what is held about you with the two rights that go with
 * it. The legal pages are their own card at the bottom, one row each.
 */
@Composable
fun SettingsScreen(
    state: AppState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenLegal: (LegalPage) -> Unit,
) {
    // Fetched when the screen opens and again whenever the account changes underneath it: deleting
    // an account and taking over another one both leave a new id behind, and the categories on
    // screen would otherwise still count the account that is gone.
    LaunchedEffect(state.profile?.id) { viewModel.loadSettings() }

    var switching by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            subtitle = stringResource(R.string.settings_subtitle),
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s),
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.m, end = Space.m, bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item { AccountCard(state) { switching = true } }
            item { LanguageCard(state, viewModel) }
            item { DataCard(state, viewModel) { deleting = true } }
            item { LegalCard(onOpenLegal) }
        }
    }

    if (switching) {
        SwitchAccountDialog(viewModel) { switching = false }
    }
    if (deleting) {
        DeleteAccountDialog(viewModel) { deleting = false }
    }
}

/**
 * The account, which until now the user had no way of seeing at all.
 *
 * The key is the account. There is no password to show instead, and there never was one — the id
 * was written into the device on first run and nobody was told about it, so a new phone meant
 * starting again from an empty profile with the old one still on the server. Showing it is what
 * turns that id into something the user owns.
 */
@Composable
private fun AccountCard(state: AppState, onSwitch: () -> Unit) {
    val colors = LocalSemanticColors.current
    val person = state.profile?.person
    val name = listOfNotNull(person?.firstName?.ifBlank { null }, person?.lastName?.ifBlank { null })
        .joinToString(" ")

    BewerboCard(Modifier.testTag("settings_account_card")) {
        SectionLabel(stringResource(R.string.settings_account))
        Text(
            name.ifBlank { stringResource(R.string.settings_account_unnamed) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = Space.xs)
                .testTag("settings_account_name"),
        )

        // The sentence stands ABOVE the key it talks about, because it says "the key below" — it is
        // what tells the reader that the line of hex under it is not a diagnostic.
        Text(
            stringResource(R.string.settings_account_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            modifier = Modifier.padding(top = Space.s),
        )
        SectionLabel(
            stringResource(R.string.settings_account_key),
            Modifier.padding(top = Space.m),
        )
        // Selectable, because a key that cannot be copied is a key that has to be typed off a
        // screen by somebody entering it on a second device — thirty-six characters of it.
        SelectionContainer {
            Text(
                state.profile?.id.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = Space.xs)
                    .testTag("settings_account_key_value"),
            )
        }

        OutlinedButton(
            onClick = onSwitch,
            modifier = Modifier
                .padding(top = Space.s)
                .fillMaxWidth()
                .testTag("settings_btn_switch_account"),
        ) {
            Icon(BewerboIcons.Person, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.settings_account_switch),
                modifier = Modifier.padding(start = Space.s),
            )
        }
    }
}

/// The language of the interface. It stood on the Profil screen beside the INPUT language, which is
/// profile data and stayed there; this one is a preference of the device and belongs here.
@Composable
private fun LanguageCard(state: AppState, viewModel: AppViewModel) {
    val colors = LocalSemanticColors.current

    BewerboCard(Modifier.testTag("settings_language_card")) {
        SectionLabel(stringResource(R.string.settings_language))
        Row(Modifier.padding(top = Space.s)) {
            LanguageSelector(
                label = stringResource(
                    R.string.profile_app_language,
                    labelOf(UI_LANGUAGES, state.uiLanguage),
                ),
                icon = BewerboIcons.Globe,
                options = UI_LANGUAGES,
                testTagPrefix = "settings_ui_language",
                onPick = viewModel::setUiLanguage,
            )
        }
        Text(
            stringResource(R.string.settings_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            modifier = Modifier
                .padding(top = Space.s)
                .testTag("settings_language_hint"),
        )
    }
}

/**
 * What is held about the user, with the two things they may do about it.
 *
 * All three rights the card names are here and nowhere else: seeing is the list, taking a copy is
 * the first button, having it deleted is the second. A category with nothing in it is still listed
 * — a zero answers the question too, and leaving it out reads as something withheld.
 */
@Composable
private fun DataCard(state: AppState, viewModel: AppViewModel, onDelete: () -> Unit) {
    val colors = LocalSemanticColors.current
    val data = state.accountData

    BewerboCard(Modifier.testTag("settings_data_card")) {
        SectionLabel(stringResource(R.string.settings_data))
        Text(
            stringResource(R.string.settings_data_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            modifier = Modifier.padding(top = Space.xs),
        )

        if (data == null) {
            // Nothing fetched, and the snackbar that said why is long gone. A screen that waits for
            // a call nobody runs a second time waits for ever — the same trap the Übersicht had.
            if (state.busy != "settings") {
                Callout(
                    icon = BewerboIcons.Attention,
                    title = stringResource(R.string.overview_unreachable_title),
                    body = stringResource(R.string.overview_unreachable_body),
                    tone = PillTone.Attention,
                    modifier = Modifier
                        .padding(top = Space.s)
                        .testTag("settings_data_unreachable"),
                )
                OutlinedButton(
                    onClick = { viewModel.loadSettings() },
                    modifier = Modifier
                        .padding(top = Space.s)
                        .fillMaxWidth()
                        .testTag("settings_btn_data_retry"),
                ) {
                    Icon(BewerboIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.overview_retry),
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
            }
            return@BewerboCard
        }

        data.categories.forEach { category -> DataRow(category) }

        OutlinedButton(
            onClick = { viewModel.exportAccountData() },
            modifier = Modifier
                .padding(top = Space.s)
                .fillMaxWidth()
                .testTag("settings_btn_export"),
        ) {
            Icon(BewerboIcons.Export, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.settings_data_export),
                modifier = Modifier.padding(start = Space.s),
            )
        }
        Text(
            stringResource(R.string.settings_data_export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            modifier = Modifier.padding(top = Space.xs),
        )

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier
                .padding(top = Space.s)
                .fillMaxWidth()
                .testTag("settings_btn_delete"),
        ) {
            Icon(
                BewerboIcons.Trash, contentDescription = null,
                tint = colors.danger, modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.settings_data_delete),
                color = colors.danger,
                modifier = Modifier.padding(start = Space.s),
            )
        }
    }
}

/// One category and how much of it is held. Laid out like the label row of a [Meter], because it is
/// the same kind of statement: a name on the left, the figure it stands for on the right.
@Composable
private fun DataRow(category: DataCategory) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.s)
            .testTag("settings_data_${category.key}"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(dataCategoryLabel(category.key), style = MaterialTheme.typography.bodyMedium)
        Text(
            category.count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/// The name of a category, in the user's language. The server sends the key; the screen writes the
/// word — the same division every other answer of the API follows.
@Composable
private fun dataCategoryLabel(key: String): String = when (key) {
    "person" -> stringResource(R.string.data_category_person)
    "berufserfahrung" -> stringResource(R.string.data_category_berufserfahrung)
    "ausbildung" -> stringResource(R.string.data_category_ausbildung)
    "sprachen" -> stringResource(R.string.data_category_sprachen)
    "luecken" -> stringResource(R.string.data_category_luecken)
    "anlagen" -> stringResource(R.string.data_category_anlagen)
    "stellenanzeigen" -> stringResource(R.string.data_category_stellenanzeigen)
    "bewerbungen" -> stringResource(R.string.data_category_bewerbungen)
    else -> key
}

/// The legal pages, one row each. Each row explains the German word it carries where the user first
/// meets it, which is the second half of the rule in ui/GermanTerms.kt.
@Composable
private fun LegalCard(onOpen: (LegalPage) -> Unit) {
    BewerboCard(Modifier.testTag("settings_legal_card")) {
        SectionLabel(stringResource(R.string.settings_legal))
        LegalPage.entries.forEach { page ->
            Column {
                IconRow(
                    icon = BewerboIcons.Document,
                    title = stringResource(page.label),
                    modifier = Modifier.testTag("settings_${page.tag}"),
                    onClick = { onOpen(page) },
                    trailing = {
                        Icon(BewerboIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                TermNote(germanTerm(page.termSlug))
            }
        }
    }
}

/**
 * Taking over an account that already exists, by its key.
 *
 * Through [BewerboDialog] and not AlertDialog, as every dialog in this app is: the dialog composes
 * in a window of its own, which re-provides the locale and drops the test-tag flag, and it holds a
 * text field the keyboard would otherwise cover.
 */
@Composable
private fun SwitchAccountDialog(viewModel: AppViewModel, onClose: () -> Unit) {
    var key by remember { mutableStateOf("") }

    BewerboDialog(
        onDismissRequest = onClose,
        testTag = "settings_switch_dialog",
        title = { Text(stringResource(R.string.settings_account_switch)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_account_switch_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LabelledField(
                    label = stringResource(R.string.settings_account_key),
                    value = key,
                    onValueChange = { key = it },
                    testTag = "settings_switch_key",
                    modifier = Modifier.padding(top = Space.s),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.useAccount(key)
                    onClose()
                },
                modifier = Modifier.testTag("settings_switch_confirm"),
            ) { Text(stringResource(R.string.settings_account_switch_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("settings_switch_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/// Asked before erasing, because nothing brings it back. The confirming button says what it does
/// rather than "OK" — it is the last thing read before the data is gone.
@Composable
private fun DeleteAccountDialog(viewModel: AppViewModel, onClose: () -> Unit) {
    val colors = LocalSemanticColors.current

    BewerboDialog(
        onDismissRequest = onClose,
        testTag = "settings_delete_dialog",
        title = { Text(stringResource(R.string.settings_delete_title)) },
        text = {
            Text(
                stringResource(R.string.settings_delete_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteAccount()
                    onClose()
                },
                modifier = Modifier.testTag("settings_delete_confirm"),
            ) { Text(stringResource(R.string.action_delete), color = colors.danger) }
        },
        dismissButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("settings_delete_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
