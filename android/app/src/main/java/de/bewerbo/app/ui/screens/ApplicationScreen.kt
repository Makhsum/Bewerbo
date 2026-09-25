package de.bewerbo.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.bewerbo.app.Destination
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.EmailDraft
import de.bewerbo.app.data.isOpeningApplication
import de.bewerbo.app.ui.TermNote
import de.bewerbo.app.ui.germanTerm
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.DinOverlay
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.SegmentedControl
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.components.applicationStatusLabel
import de.bewerbo.app.ui.components.atsFailedLabels
import de.bewerbo.app.ui.components.atsFindingDetail
import de.bewerbo.app.ui.components.atsFindingLabel
import de.bewerbo.app.ui.components.letterBlockerItems
import de.bewerbo.app.ui.components.reviewCheckDetail
import de.bewerbo.app.ui.components.reviewCheckTitle
import de.bewerbo.app.ui.components.reviewFailedLabels
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/// The five states an application passes through, in that order. They keep the backend's spelling
/// because that is the value the status endpoint takes.
private val STATUSES = listOf("Entwurf", "Versendet", "Wartend", "Einladung", "Absage")

/// The Lebenslauf layouts, in the order the SegmentedControl shows them. Like the statuses, these
/// are backend values, not words for the user — `templateLabel` says how they are written.
private val TEMPLATES = listOf("Klassisch", "Modern", "Fachlich")

/// The place a finding leads to, read off the bottom bar's own list so the route and the word for
/// it cannot drift apart. Null for a route that is not a place — the row is then offered as a way
/// nowhere, which is what a finding with no target means.
private fun placeOf(route: String): Destination? =
    Destination.entries.firstOrNull { it.route == route }

