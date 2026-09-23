package de.bewerbo.app.ui

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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

/// The language to draw in for a [stored] tag that could be anything: the one it names while we
/// still offer it, and the first guess otherwise. A tag we no longer have resources for would show
/// as a blank language on the Profil button over an interface that had quietly fallen back to
/// English, and allowBackup means a preferences file can arrive from a build that had it.
fun uiLanguageOrDefault(stored: String?): String =
    UI_LANGUAGES.firstOrNull { it.first == stored }?.first ?: defaultUiLanguage()

/// The tag the interface is currently drawn in.
///
/// A dialog and a dropdown menu are composed in windows of their own, and those windows provide
/// LocalContext and LocalConfiguration AFRESH from the phone — so the language has to be applied
/// again inside them. This local is not re-provided at a window boundary, which is how that code
/// learns which language to re-apply without every screen threading the tag down to it. The same
/// reason [Modifier.exposeTestTags] exists, for the same boundary.
val LocalUiLanguage = compositionLocalOf { "en" }

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

    // Remembered, and that matters: createConfigurationContext() allocates a Context, and
    // LocalContext is a STATIC CompositionLocal — handing it an instance it has not seen before
    // recomposes the WHOLE subtree under it, which here is every screen in the app. Built outside
    // a remember it was a new instance on every recomposition, so each state update the view model
    // emitted redrew the lot.
    val localised = remember(tag, context, configuration) {
        context.createConfigurationContext(
            Configuration(configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
        )
    }

    // The registry a rememberLauncherForActivityResult() needs, read HERE — where LocalContext is
    // still the activity's — and handed on explicitly below.
    //
    // It has no composition local of its own to fall back on: it finds its owner by walking up the
    // ContextWrapper chain from LocalContext, and [localised] is not on that chain at all, because
    // createConfigurationContext() returns a fresh Context rather than a wrapper around this one.
    // So without this the first screen to open the photo picker died on
    // "No ActivityResultRegistryOwner was provided". Same root as the FLAG_ACTIVITY_NEW_TASK every
    // startActivity() in this app carries: under this provider, LocalContext is never the activity.
    val registryOwner = LocalActivityResultRegistryOwner.current

    CompositionLocalProvider(
        values = buildList {
            add(LocalUiLanguage provides tag)
            // Taken off the context rather than kept beside it, so the two can never disagree.
            add(LocalConfiguration provides localised.resources.configuration)
            add(LocalContext provides localised)
            registryOwner?.let { add(LocalActivityResultRegistryOwner provides it) }
        }.toTypedArray(),
        content = content,
    )
}
