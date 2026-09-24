package de.bewerbo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import de.bewerbo.app.R
import de.bewerbo.app.ui.icons.BewerboIcons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import de.bewerbo.app.ui.LocalUiLanguage
import de.bewerbo.app.ui.UiLanguageProvider
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.bewerbo.app.ui.theme.CardElevation
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

// ---------------------------------------------------------------------------------------------
// The components the design system is actually made of. "Professional" is mostly these: a card
// with one elevation step, a status pill that is not a chip, a meter that reads as a measurement.
// ---------------------------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalSemanticColors.current.muted,
        modifier = modifier,
    )
}

@Composable
fun BewerboCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(Space.m), content = content)
    }
}

/// A status pill. Deliberately not a chip: a chip is something you tap, a pill is something the
/// app is telling you. Keeping them apart is half of why an interface reads as considered.
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val colors = LocalSemanticColors.current
    val (fg, bg) = when (tone) {
        PillTone.Success -> colors.success to colors.successTint
        PillTone.Attention -> colors.attention to colors.attentionTint
        PillTone.Danger -> colors.danger to colors.dangerTint
        PillTone.Accent -> colors.accent to colors.accentTint
        PillTone.Neutral -> colors.muted to MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
        )
    }
}

enum class PillTone { Success, Attention, Danger, Accent, Neutral }

/// A labelled row with an icon, a title and an optional trailing pill. The building block most of
/// the app's lists are made of.
@Composable
fun IconRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    tone: PillTone = PillTone.Accent,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalSemanticColors.current
    val tint = when (tone) {
        PillTone.Success -> colors.success
        PillTone.Attention -> colors.attention
        PillTone.Danger -> colors.danger
        PillTone.Accent -> colors.accent
        PillTone.Neutral -> colors.muted
    }
    val tintBg = when (tone) {
        PillTone.Success -> colors.successTint
        PillTone.Attention -> colors.attentionTint
        PillTone.Danger -> colors.dangerTint
        PillTone.Accent -> colors.accentTint
        PillTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = (if (onClick != null) modifier.clickable(onClick = onClick) else modifier)
            .fillMaxWidth()
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(tintBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = Space.s),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            // Empty counts as absent: a caller that composes its detail hands over a String rather
            // than null, and an empty one drew a blank line the height of the text under the title.
            if (!detail.isNullOrEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        trailing?.invoke()
    }
}

/// A horizontal meter. Used wherever a number is a proportion — completeness, gaps explained,
/// requirements covered — so the same quantity always looks the same.
@Composable
fun Meter(
    label: String,
    value: String,
    fraction: Float,
    tone: PillTone = PillTone.Success,
    modifier: Modifier = Modifier,
    /// True when there is genuinely nothing to measure yet — "0 of 0". Drawn as an empty track,
    /// because a full green bar over "0 / 0" tells the user they have finished something they have
    /// not started.
    empty: Boolean = false,
) {
    val colors = LocalSemanticColors.current
    val bar = when (tone) {
        PillTone.Attention -> colors.attention
        PillTone.Danger -> colors.danger
        PillTone.Accent -> colors.accent
        PillTone.Neutral -> colors.muted
        PillTone.Success -> colors.success
    }
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .padding(top = Space.xs)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (empty) 0f else fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(bar),
            )
        }
    }
}

