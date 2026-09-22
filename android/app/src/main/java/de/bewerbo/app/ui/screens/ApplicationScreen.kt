package de.bewerbo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.DinOverlay
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space

/**
 * Bewerbung — the rendered letter, the DIN inspector over it, the Prüfung, and the export.
 *
 * The preview is set in a serif at document proportions rather than in the app's UI font. That is
 * not decoration: what the user is checking is whether this reads as a German business letter, and
 * it cannot read as one in Segoe UI at list spacing.
 */
@Composable
fun ApplicationScreen(state: AppState, viewModel: AppViewModel) {
    val colors = LocalSemanticColors.current
    val application = state.application

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
            item {
                Callout(
                    icon = BewerboIcons.Document,
                    title = stringResource(R.string.application_none_title),
                    body = stringResource(R.string.application_none_body),
                )
            }
            return@LazyColumn
        }

        // The letter as a PAGE, not as a text card.
        //
        // This has to be A4-proportioned with the sender line, the Anschriftenfeld and the date
        // sitting where the norm puts them, because the inspector draws its boxes at fractions of
        // the page. Over a card that only holds body text, "Anschriftenfeld 45 mm" would be drawn
        // across the middle of a paragraph and would be telling the user something untrue.
        item {
            Box(Modifier.testTag("application_preview_pager")) {
                BewerboCard {
                    Text(
                        stringResource(R.string.application_page_one),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                    Box(
                        Modifier
                            .padding(top = Space.s)
                            .fillMaxWidth()
                            .aspectRatio(210f / 297f)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("application_page_1"),
                    ) {
                        LetterPage(state, application)

                        if (state.showDinGrid) {
                            // Same measurements as Rendering/DocumentTheme.cs, over the same page.
                            DinOverlay(
                                Modifier
                                    .fillMaxSize()
                                    .testTag("application_din_overlay"),
                            )
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

        // Prüfung — every check rule-based, so a failing one names what failed.
        state.review?.let { review ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(stringResource(R.string.application_review))
                    StatusPill(
                        if (review.hintCount == 0) {
                            stringResource(R.string.application_review_clean)
                        } else stringResource(R.string.application_review_hints, review.hintCount),
                        if (review.hintCount == 0) PillTone.Success else PillTone.Attention,
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
                                Text(check.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    check.detail,
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
        state.ats?.let { ats ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(stringResource(R.string.application_ats))
                    StatusPill(
                        stringResource(
                            if (ats.passed) R.string.application_ats_passed
                            else R.string.application_ats_failed,
                        ),
                        if (ats.passed) PillTone.Success else PillTone.Danger,
                        Modifier.testTag("application_ats_summary"),
                    )
                }
            }
            item {
                BewerboCard(Modifier.testTag("application_ats_card")) {
                    ats.findings.forEachIndexed { index, finding ->
                        Row(Modifier.padding(vertical = Space.xs)) {
                            Icon(
                                if (finding.found) BewerboIcons.Covered else BewerboIcons.NotClaimed,
                                contentDescription = null,
                                tint = if (finding.found) colors.success else colors.danger,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(Modifier.padding(start = Space.s)) {
                                Text(finding.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    finding.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                        }
                        @Suppress("UNUSED_EXPRESSION") index
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

                Row(
                    Modifier.padding(top = Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    listOf("anschreiben", "lebenslauf", "anlagenverzeichnis").forEach { part ->
                        StatusPill(
                            stringResource(partLabel(part)),
                            PillTone.Success,
                            Modifier.testTag("application_export_part_$part"),
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.savePdf() },
                enabled = state.busy == null,
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

        item {
            Callout(
                icon = BewerboIcons.Faltmarke,
                title = stringResource(R.string.application_why_german_title),
                body = stringResource(R.string.application_why_german_body),
                modifier = Modifier.testTag("application_why_german"),
            )
        }

        // Which writer produced this letter. Stated, not hidden.
        item {
            Text(
                stringResource(
                    if (application.source == "model") R.string.application_source_model
                    else R.string.application_source_rules,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("application_source"),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One A4 page of the Anschreiben, with every block at the fraction of the sheet the norm gives it.
 *
 * The numbers here are the same ones the PDF renderer uses (Rendering/DocumentTheme.cs): the
 * Anschriftenfeld opens at 45 mm of 297, the type area runs from 24.1 mm to 190 mm of 210. Keeping
 * them in step is what lets the inspector overlay mean anything.
 */
@Composable
private fun LetterPage(state: AppState, application: de.bewerbo.app.data.ApplicationView) {
    val person = state.profile?.person
    val posting = state.posting
    val colors = LocalSemanticColors.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        val pageWidth = maxWidth
        fun mmY(mm: Float) = pageHeight * (mm / 297f)
        fun mmX(mm: Float) = pageWidth * (mm / 210f)

        // The type area, inset from the left and right edges exactly as DIN 5008 states them.
        Column(
            Modifier
                .padding(start = mmX(24.1f), end = mmX(20f))
                .fillMaxSize(),
        ) {
            // 0 – 45 mm: the Briefkopf band, sender's one line at its foot.
            Box(Modifier.height(mmY(45f)), contentAlignment = Alignment.BottomStart) {
                MicroText(
                    listOfNotNull(
                        person?.let { "${it.firstName} ${it.lastName}".trim().ifBlank { null } },
                        person?.street?.ifBlank { null },
                        person?.let { "${it.postalCode} ${it.city}".trim().ifBlank { null } },
                    ).joinToString("  ·  "),
                    colors.muted,
                )
            }

            // 45 – 90 mm: the Anschriftenfeld.
            Column(Modifier.height(mmY(45f))) {
                Box(Modifier.height(mmY(5f)))
                listOfNotNull(
                    posting?.field("company")?.value?.ifBlank { null },
                    posting?.field("contact")?.value?.ifBlank { null },
                    posting?.field("contactRole")?.value?.ifBlank { null },
                    posting?.field("companyAddress")?.value?.ifBlank { null },
                ).flatMap { it.split(",").map(String::trim) }
                    .forEach { DocumentText(it) }
            }

            // The Informationsblock: place and date, right-aligned.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                DocumentText(
                    "${person?.city.orEmpty()}, ${todayInGerman()}".trimStart(',', ' '),
                    align = TextAlign.End,
                )
            }

            Box(Modifier.height(mmY(8f)))
            DocumentText(application.letter.subject, bold = true)
            Box(Modifier.height(mmY(8f)))
            DocumentText("${application.letter.salutation},")

            application.letter.paragraphs.forEach { paragraph ->
                Box(Modifier.height(mmY(4f)))
                DocumentText(paragraph, justify = true)
            }

            Box(Modifier.height(mmY(6f)))
            DocumentText(application.letter.closing)
            Box(Modifier.height(mmY(10f)))
            DocumentText("${person?.firstName.orEmpty()} ${person?.lastName.orEmpty()}".trim())

            if (application.letter.attachments.isNotEmpty()) {
                Box(Modifier.height(mmY(6f)))
                DocumentText(
                    stringResource(
                        R.string.application_attachments,
                        application.letter.attachments.joinToString(", "),
                    ),
                )
            }
        }
    }
}

@Composable
private fun MicroText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = 7.sp,
        lineHeight = 9.sp,
        color = color,
    )
}

private fun todayInGerman(): String {
    val today = java.time.LocalDate.now()
    val month = java.time.format.DateTimeFormatter
        .ofPattern("d. MMMM yyyy", java.util.Locale.GERMAN)
    return today.format(month)
}

/// The document's own type: a serif at the proportions of the page, not the app's UI font.
@Composable
private fun DocumentText(
    text: String,
    bold: Boolean = false,
    justify: Boolean = false,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        // Small enough that a whole A4 page of a real letter fits the preview at its true
        // proportions. The PDF is set at 10.5 pt; this is the same page, scaled.
        fontSize = 7.sp,
        lineHeight = 10.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align ?: if (justify) TextAlign.Justify else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun partLabel(part: String) = when (part) {
    "anschreiben" -> R.string.part_anschreiben
    "lebenslauf" -> R.string.part_lebenslauf
    else -> R.string.part_anlagen
}
