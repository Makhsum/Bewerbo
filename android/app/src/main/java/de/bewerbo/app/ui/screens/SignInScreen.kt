package de.bewerbo.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
    var resetting by remember { mutableStateOf(false) }

    val page = legalPage
    if (page != null) {
        // Read in place of the door rather than on top of it: there is no NavHost above the
        // Scaffold to push onto, and the arrow in the page's own header is the way back.
        //
        // The inset is applied HERE for the same reason. Inside the app the Scaffold hands every
        // screen its padding, and these pages have never had to ask; opened from the door there is
        // no Scaffold above them, and the header ran under the status bar.
        //
        // And the back gesture for the same reason again: the page is state rather than a NavHost
        // destination, so nothing below answered the system back and it fell through to the
        // Activity — reading the AGB from the door and pressing back LEFT Bewerbo. The arrow in
        // the header and the gesture have to mean one thing.
        BackHandler { legalPage = null }

        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            LegalScreen(page, state, viewModel) { legalPage = null }
        }
        return
    }

    // In place of the door for the same reason the legal pages are: there is no NavHost above the
    // Scaffold to push onto. The address already typed goes with it, so a user who tried their
    // password twice does not type it a third time.
    if (resetting) {
        ResetPasswordScreen(state, viewModel, email) {
            resetting = false
            viewModel.forgetPasswordReset()
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

                // Only under the sign-in. Somebody filling in a brand new account has no password
                // to have forgotten, and the offer there would read as a warning.
                if (!registering) {
                    item {
                        TextButton(
                            onClick = {
                                resetting = true
                                viewModel.dismissError()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signin_btn_forgot"),
                        ) {
                            Text(stringResource(R.string.signin_forgot))
                        }
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

/**
 * The way back in when the password is gone — the door in two steps, on the door's own screen.
 *
 * The step is read off [AppState.resetRequestedFor] and not off anything the server said, because
 * the server deliberately says nothing: forgot-password answers the same way for an address it has
 * never seen. So the second step is reached whether or not there was an account, and the Callout
 * says "if there is an account for this address" rather than "we have sent you a code". That is not
 * hedging — it is the one sentence that is true either way, and saying more would make this screen
 * the fastest way to find out who has an account here.
 *
 * The wordmark row comes along for a reason beyond looking like the door: the mail is written in
 * the interface language, so the language button has to be reachable on the screen that asks for
 * it. The legal footer does not — it is one tap away on the door behind this.
 */
@Composable
private fun ResetPasswordScreen(
    state: AppState,
    viewModel: AppViewModel,
    initialEmail: String,
    onClose: () -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val sentTo = state.resetRequestedFor

    // Same reason as the legal pages: nothing below this answers the system back, so without it the
    // gesture falls through to the Activity and leaves Bewerbo.
    BackHandler { onClose() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .testTag("reset_screen"),
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
                        stringResource(R.string.reset_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("reset_headline"),
                    )
                }

                if (sentTo == null) {
                    item {
                        Text(
                            stringResource(R.string.reset_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalSemanticColors.current.muted,
                        )
                    }
                    item {
                        LabelledField(
                            label = stringResource(R.string.person_email),
                            value = email,
                            onValueChange = { email = it },
                            testTag = "reset_email",
                        )
                    }
                } else {
                    item {
                        Callout(
                            icon = BewerboIcons.Mail,
                            title = stringResource(R.string.reset_sent_title),
                            body = stringResource(R.string.reset_sent_body, sentTo),
                            modifier = Modifier.testTag("reset_sent_note"),
                        )
                    }
                    item {
                        LabelledField(
                            label = stringResource(R.string.reset_code),
                            value = code,
                            onValueChange = { code = it },
                            testTag = "reset_code",
                        )
                    }
                    item {
                        LabelledField(
                            label = stringResource(R.string.reset_new_password),
                            value = password,
                            onValueChange = { password = it },
                            testTag = "reset_password",
                            password = true,
                        )
                    }
                    item {
                        // The door's own rule, word for word: it is the same password and the same
                        // eight characters, and a second sentence for it would be a second rule.
                        Text(
                            stringResource(R.string.signin_password_rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalSemanticColors.current.muted,
                            modifier = Modifier.testTag("reset_password_rule"),
                        )
                    }
                }

                state.error?.let { error ->
                    item {
                        Text(
                            errorMessage(error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalSemanticColors.current.danger,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_error"),
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (sentTo == null) {
                                viewModel.requestPasswordReset(email)
                            } else {
                                viewModel.resetPassword(code, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.s)
                            .testTag("reset_btn_submit"),
                        enabled = state.busy == null,
                    ) {
                        Text(
                            stringResource(
                                if (sentTo == null) {
                                    R.string.reset_action_request
                                } else {
                                    R.string.reset_action
                                },
                            ),
                        )
                    }
                }

                // The way out of a code that never arrived, or one that is older than its half
                // hour. It goes back to the address rather than straight out to the door, because
                // asking again is what the refusal tells the user to do.
                if (sentTo != null) {
                    item {
                        TextButton(
                            onClick = viewModel::forgetPasswordReset,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_btn_again"),
                        ) {
                            Text(stringResource(R.string.reset_again))
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_btn_back"),
                    ) {
                        Text(stringResource(R.string.reset_back))
                    }
                }
            }
        }
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
/// A footer and not a menu, so it stays centred and on as few lines as the language allows.
///
/// It WRAPS, for the reason the Profil screen's section rail does: the three labels fit one
/// phone-width row in English and in none of the other languages the picker offers. The Russian and
/// Ukrainian labels for the terms and the privacy notice are twice the length of the English ones,
/// and a fixed row answered that by squeezing the third one against the right edge until
/// "Impressum" broke into a stack of syllables - 96 px wide in Russian against 162 in English. The
/// Impressum is the page German law requires of a published service and this screen is where a
/// signed-out user has to find it, so it is the last label that may be made unreadable to fit the
/// others in.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LegalFooter(onOpen: (LegalPage) -> Unit) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s, vertical = Space.xs)
            .testTag("signin_legal_footer"),
        horizontalArrangement = Arrangement.Center,
    ) {
        LegalPage.entries.forEach { page ->
            TextButton(
                onClick = { onOpen(page) },
                modifier = Modifier.testTag("signin_${page.tag}"),
                // The default 24 dp each side spends a third of the row on empty space, which on
                // this footer buys nothing: the three labels stand far enough apart without it, and
                // in German the saving is what keeps them on one line at all.
                contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.xs),
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