/// A segmented control. One choice out of a small fixed set, shown in full rather than hidden in
/// a dropdown — which is the point when the choice changes what the document says.
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String? = null,
    /// How an option is written on screen. The option itself stays the value the server knows, so
    /// a name the backend spells as one word — "OeffentlicherDienst" — is not shown that way.
    /// Composable, because every caller now resolves the label out of the string resources: the
    /// backend's vocabulary is German and the control it is drawn in is not.
    label: @Composable (String) -> String = { it },
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(3.dp)) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shape = MaterialTheme.shapes.extraSmall,
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (tagPrefix != null) {
                                Modifier.testTag("${tagPrefix}_${option.lowercase()}")
                            } else Modifier,
                        )
                        // Which option is chosen was said only in colour, weight and elevation, so
                        // a screen reader read the options as equal labels and could not tell the
                        // user which template, tone, employer type or document kind was selected.
                        // selectable() carries the state and the role, which is the one channel
                        // that does not depend on seeing the control.
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ),
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            LocalSemanticColors.current.muted
                        },
                        modifier = Modifier.padding(vertical = Space.s),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A labelled field with its value and a focus ring — the app's text input, rather than a bare
 * OutlinedTextField with a floating label.
 *
 * It also keeps ITSELF in view while it is being typed into, and that is not something the scroll
 * container above it does on its own. A scroll container brings a field into view at the moment
 * the field takes focus — one moment too early: the keyboard opens AFTERWARDS, the viewport
 * shrinks under a field that already has focus, and nothing asks a second time. In "Correct the
 * fields" that left the last field clipped to a sliver with the typed value invisible, and no
 * amount of typing brought it back; only a manual scroll did. Tapping a field while the keyboard
 * is already up failed the same way, because the sliver counts as in view.
 *
 * So the field asks again itself, whenever the keyboard comes or goes while it holds the focus.
 * The request waits one frame: the inset changes a frame before the layout that follows from it,
 * and asked any earlier it measures the viewport the keyboard has not shrunk yet and finds
 * nothing to do.
 */
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboardOpen = WindowInsets.isImeVisible

    LaunchedEffect(focused, keyboardOpen) {
        if (focused) {
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
    }

    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

/// A callout — the app explaining something, with an icon and no action.
@Composable
fun Callout(
    icon: ImageVector,
    title: String,
    body: String,
    tone: PillTone = PillTone.Accent,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSemanticColors.current
    val (fg, bg) = when (tone) {
        PillTone.Success -> colors.success to colors.successTint
        PillTone.Attention -> colors.attention to colors.attentionTint
        PillTone.Danger -> colors.danger to colors.dangerTint
        else -> colors.accent to colors.accentTint
    }

    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.m)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = Space.s)) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}


/// The endonym of [code] in [options] — the name a language calls itself, which is the only name
/// that is legible to somebody who cannot yet read the interface.
fun labelOf(options: List<Pair<String, String>>, code: String?): String =
    options.firstOrNull { it.first == code }?.second.orEmpty()

/**
 * A language button with the menu it opens.
 *
 * The app language and the input language are picked the same way and differ only in their list,
 * their label and what they write to — so they are one control used twice rather than two that
 * drift apart. It stood on the Profil screen while both of them did; the interface language moved
 * to the settings and the input language stayed behind, which is what brought it here.
 */
@Composable
fun LanguageSelector(
    label: String,
    icon: ImageVector,
    options: List<Pair<String, String>>,
    testTagPrefix: String,
    onPick: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { menuOpen = true },
            modifier = Modifier.testTag("${testTagPrefix}_selector"),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, modifier = Modifier.padding(start = Space.s))
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // A menu renders in its own window and inherits nothing from the root, so it carries
            // its own flag or a driver cannot see any of these items.
            modifier = Modifier.exposeTestTags(),
        ) {
            options.forEach { (code, endonym) ->
                DropdownMenuItem(
                    text = { Text(endonym) },
                    modifier = Modifier.testTag("${testTagPrefix}_option_$code"),
                    onClick = {
                        menuOpen = false
                        onPick(code)
                    },
                )
            }
        }
    }
}