/**
 * Bewerbung — the Mappe page by page, the DIN inspector over it, the Prüfung, and the export.
 *
 * The preview is the exported PDF rasterised, not a redrawing of it. What the user is checking here
 * is whether this reads as a German business letter, and that question can only be answered against
 * the file that will actually be sent — every page of it, in the type the renderer set.
 *
 * [navigate] leads out of the flow to a place: a Maschinenlesbarkeit check that could not be run
 * because a profile field is empty says so and takes the user to where that field is filled in.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ApplicationScreen(state: AppState, viewModel: AppViewModel, navigate: (String) -> Unit) {
    val colors = LocalSemanticColors.current
    val application = state.application

    val documents = state.profile?.documents.orEmpty()
    // The documents whose scan this installation actually holds. A copy can only be appended where
    // there is one, and the two counts below are what the export card states rather than implies.
    val withCopy = documents.filter { it.scan != null }
    val withoutCopy = documents.filter { it.scan == null }

    // The Anlagenverzeichnis exists as a page only when there is something for it to list, and the
    // copies only when one of those documents has a file behind it — offering either otherwise
    // promises a page the saved PDF does not contain.
    val availableParts = buildList {
        add("anschreiben")
        add("lebenslauf")
        if (documents.isNotEmpty()) add("anlagenverzeichnis")
        if (withCopy.isNotEmpty()) add("scans")
    }
    var selectedParts by remember(availableParts) { mutableStateOf(availableParts.toSet()) }

    // Which page the preview is showing. Kept here rather than in the item, because a LazyColumn
    // item that scrolls out of view leaves composition and would come back on page 1.
    var previewPage by remember { mutableStateOf(0) }
    val previewPages = state.previewPages
    val currentPage = previewPage.coerceIn(0, (previewPages.size - 1).coerceAtLeast(0))

    // The preview shows the file the current selection produces, so it is rendered again whenever
    // that selection changes — and when the screen opens on a different application.
    LaunchedEffect(application?.id, selectedParts) {
        if (application != null && selectedParts.isNotEmpty()) {
            viewModel.refreshPreview(selectedParts.joinToString(","))
        }
    }

    // The mail app is started from here and not from the view model: an Intent needs a context that
    // can start an activity, and the view model only holds the Application.
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.application_send_email)
    LaunchedEffect(state.pendingEmail) {
        state.pendingEmail?.let { draft ->
            context.startActivity(emailChooser(context, draft, chooserTitle))
            viewModel.emailHandled()
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("application_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column {
                Text(stringResource(R.string.nav_application), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.application_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        if (application == null) {
            // Two different things leave nothing here: the application the user tapped on the
            // Übersicht has not arrived yet, or they have none. Only the second is an empty state.
            // Drawn for both, it said "Noch kein Anschreiben — führen Sie zuerst den Abgleich durch"
            // over a letter that existed and a step that was finished, for as long as three calls
            // take. The wait says that it is a wait, the way the Übersicht's own does.
            item {
                if (state.isOpeningApplication) {
                    Callout(
                        icon = BewerboIcons.Refresh,
                        title = stringResource(R.string.application_loading_title),
                        body = stringResource(R.string.application_loading_body),
                        modifier = Modifier.testTag("application_loading"),
                    )
                } else {
                    Callout(
                        icon = BewerboIcons.Document,
                        title = stringResource(R.string.application_none_title),
                        body = stringResource(R.string.application_none_body),
                        modifier = Modifier.testTag("application_none"),
                    )
                }
            }
            return@LazyColumn
        }

        // Which writer produced this letter — ABOVE the pages, because it qualifies them.
        //
        // It stood at the foot of the screen in small grey type, where it was read after the letter
        // and after the judgement the letter had already produced. A reader who learns only then
        // that no model was involved has been told nothing they can still act on. So it is a
        // Callout like the other statements this screen makes about the Mappe, and it sits where
        // the thing it qualifies begins.
        item {
            Callout(
                icon = BewerboIcons.Rewrite,
                title = stringResource(R.string.application_source_title),
                body = stringResource(
                    if (application.source == "model") R.string.application_source_model
                    else R.string.application_source_rules,
                ),
                modifier = Modifier.testTag("application_source"),
            )
        }

        // The whole Mappe as PAGES, not just its first one.
        //
        // These are the real exported file rasterised, so the page shown is A4-proportioned with
        // the Anschriftenfeld and the date exactly where the renderer put them — which is what lets
        // the inspector overlay, drawn at fractions of the sheet, mean anything.
        //
        // When there are no pages to show, this place says SO and offers the way back rather than
        // showing pages — the same shape the Übersicht uses when the start itself could not reach
        // the server, and for the same reason: the snackbar that said so is long gone. Everything
        // below stays reachable; it is the preview that failed, not the application.
        //
        // Two things can leave the preview without pages and they are not the same statement: the
        // Mappe never arrived, or it arrived and this device could not draw it. The second one used
        // to say nothing at all and left the screen on "the pages are being made" for good. The way
        // back is the one button either way — asking again is all the user can do about both.
        val previewFailure = state.previewFailure
        if (previewFailure != null) {
            val notDrawn = previewFailure == AppViewModel.PREVIEW_NOT_DRAWN
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Callout(
                        icon = BewerboIcons.Attention,
                        title = stringResource(
                            if (notDrawn) R.string.application_preview_undrawn_title
                            else R.string.application_preview_failed_title,
                        ),
                        body = stringResource(
                            if (notDrawn) R.string.application_preview_undrawn_body
                            else R.string.application_preview_failed_body,
                        ),
                        tone = PillTone.Attention,
                        modifier = Modifier.testTag(
                            if (notDrawn) "application_preview_undrawn" else "application_preview_failed",
                        ),
                    )
                    OutlinedButton(
                        onClick = { viewModel.refreshPreview(selectedParts.joinToString(",")) },
                        enabled = state.busy == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("application_btn_preview_retry"),
                    ) {
                        Icon(BewerboIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.application_preview_retry),
                            modifier = Modifier.padding(start = Space.s),
                        )
                    }
                }
            }
        } else item {
            Box(Modifier.testTag("application_preview_pager")) {
                BewerboCard {
                    if (previewPages.isEmpty()) {
                        // Not an error: the file is being rendered. It says so rather than showing
                        // an empty sheet, which would read as an application with nothing in it.
                        Text(
                            stringResource(R.string.application_preview_pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            modifier = Modifier.testTag("application_preview_pending"),
                        )
                    } else {
                        Text(
                            stringResource(
                                R.string.application_preview_page,
                                currentPage + 1, previewPages.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                            modifier = Modifier.testTag("application_preview_caption"),
                        )
                        Box(
                            Modifier
                                .padding(top = Space.s)
                                .fillMaxWidth()
                                .aspectRatio(210f / 297f)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("application_page_${currentPage + 1}"),
                        ) {
                            Image(
                                bitmap = previewPages[currentPage].asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )

                            // Only over page 1 of the Anschreiben: DIN 5008 states where the
                            // Anschriftenfeld and the Faltmarken of a LETTER sit, and drawing those
                            // boxes over a page of the Lebenslauf would assert something untrue.
                            if (state.showDinGrid && currentPage == 0 && "anschreiben" in selectedParts) {
                                // Same measurements as Rendering/DocumentTheme.cs, same page.
                                DinOverlay(
                                    Modifier
                                        .fillMaxSize()
                                        .testTag("application_din_overlay"),
                                )
                            }
                        }

                        // The thumbnail strip — how a page other than the current one is reached.
                        Row(
                            Modifier
                                .padding(top = Space.s)
                                .horizontalScroll(rememberScrollState())
                                .testTag("application_page_thumbs"),
                            horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            previewPages.forEachIndexed { index, page ->
                                PageThumbnail(
                                    page = page,
                                    number = index + 1,
                                    selected = index == currentPage,
                                    onClick = { previewPage = index },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(
                    onClick = { viewModel.toggleDinGrid() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("application_toggle_din_grid"),
                ) {
                    Icon(BewerboIcons.DinGrid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(
                            if (state.showDinGrid) R.string.application_din_hide
                            else R.string.application_din_show,
                        ),
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.regenerateLetter(application.tone) },
                    modifier = Modifier.testTag("application_btn_regenerate_letter"),
                ) {
                    Icon(
                        BewerboIcons.Refresh,
                        contentDescription = stringResource(R.string.application_regenerate),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // DIN 5008 is named on the button above, and this is the screen it first appears on.
        item { TermNote(germanTerm("din_5008")) }

        // What happened to this application. The Übersicht already colours these four states —
        // Einladung green, Absage red — and until this control existed none of them could be
        // reached: setStatus had no caller anywhere, so every application stayed "Entwurf" and
        // the list of active applications could not tell the user anything they did not know.
        item {
            BewerboCard(Modifier.testTag("application_status_card")) {
                SectionLabel(stringResource(R.string.application_status))
                Box(Modifier.padding(top = Space.s)) {
                    SegmentedControl(
                        options = STATUSES,
                        selectedIndex = STATUSES.indexOf(application.status).coerceAtLeast(0),
                        onSelect = { viewModel.setStatus(STATUSES[it]) },
                        label = { stringResource(applicationStatusLabel(it)) },
                        modifier = Modifier.testTag("application_status_selector"),
                        tagPrefix = "application_status",
                    )
                }
            }
        }

        // Prüfung — every check rule-based, so a failing one names what failed.
        state.review?.let { review ->
            // A check that rests on a proportion says nothing about a letter of three sentences, so
            // the backend leaves it out rather than reporting noise. "No hints" over a check that
            // never ran would claim it passed, which is why the pill has a third state — the same
            // one the Maschinenlesbarkeit summary below uses for a check it could not carry out.
            //
            // A FAILED check is the fourth, and it outranks the other three: over a red
            // "Anschreiben, nicht Motivationsschreiben" the pill read "1 hint", which is the
            // summary contradicting the row underneath it. Same order and same tones as the
            // Maschinenlesbarkeit summary below — failure first, then what could not be judged.
            val notChecked = review.notChecked.isNotEmpty()
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(stringResource(R.string.application_review))
                    StatusPill(
                        when {
                            !review.passed -> stringResource(R.string.application_review_failed)
                            // A count next to a number needs a plural rule. This one read
                            // "1 HINTS", which is the sort of detail plurals.xml was written to
                            // prevent and then did not cover.
                            review.hintCount > 0 -> pluralStringResource(
                                R.plurals.application_review_hint_count,
                                review.hintCount, review.hintCount,
                            )
                            notChecked -> stringResource(R.string.application_review_unchecked)
                            else -> stringResource(R.string.application_review_clean)
                        },
                        when {
                            !review.passed -> PillTone.Danger
                            review.hintCount > 0 || notChecked -> PillTone.Attention
                            else -> PillTone.Success
                        },
                        Modifier.testTag("application_review_summary"),
                    )
                }
            }
            review.checks.forEachIndexed { index, check ->
                item {
                    BewerboCard(Modifier.testTag("application_check_$index")) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                when (check.verdict) {
                                    "ok" -> BewerboIcons.Covered
                                    "hinweis" -> BewerboIcons.Attention
                                    else -> BewerboIcons.NotClaimed
                                },
                                contentDescription = null,
                                tint = when (check.verdict) {
                                    "ok" -> colors.success
                                    "hinweis" -> colors.attention
                                    else -> colors.danger
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Column(Modifier.padding(start = Space.s)) {
                                Text(reviewCheckTitle(check), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    reviewCheckDetail(check),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                                if (check.key == "floskeln" && check.items.isNotEmpty()) {
                                    Text(
                                        check.items.joinToString("  ·  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (check.verdict == "ok") colors.muted else colors.danger,
                                        textDecoration = if (check.verdict == "ok") {
                                            androidx.compose.ui.text.style.TextDecoration.LineThrough
                                        } else null,
                                        modifier = Modifier
                                            .padding(top = Space.xs)
                                            .testTag("application_check_items_$index"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Maschinenlesbarkeit — the produced PDF read back as text.
        //
        // Three of the five checks read a profile field and look for it in the file. With that
        // field empty there is nothing to look for, so the backend answers "ungeprueft" instead of
        // "fehler": red belongs to a fault of the document, and three red marks over an empty
        // profile taught the user that red means nothing. Such a row names the field and leads to
        // the place it is filled in on, the way a next step on the Übersicht does.
        state.ats?.let { ats ->
            val unchecked = ats.findings.any { it.verdict == "ungeprueft" }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(stringResource(R.string.application_ats))
                    StatusPill(
                        stringResource(
                            when {
                                !ats.passed -> R.string.application_ats_failed
                                unchecked -> R.string.application_ats_unchecked
                                else -> R.string.application_ats_passed
                            },
                        ),
                        when {
                            !ats.passed -> PillTone.Danger
                            unchecked -> PillTone.Attention
                            else -> PillTone.Success
                        },
                        Modifier.testTag("application_ats_summary"),
                    )
                }
            }
            item {
                BewerboCard(Modifier.testTag("application_ats_card")) {
                    ats.findings.forEachIndexed { index, finding ->
                        val place = if (finding.verdict == "ungeprueft") placeOf(finding.target) else null
                        val row = Modifier
                            .fillMaxWidth()
                            .testTag("application_ats_finding_$index")
                        Row(
                            (if (place != null) row.clickable { navigate(place.route) } else row)
                                .padding(vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when (finding.verdict) {
                                    "ok" -> BewerboIcons.Covered
                                    "ungeprueft" -> BewerboIcons.Attention
                                    else -> BewerboIcons.NotClaimed
                                },
                                contentDescription = null,
                                tint = when (finding.verdict) {
                                    "ok" -> colors.success
                                    "ungeprueft" -> colors.attention
                                    else -> colors.danger
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = Space.s),
                            ) {
                                Text(atsFindingLabel(finding), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    atsFindingDetail(finding),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                                if (place != null) {
                                    Text(
                                        stringResource(
                                            R.string.ats_fill_in, stringResource(place.label),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("application_ats_fill_in_$index"),
                                    )
                                }
                            }
                            if (place != null) {
                                Icon(
                                    BewerboIcons.ChevronRight, contentDescription = null,
                                    tint = colors.muted, modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // The export. One file.
        item {
            BewerboCard(Modifier.testTag("application_export_card")) {
                SectionLabel(stringResource(R.string.application_export))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        BewerboIcons.Document, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = Space.s),
                    ) {
                        Text(
                            application.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("application_file_name"),
                        )
                        state.ats?.let {
                            Text(
                                stringResource(
                                    R.string.application_file_detail,
                                    it.pageCount, it.sizeBytes / 1024,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                    }
                }

                // What goes into the file, and it is a CHOICE. These read as chips and were not
                // selectable at all, while the export endpoint has taken a "parts" list all along
                // and was only ever called with null. The Anlagenverzeichnis is only offered when
                // the Mappe holds a document, because the renderer only produces that page then —
                // showing it otherwise promised a page the saved PDF did not contain.
                Text(
                    stringResource(R.string.application_export_choose),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = Space.s),
                )
                // FlowRow for the reason the Profil screen's section rail uses one: three chips fit
                // a phone-width row and the fourth did not — "Copies" was laid out past the right
                // edge, present to a driver and invisible to the user, which is the worst of both.
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    availableParts.forEach { part ->
                        val chosen = part in selectedParts
                        StatusPill(
                            stringResource(partLabel(part)),
                            if (chosen) PillTone.Success else PillTone.Neutral,
                            Modifier
                                .testTag("application_export_part_$part")
                                .clickable {
                                    selectedParts = if (chosen) {
                                        selectedParts - part
                                    } else selectedParts + part
                                },
                        )
                    }
                }

                // What the fourth part actually contains, in numbers. "Copies" on its own says
                // nothing about how many of the documents have one, and a Mappe that quietly
                // leaves out a Zeugnis the Anlagenverzeichnis promises is worse than one that
                // offers no copies at all.
                if ("scans" in availableParts) {
                    Text(
                        stringResource(
                            R.string.application_scans_summary, withCopy.size, documents.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier
                            .padding(top = Space.s)
                            .testTag("application_scans_summary"),
                    )
                }

                // And the ones that have none, by name. The user can do two things about it —
                // add the scan, or send that document separately — and neither is possible if
                // they only find out when the employer asks.
                if (withoutCopy.isNotEmpty() && documents.isNotEmpty()) {
                    Box(Modifier.padding(top = Space.s)) {
                        Callout(
                            icon = BewerboIcons.Attention,
                            title = pluralStringResource(
                                R.plurals.application_scans_missing_title,
                                withoutCopy.size, withoutCopy.size,
                            ),
                            body = stringResource(
                                R.string.application_scans_missing_body,
                                withoutCopy.joinToString(", ") { it.title },
                            ),
                            tone = PillTone.Attention,
                            modifier = Modifier.testTag("application_scans_missing"),
                        )
                    }
                }

                // The parts are named in German above — Anschreiben and Lebenslauf are explained
                // where the user first meets them, the Anlagenverzeichnis only appears here.
                TermNote(germanTerm("anlagenverzeichnis"))

                // The Vorlage of the Lebenslauf, next to the file it lays out. It used to sit on
                // the Profil screen one row BELOW the button that produces the Lebenslauf: a
                // decision about the document, asked after the action it belongs to and of a user
                // who had no document in front of them to choose against. Here the Lebenslauf is
                // one of the parts listed above it, so the choice has something to be about.
                //
                // It is stored on the person, which is why it is savePerson that writes it — the
                // same call the Profil screen made.
                val person = state.profile?.person
                Text(
                    stringResource(R.string.application_template),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = Space.m),
                )
                Box(Modifier.padding(top = Space.s)) {
                    SegmentedControl(
                        options = TEMPLATES,
                        selectedIndex = TEMPLATES.indexOf(person?.template ?: TEMPLATES[0])
                            .coerceAtLeast(0),
                        onSelect = { index ->
                            person?.let { viewModel.savePerson(it.copy(template = TEMPLATES[index])) }
                        },
                        modifier = Modifier.testTag("application_template_selector"),
                        tagPrefix = "application_template",
                        label = { stringResource(templateLabel(it)) },
                    )
                }
            }
        }

        // A failed check is a fault of the file that is about to leave the phone, and BOTH blocks
        // above produce them: a letter that reads as a Motivationsschreiben is as unsendable as one
        // an employer's system cannot index. So the export waits on either, and says which check is
        // failing rather than only greying out.
        //
        // Only "fehler" stops it. A "hinweis" of the Textprüfung is a suggestion about wording, and
        // "ungeprueft" says a check could not be run at all — neither is a fault of the document,
        // for the reason the two sections above state.
        val failedChecks = state.review?.checks.orEmpty().filter { it.verdict == "fehler" }
        val failedFindings = state.ats?.findings.orEmpty().filter { it.verdict == "fehler" }

        // The second thing that holds the export, and it is not a failed check: the profile no
        // longer carries what the letter is MADE of. The file is rendered from the profile as it is
        // now, not from the one the letter was written against, so a name cleared afterwards leaves
        // an Anschreiben with no sender address and nothing under the closing — and the
        // Maschinenlesbarkeit calls that "ungeprueft", which by design does not stop anything.
        // Same list the Abgleich refuses to write a letter from, so the two cannot drift apart.
        val blockers = state.overview?.letterBlockers.orEmpty()
        val failed = failedChecks.isNotEmpty() || failedFindings.isNotEmpty() || blockers.isNotEmpty()

        if (blockers.isNotEmpty()) {
            item {
                Callout(
                    icon = BewerboIcons.Attention,
                    title = stringResource(R.string.application_export_incomplete_title),
                    body = stringResource(
                        R.string.application_export_incomplete_body,
                        letterBlockerItems(blockers),
                        stringResource(placeOf("profil")?.label ?: R.string.nav_profile),
                    ),
                    tone = PillTone.Attention,
                    modifier = Modifier.testTag("application_export_incomplete"),
                )
            }
        }

        if (failedChecks.isNotEmpty() || failedFindings.isNotEmpty()) {
            item {
                // Both halves are already in the user's language here, so THIS join is a plain one.
                val named = listOf(reviewFailedLabels(failedChecks), atsFailedLabels(failedFindings))
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  ")
                Callout(
                    icon = BewerboIcons.NotClaimed,
                    title = stringResource(R.string.application_export_blocked_title),
                    body = stringResource(R.string.application_export_blocked_body, named),
                    tone = PillTone.Danger,
                    modifier = Modifier.testTag("application_export_blocked"),
                )
            }
        }

        item {
            Button(
                // Nothing selected is not an export; the server would silently fall back to all
                // three, which is the opposite of what the user just asked for.
                onClick = { viewModel.savePdf(selectedParts.joinToString(",")) },
                enabled = state.busy == null && selectedParts.isNotEmpty() && !failed,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("application_btn_save_pdf"),
            ) {
                Icon(BewerboIcons.Export, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.application_save_pdf),
                    modifier = Modifier.padding(start = Space.s),
                )
            }
        }

        // Saving was the only thing that could be done with the finished file, and the folder it
        // saves into is the app's own — so the Mappe had no way out of the phone at all. Most German
        // applications arrive by e-mail; this hands the same file to whichever mail app is there.
        item {
            OutlinedButton(
                onClick = { viewModel.sendPdfByEmail(selectedParts.joinToString(",")) },
                enabled = state.busy == null && selectedParts.isNotEmpty() && !failed,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("application_btn_send_email"),
            ) {
                Icon(BewerboIcons.Mail, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.application_send_email),
                    modifier = Modifier.padding(start = Space.s),
                )
            }
        }

        item {
            Callout(
                icon = BewerboIcons.Faltmarke,
                title = stringResource(R.string.application_why_german_title),
                body = stringResource(R.string.application_why_german_body),
                modifier = Modifier.testTag("application_why_german"),
            )
        }
    }
}

/**
 * One page of the Mappe, small, as the way to reach it.
 *
 * The current page carries a border in the primary colour rather than a checkmark or a label: this
 * strip sits directly under the page it selects, so the only thing it has to say is which of them
 * is the one above.
 */
