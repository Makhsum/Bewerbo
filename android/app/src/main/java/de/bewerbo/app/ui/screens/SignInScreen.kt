package de.bewerbo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.ui.UI_LANGUAGES
import de.bewerbo.app.ui.components.BewerboDialog
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.LanguageSelector
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.errorMessage
import de.bewerbo.app.ui.components.labelOf
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/**
 * The door — the one screen that stands in front of the Übersicht.
 *
 * It is drawn by MainActivity ABOVE the Scaffold rather than as a destination of the NavHost, and
 * that is what keeps the rest of the app exactly where it was: the bottom bar carries the same
 * three places, the flow has the same three steps, and nothing in here can be reached by a stray
 * navigate(). See the rule in MainActivity.
 *
 * Two things stand on it that would be easy to leave behind, because everything that carries them
 * today is BEHIND the door. The interface language is one: the settings are inside, and somebody
 * who reads no English has to be able to change the language before they can get through. The
 * Impressum, the AGB and the privacy notice are the other — what this installation does with what
 * you type is read BEFORE you type it, not after. Both are reached without an account, which is
 * why [AppViewModel.loadLegal] exists apart from the settings' own fetch.
 *
 * The failure is shown HERE and not in the snackbar. A snackbar over an empty screen names no field
 * and is gone in seconds; the line under the form stays until the user has done something about it.
 * MainActivity is what holds the snackbar back while this screen is up.
 */
@Composable
fun SignInScreen(state: AppState, viewModel: AppViewModel) {
    // Which of the two the door is offering. A phone that is still holding a nameless Lebenslauf
    // opens on "Create an account", because that is the one that keeps it; every other phone opens
    // on the sign-in, because a user who is here twice is the ordinary case.
    var registering by remember { mutableStateOf(state.adoptableProfileId != null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var legalPage by remember { mutableStateOf<LegalPage?>(null) }
    var leaving by remember { mutableStateOf(false) }

    val page = legalPage
    if (page != null) {
        // Read in place of the door rather than on top of it: there is no NavHost above the
        // Scaffold to push onto, and the arrow in the page's own header is the way back.
        //
        // The inset is applied HERE for the same reason. Inside the app the Scaffold hands every
        // screen its padding, and these pages have never had to ask; opened from the door there is
        // no Scaffold above them, and the header ran under the status bar.
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            LegalScreen(page, state, viewModel) { legalPage = null }
        }
        return
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .testTag("signin_screen"),
        ) {
            Wordmark(state, viewModel)

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Space.m, end = Space.m, bottom = Space.m,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                item {
                    Text(
                        stringResource(
                            if (registering) R.string.signin_create_title else R.string.signin_title,
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("signin_headline"),
                    )
                }
                item {
                    Text(
                        stringResource(
                            if (registering) R.string.signin_create_body else R.string.signin_body,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalSemanticColors.current.muted,
                    )
                }

                // Said only where it is true and only where it is actionable: this phone is holding
                // a Lebenslauf that belongs to nobody, and creating an account is what keeps it.
                if (registering && state.adoptableProfileId != null) {
                    item {
                        Callout(
                            icon = BewerboIcons.Attention,
                            title = stringResource(R.string.signin_adopt_title),
                            body = stringResource(R.string.signin_adopt_body),
                            tone = PillTone.Attention,
                            modifier = Modifier.testTag("signin_adopt_note"),
                        )
                    }
                }

                item {
                    LabelledField(
                        label = stringResource(R.string.person_email),
                        value = email,
                        onValueChange = { email = it },
                        testTag = "signin_email",
                    )
                }
                item {
                    LabelledField(
                        label = stringResource(R.string.signin_password),
                        value = password,
                        onValueChange = { password = it },
                        testTag = "signin_password",
                        password = true,
                    )
                }
                if (registering) {
                    item {
                        Text(
                            stringResource(R.string.signin_password_rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalSemanticColors.current.muted,
                            modifier = Modifier.testTag("signin_password_rule"),
                        )
                    }
                }

                // The refusal, under the form and above the button that caused it.
                state.error?.let { error ->
                    item {
                        Text(
                            errorMessage(error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalSemanticColors.current.danger,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signin_error"),
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            // Signing in to ANOTHER account leaves the nameless Lebenslauf on this
                            // phone behind for good, so it is asked once. Creating an account keeps
                            // it and needs no question.
                            when {
                                registering -> viewModel.register(email, password)
                                state.adoptableProfileId != null -> leaving = true
                                else -> viewModel.signIn(email, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.s)
                            .testTag("signin_btn_submit"),
                        enabled = state.busy == null,
                    ) {
                        Text(
                            stringResource(
                                if (registering) {
                                    R.string.signin_create_action
                                } else {
                                    R.string.signin_action
                                },
                            ),
                        )
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            registering = !registering
                            // The message belonged to the form that is being left; carrying it over
                            // would have it explain a button the user is no longer looking at.
                            viewModel.dismissError()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_btn_mode"),
                    ) {
                        Text(
                            stringResource(
                                if (registering) {
                                    R.string.signin_have_account
                                } else {
                                    R.string.signin_create_title
                                },
                            ),
                        )
                    }
                }
            }

            LegalFooter { legalPage = it }
        }
    }

    if (leaving) {
        LeaveProfileDialog(
            onConfirm = {
                leaving = false
                viewModel.signIn(email, password)
            },
            onClose = { leaving = false },
        )
    }
}

/// The name of the product, and the one control that has to be reachable before the user can read
/// anything else on the screen.
@Composable
private fun Wordmark(state: AppState, viewModel: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        LanguageSelector(
            label = labelOf(UI_LANGUAGES, state.uiLanguage),
            icon = BewerboIcons.Globe,
            options = UI_LANGUAGES,
            testTagPrefix = "signin_language",
            onPick = viewModel::setUiLanguage,
        )
    }
}

/// The three pages a service has to carry, on the screen that stands in front of everything else.
/// One row, because they are a footer and not a menu.
@Composable
private fun LegalFooter(onOpen: (LegalPage) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s, vertical = Space.xs),
        horizontalArrangement = Arrangement.Center,
    ) {
        LegalPage.entries.forEach { page ->
            TextButton(
                onClick = { onOpen(page) },
                modifier = Modifier.testTag("signin_${page.tag}"),
            ) {
                Text(stringResource(page.label), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Asked once, before a sign-in that cannot be undone.
 *
 * The nameless Lebenslauf on this phone belongs to no account, so nothing carries it anywhere: the
 * moment this device moves onto an account that is not it, it is unreachable for good. The user is
 * told that, with the one thing that would have kept it named beside it.
 */
@Composable
private fun LeaveProfileDialog(onConfirm: () -> Unit, onClose: () -> Unit) {
    BewerboDialog(
        onDismissRequest = onClose,
        testTag = "signin_adopt_dialog",
        title = { Text(stringResource(R.string.signin_leave_title)) },
        text = {
            Text(
                stringResource(R.string.signin_leave_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("signin_leave_confirm")) {
                Text(stringResource(R.string.signin_leave_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("signin_leave_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