/**
 * The head of a screen that was opened FROM another one, with the way back out of it.
 *
 * Every other destination is reached from the bottom bar or from the flow rail, and each writes its
 * own headline; the settings and the legal pages are the first that are not, so they are the first
 * that have to say how to leave. One component, so that the two look alike and the next screen of
 * the kind does not invent a third arrangement.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("screen_back")) {
            Icon(BewerboIcons.ChevronLeft, contentDescription = stringResource(R.string.settings_back))
        }
        Column(Modifier.padding(start = Space.xs)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSemanticColors.current.muted,
                )
            }
        }
    }
}

@Composable
fun Gutter(height: androidx.compose.ui.unit.Dp = Space.m) {
    Box(Modifier.height(height).width(1.dp))
}

/**
 * Marks a subtree as exposing its test tags as Android resource-ids.
 *
 * The root sets this once for the app, but a dialog and a dropdown menu render in their OWN
 * windows and inherit nothing from it — so without this on each of them, nothing inside has a
 * resource-id and no driver can reach it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.exposeTestTags(): Modifier =
    this.semantics { testTagsAsResourceId = true }

/**
 * An AlertDialog that keeps the app's own rules inside its own window.
 *
 * THREE things of ours do not cross that boundary, and each dialog used to have to remember them.
 * The testTagsAsResourceId flag is the one the app already knew about. The interface language is
 * the other, and it was missed: the dialog's window provides LocalContext and LocalConfiguration
 * afresh from the PHONE's locale, so every stringResource inside came back in the phone's language
 * — the title read "Correct the fields" and the buttons "Cancel"/"Save" while the app behind the
 * dialog was German. The language is re-applied around each slot, because the slots are what the
 * dialog composes in that window.
 *
 * The keyboard is the third. The NavHost's imePadding() belongs to the content behind the dialog
 * and stops at its own window, so the dialog was laid out for the whole screen and the keyboard
 * came up over its bottom: in "Correct the fields" the Save and Cancel buttons sat under the
 * keys, invisible to the user and unreachable for a run — a tap on posting_edit_confirm landed on
 * the keyboard and opened its settings instead. decorFitsSystemWindows = false is what makes the
 * dialog's window report the IME inset at all; imePadding() then lifts the dialog and its text
 * slot, which already scrolls, gives way.
 *
 * All three are applied here once so that the next dialog gets them without knowing any of this.
 */
@Composable
fun BewerboDialog(
    onDismissRequest: () -> Unit,
    testTag: String,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    val language = LocalUiLanguage.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.exposeTestTags().testTag(testTag).imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { UiLanguageProvider(language) { title() } },
        text = { UiLanguageProvider(language) { text() } },
        confirmButton = { UiLanguageProvider(language) { confirmButton() } },
        dismissButton = { UiLanguageProvider(language) { dismissButton() } },
    )
}

/**
 * One line of text that SHRINKS until it fits instead of breaking across two lines.
 *
 * A bottom-bar label gets the screen width divided by five and not a pixel more. At a large
 * system font scale — or in a locale whose word for a destination is simply long — "Application"
 * wrapped to "Applicati / on" and the whole bar lost its baseline. Shrinking a step at a time
 * keeps the word whole, which an ellipsis would not: "Applicati…" is not a navigation label.
 *
 * Below [minFontSize] the text stops shrinking and is ellipsised rather than made illegible, and
 * a style whose size is not given in sp is rendered unshrunk on one line — there is no sensible
 * ladder to walk for an em size.
 */
@Composable
fun FitOneLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 9.sp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val fitted =
            if (constraints.hasBoundedWidth) {
                fitToWidth(measurer, text, style, constraints.maxWidth, minFontSize)
            } else {
                style
            }
        Text(
            text = text,
            style = fitted,
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Each step is small enough that the shrink is not visible as a jump between two destinations. */
private const val ShrinkFactor = 0.94f

/** The ladder cannot run forever: 0.94^14 is under half size, well past [minFontSize] anywhere. */
private const val MaxShrinkSteps = 14

private fun fitToWidth(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
    minFontSize: TextUnit,
): TextStyle {
    if (style.fontSize.type != TextUnitType.Sp || minFontSize.type != TextUnitType.Sp) return style
    var candidate = style
    repeat(MaxShrinkSteps) {
        val measured = measurer.measure(
            text = text,
            style = candidate,
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(maxWidth = maxWidth),
        )
        if (!measured.hasVisualOverflow) return candidate
        val next = candidate.fontSize * ShrinkFactor
        if (next.value < minFontSize.value) return candidate.copy(fontSize = minFontSize)
        candidate = candidate.copy(fontSize = next)
    }
    return candidate
}