@Composable
private fun PageThumbnail(
    page: android.graphics.Bitmap,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalSemanticColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = page.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(210f / 297f)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    // The same hairline BewerboCard draws, so an unselected page reads as paper
                    // rather than as a second control.
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                )
                .clickable(onClick = onClick)
                .testTag("application_page_thumb_$number"),
        )
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.muted,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/**
 * The Mappe as an e-mail: the PDF attached, the Betreffzeile as the subject, the covering note as
 * the body, and the address the posting handed the application to as the recipient.
 *
 * ACTION_SEND and not a mailto: URI — mailto carries no attachment, and the attachment is the whole
 * point. But a bare ACTION_SEND chooser is not an e-mail chooser: under a button that says "send by
 * e-mail" it offered Quick Share, Print, Drive, Bluetooth and Messages, with the mail app somewhere
 * further down. So the mail apps are resolved first, by the one question only they answer — SENDTO
 * on a mailto: URI — and the chooser is built from those alone. The chooser itself stays: which
 * mail app sends a German application is the user's business.
 *
 * The file lives in the app's private storage, so it goes out as a content:// URI from the
 * FileProvider declared in the manifest; FLAG_GRANT_READ_URI_PERMISSION is what lets the mail app
 * read it.
 *
 * FLAG_ACTIVITY_NEW_TASK is NOT optional here, however much it looks like it: every screen of this
 * app runs under the configuration context that [de.bewerbo.app.ui.UiLanguageProvider] provides as
 * LocalContext, so LocalContext.current is a ContextImpl and never the Activity — and starting an
 * activity from one without this flag throws AndroidRuntimeException and takes the app down.
 */
private fun emailChooser(context: Context, draft: EmailDraft, title: String): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", draft.file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, draft.subject)
        putExtra(Intent.EXTRA_TEXT, draft.body)
        if (draft.recipient.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(draft.recipient))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // The same intent once per mail app. One activity per package, because a mail app that
    // registers two of them would otherwise appear twice in a chooser of five.
    val mailApps = context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
        .distinctBy { it.activityInfo.packageName }
        .map { Intent(send).setPackage(it.activityInfo.packageName) }

    // A device with no mail app at all still gets the general sheet: an empty chooser would be a
    // worse answer than a wide one.
    val chooser = if (mailApps.isEmpty()) {
        Intent.createChooser(send, title)
    } else {
        Intent.createChooser(mailApps.first(), title)
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, mailApps.drop(1).toTypedArray())
    }
    return chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}


private fun partLabel(part: String) = when (part) {
    "anschreiben" -> R.string.part_anschreiben
    "lebenslauf" -> R.string.part_lebenslauf
    "scans" -> R.string.part_scans
    else -> R.string.part_anlagen
}

private fun templateLabel(template: String) = when (template) {
    "Klassisch" -> R.string.template_klassisch
    "Modern" -> R.string.template_modern
    else -> R.string.template_fachlich
}
