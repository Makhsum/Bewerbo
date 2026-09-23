package de.bewerbo.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/// The languages the INTERFACE is offered in — one entry per values-<tag>/strings.xml that
/// actually exists, code to endonym the way INPUT_LANGUAGES on the Profil screen does it.
///
/// A language with no resources is deliberately NOT here. The input-language list has one —
/// Türkçe — because that list is about what the user types; offering it here would promise a
/// translation and hand back English, which is the very complaint this control answers.
/// UiLanguagesTest fails the build if a tag on this list loses its strings.xml.
val UI_LANGUAGES = listOf(
    "ru" to "Русский",
    "uk" to "Українська",
    "en" to "English",
    "de" to "Deutsch",
)

/// The interface language the app settles on before the user has said anything: the phone's, when
/// it is one we have, and English otherwise. This is what the app did at every start until now —
/// the difference is that it is a first guess written down once, not a verdict.
fun defaultUiLanguage(): String =
    UI_LANGUAGES.firstOrNull { it.first == Locale.getDefault().language }?.first ?: "en"

/**
 * Draws everything below it in [tag], whatever the phone is set to.
 *
 * The phone's locale used to decide this alone, so a phone bought in Germany showed an English
 * app to someone who had come here for a German application. Overriding the configuration for the
 * composition — rather than restarting the activity with a new locale — is what lets the choice
 * take effect on the screen the user made it on, which is the only place they can see that it
 * worked.
 *
 * [LocalContext] goes along with [LocalConfiguration] because stringResource reads the former for
 * the resources and the latter to know it has to look again; providing one without the other
 * leaves half the screen in the old language until something else recomposes it.
 */
@Composable
fun UiLanguageProvider(tag: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val localised = remember(tag, configuration) {
        Configuration(configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    }

    CompositionLocalProvider(
        LocalConfiguration provides localised,
        LocalContext provides context.createConfigurationContext(localised),
        content = content,
    )
}
