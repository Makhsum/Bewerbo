package de.bewerbo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import de.bewerbo.app.ui.LocalUiLanguage
import de.bewerbo.app.ui.UiLanguageProvider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
///
/// The options share the width in equal columns, and the number of columns is what the longest
/// label needs, not the number of options: four document kinds in a phone-width card gave
/// "Arbeitszeugnis" and "Sprachnachweis" a quarter of the row each and broke them mid-word. What
/// does not fit on one line goes to the next, the way the Profil section rail and the export
/// chips wrap — a word a user has to recognise from a job advert is not shrunk and not cut.
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
        val labels = options.map { label(it) }
        val measurer = rememberTextMeasurer()
        // Measured bold: the chosen option is written bold, so a column sized for the others is
        // the column the selection then overflows.
        val widestStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        BoxWithConstraints(Modifier.padding(3.dp)) {
            // The width the longest label needs to stay one piece, and nothing on top of it: a
            // control whose options fit today is meant to render exactly as it did, and the five
            // application states leave barely a dozen pixels between them.
            val widest = labels.maxOf {
                measurer.measure(it, widestStyle, softWrap = false).size.width
            }
            val perLine = segmentsPerLine(constraints.maxWidth, widest, options.size)
            Column {
                options.indices.chunked(perLine).forEach { line ->
                    Row {
                        line.forEach { index ->
                            val option = options[index]
                            val isSelected = index == selectedIndex
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    Color.Transparent
                                },
                                shape = MaterialTheme.shapes.extraSmall,
                                shadowElevation = if (isSelected) 1.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (tagPrefix != null) {
                                            Modifier.testTag("${tagPrefix}_${option.lowercase()}")
                                        } else Modifier,
                                    )
                                    // Which option is chosen was said only in colour, weight and
                                    // elevation, so a screen reader read the options as equal
                                    // labels and could not tell the user which template, tone,
                                    // employer type or document kind was selected. selectable()
                                    // carries the state and the role, which is the one channel
                                    // that does not depend on seeing the control.
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(index) },
                                    ),
                            ) {
                                Text(
                                    labels[index],
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
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
                        // A last line that does not fill its columns keeps them empty rather than
                        // widening the options on it: every segment of the control stays the same
                        // width as every other.
                        repeat(perLine - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * How many segments of a [SegmentedControl] share one line: as many equal columns of [widest] as
 * [available] holds, and never more than [count].
 *
 * The lines are filled EVENLY, not greedily. Three of the four document kinds fit one
 * phone-width line, and a 3 + 1 control reads as a row with something stuck under it; the same
 * four read as 2 + 2. So the number of lines is settled first, and the columns follow from it.
 *
 * A label wider than the whole control still gets its own line — one column is the floor, and
 * there the text wraps as it always did rather than being clipped.
 */
internal fun segmentsPerLine(available: Int, widest: Int, count: Int): Int {
    if (count <= 0) return 1
    if (available <= 0 || widest <= 0) return count
    val fitting = (available / widest).coerceIn(1, count)
    val lines = (count + fitting - 1) / fitting
    return (count + lines - 1) / lines
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
    /// A field nobody reading over the shoulder gets to see. The door is the only screen in this
    /// app that has one, and it is a parameter here rather than a field of its own on that screen
    /// so that it carries the same label, the same shape and the same keyboard handling as every
    /// other input the user meets.
    password: Boolean = false,
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
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) {
            KeyboardOptions(keyboardType = KeyboardType.Password)
        } else {
            KeyboardOptions.Default
        },
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
 * [minFontSize] is where the shrinking stops, and it is a Dp rather than an sp ON PURPOSE: it is
 * the smallest text this screen may put in front of a reader, and that size does not grow because
 * the reader turned the system font up. Written as an sp it did — at the accessibility maximum the
 * floor stood at twice the pixels it was meant to be, the ladder reached it while the word was
 * still wider than its place, and "Unterlagen" arrived as "Unterla…" on the bottom bar of exactly
 * the reader who had raised the font in order to read.
 *
 * Below [minFontSize] the text stops shrinking and is ellipsised rather than made illegible, and
 * a style whose size is not given in sp is rendered unshrunk on one line — there is no sensible
 * ladder to walk for an em size.
 *
 * [peers] is the row this text is one of, and naming it is what keeps a row LETTERED IN ONE SIZE.
 * Shrinking each word only as far as its own place needs is right for a label that stands alone and
 * wrong for four beside each other: at a raised font size "Profil" stayed at full size next to a
 * visibly smaller "Übersicht", and the bottom bar read as four separate controls instead of one
 * row. The size handed back is the one that fits the longest of [peers] too — the same decision
 * [SegmentedControl] takes when it sizes every column from its widest label.
 *
 * Inside a [FitOneLineTextGroup] the row also agrees on the result, which is what makes it exact;
 * see there for the one pixel that makes the agreement necessary.
 */
@Composable
fun FitOneLineText(
    text: String,
    modifier: Modifier = Modifier,
    /// The tag belongs on the LINE, which is why it is a parameter here rather than something the
    /// caller puts into [modifier]: the modifier lands on the box that MEASURES the place, and the
    /// word is drawn by a Text inside it. Tagged on the box, the node a driver reached answered with
    /// the empty string and the word could only be got at by walking the tree by hand — a test that
    /// asserts "this tab is named Profil" failed against a screen that was perfectly right. Tagged
    /// here, the node that carries the tag is the node that has the word.
    testTag: String? = null,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: Dp = 9.dp,
    peers: List<String> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    // The floor in the sp of the moment: 9.dp is 9.sp at the default font scale and 4.5.sp at the
    // accessibility maximum — the same pixels on the screen either way, which is the whole point.
    val floor = with(LocalDensity.current) { minFontSize.toSp() }
    val group = LocalFitOneLineTextSizes.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val own =
            if (constraints.hasBoundedWidth) {
                fitToWidth(measurer, listOf(text) + peers, style, constraints.maxWidth, floor)
            } else {
                style
            }
        // In a group the size this place allows is only a vote: the row is lettered in the
        // smallest of the four, and every member says what its own place allowed.
        if (group != null) {
            DisposableEffect(group, text, own.fontSize) {
                if (own.fontSize.type == TextUnitType.Sp) group.report(text, own.fontSize)
                onDispose { group.forget(text) }
            }
        }
        val fitted = group?.smallest?.let { own.copy(fontSize = it) } ?: own
        Text(
            text = text,
            style = fitted,
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        )
    }
}

/**
 * A row of [FitOneLineText]s that is lettered in ONE size: the smallest any of them arrived at.
 *
 * Handing every text the other words of its row — [FitOneLineText]'s peers — letters the row in one
 * size only while the places are exactly as wide as each other, and a row of four is not quite: the
 * pixels left over when a Row divides the screen into four go to one of the items, and ONE pixel is
 * enough to let that item's word live one step of the ladder longer than the other three. That is
 * measured, not feared: at font scale 1.5 the bottom bar gave its first item 211 px and the other
 * three 210, and "Übersicht" was lettered one step above "Assistent", "Profil" and "Unterlagen".
 *
 * So the members measure their own place as before and then agree: each says what its place allowed
 * and every one of them renders at the smallest of those. A member that is measured again — another
 * font scale, another interface language — corrects its own answer, and one that leaves the screen
 * takes it with it, so the row can grow back as well as shrink.
 */
@Composable
fun FitOneLineTextGroup(content: @Composable () -> Unit) {
    val group = remember { FitOneLineTextSizes() }
    CompositionLocalProvider(LocalFitOneLineTextSizes provides group, content = content)
}

/// What the members of a [FitOneLineTextGroup] answered, one entry per text.
private class FitOneLineTextSizes {
    private val sizes = mutableStateMapOf<String, TextUnit>()

    /// The size the row is lettered in. Null until the first member has measured — then every
    /// member renders at it, which is one recomposition after the row first appears.
    val smallest: TextUnit?
        get() = sizes.values.minByOrNull { it.value }

    fun report(text: String, size: TextUnit) {
        sizes[text] = size
    }

    fun forget(text: String) {
        sizes.remove(text)
    }
}

/// Null outside a [FitOneLineTextGroup]: a text that stands alone is lettered in the size its own
/// place allows, which is what every caller but the bottom bar wants.
private val LocalFitOneLineTextSizes = compositionLocalOf<FitOneLineTextSizes?> { null }

/** Each step is small enough that the shrink is not visible as a jump between two destinations. */
private const val ShrinkFactor = 0.94f

/**
 * The ladder stops at the floor, so its length is only the guard against a runaway — but it has to
 * be long enough to REACH that floor from the largest style the system font scale hands it.
 * Fourteen steps ran out at 0.42 of the starting size, which at the accessibility maximum is still
 * bigger than the same text at the default one; 0.94^40 is a thousandth of it.
 */
private const val MaxShrinkSteps = 40

/**
 * The step of the ladder is coarse enough to be walked down in a handful of measurements and too
 * coarse to stop on: a word that overflows its place by a hair gives up the whole 6 %. So the step
 * that was given up is walked back up in [RefineSteps] of these, which leaves at most a hundredth
 * of the size on the table — 0.99^5 is 0.951 of a step of 0.94.
 */
private const val RefineFactor = 0.99f

/// Five of them cover a step of the ladder but for a hundredth, and five measurements is what the
/// refinement costs: the walk down has found the step already, and this only searches inside it.
private const val RefineSteps = 5

/// The largest size at which EVERY one of [texts] fits [maxWidth] on one line, and never more than
/// the size that was asked for. A single text is the ordinary case; several are a row that has to
/// be lettered in one size, and there the walk stops at the step the longest of them can still
/// live on. WHICH sizes are walked at all is [fittedFontSize]'s decision, not this one's.
private fun fitToWidth(
    measurer: TextMeasurer,
    texts: List<String>,
    style: TextStyle,
    maxWidth: Int,
    minFontSize: TextUnit,
): TextStyle {
    if (style.fontSize.type != TextUnitType.Sp || minFontSize.type != TextUnitType.Sp) return style
    val size = fittedFontSize(style.fontSize.value, minFontSize.value) { candidate ->
        val at = style.atFontSize(candidate.sp)
        val overflows = texts.any { text ->
            measurer.measure(
                text = text,
                style = at,
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = maxWidth),
            ).hasVisualOverflow
        }
        !overflows
    }
    return style.atFontSize(size.sp)
}

/**
 * The size one line of shrink-to-fit text is lettered in: the size that was asked for
 * ([requested]), or the largest size its place has room for when that is smaller, and never below
 * [floor]. [fits] answers whether a size still keeps every word of the row whole. All three are sp
 * AT THE FONT SCALE OF THE MOMENT, which is what makes [floor] — a Dp — the same pixels at every
 * one of them.
 *
 * RAISING THE SYSTEM FONT MUST NEVER LETTER A WORD SMALLER, and the sizes that are tried at all
 * are what decides it. Walking down from [requested] in steps OF [requested] put the rungs of the
 * ladder at a different place on the screen at every font scale, and the same word in the same
 * place then came out on a rung up to one step — 6 % — lower at the accessibility maximum than at
 * the default setting: a reader who turned the font up in order to read got less to read.
 *
 * So the rungs stand at FIXED places instead: [floor], and every step above it counted from there.
 * Which rung a word still fits on is then a property of the word and its place alone and does not
 * move when the font scale does. And the size asked for is a CEILING over that rung rather than
 * the rung the walk starts from, so a word with room to spare is still lettered in exactly the
 * size the system asked for and grows with it, while one without room stands still.
 */
internal fun fittedFontSize(requested: Float, floor: Float, fits: (Float) -> Boolean): Float {
    // Up to the rung above the size that was asked for. Arithmetic, not measuring: nothing is
    // measured above the size the text is going to be lettered in at most.
    var rung = floor
    var steps = 0
    while (rung < requested && steps < MaxShrinkSteps) {
        rung /= ShrinkFactor
        steps++
    }
    // And down again, to the highest rung every word of the row still fits on. The floor is where
    // the walk stops; below it the text is ellipsised rather than made illegible.
    while (steps > 0 && !fits(rung)) {
        rung *= ShrinkFactor
        steps--
    }
    // And back up inside the step that was just given up, in finer ones. A step of the ladder is
    // 6 % and a word that needed a hundredth of it less lost the whole step: the rail settled 2.6 %
    // below what its third of the screen held. These rungs stand at fixed places too — they are
    // counted from the one below them, which is counted from the floor.
    var refinements = RefineSteps
    while (refinements > 0) {
        val finer = rung / RefineFactor
        if (finer > requested || !fits(finer)) break
        rung = finer
        refinements--
    }
    return minOf(requested, maxOf(rung, floor))
}

/// [this] lettered in [size], with the tracking taken down together with the glyphs. A
/// letterSpacing is written in sp and so grows with the system font, and copying a smaller
/// fontSize over the style leaves it where it was: the same word then needs more width per pixel
/// of glyph the further the font scale is turned up, and the ladder answers with a lower rung at
/// 2.0 than at 1.5 for a place that has not changed size at all.
private fun TextStyle.atFontSize(size: TextUnit): TextStyle =
    if (letterSpacing.type == TextUnitType.Sp && fontSize.value > 0f) {
        copy(fontSize = size, letterSpacing = letterSpacing * (size.value / fontSize.value))
    } else {
        copy(fontSize = size)
    }
