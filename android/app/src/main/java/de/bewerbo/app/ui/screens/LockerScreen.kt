package de.bewerbo.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import de.bewerbo.app.R
import de.bewerbo.app.data.AppState
import de.bewerbo.app.data.AppViewModel
import de.bewerbo.app.data.DemandedDocument
import de.bewerbo.app.data.PickedScan
import de.bewerbo.app.data.StoredDocument
import de.bewerbo.app.data.documentFile
import de.bewerbo.app.data.documentsDir
import de.bewerbo.app.ui.components.BewerboCard
import de.bewerbo.app.ui.components.BewerboDialog
import de.bewerbo.app.ui.components.Callout
import de.bewerbo.app.ui.components.IconRow
import de.bewerbo.app.ui.components.LabelledField
import de.bewerbo.app.ui.components.PillTone
import de.bewerbo.app.ui.components.SectionLabel
import de.bewerbo.app.ui.components.SegmentedControl
import de.bewerbo.app.ui.components.StatusPill
import de.bewerbo.app.ui.icons.BewerboIcons
import de.bewerbo.app.ui.theme.LocalSemanticColors
import de.bewerbo.app.ui.theme.Space
import java.io.File

private val KINDS = listOf("Arbeitszeugnis", "Zertifikat", "Sprachnachweis", "AnabinAuszug")

/// The three types a scan may be, as the system picker is asked for them. The same three the
/// server accepts; what it actually stores is decided from the bytes either way.
private val SCAN_TYPES = arrayOf("application/pdf", "image/jpeg", "image/png")

/// Where the camera writes. Under the folder file_paths.xml already shares, so the picture comes
/// back through the same FileProvider everything else in this app leaves through.
private const val PHOTO_FILE = "scan-photo.jpg"

/**
 * Mappe — the documents the Anlagenverzeichnis refers to.
 *
 * Two things are stored here and they are separate on purpose. The RECORD of a document — its
 * title, its kind, how many pages — is what the Anlagenverzeichnis needs, and it is what every
 * device that signs in sees. The SCAN is the file behind it, and it is optional: a row may name a
 * Zeugnis whose file has not been added yet, which is what every document added on another phone
 * looks like until somebody adds it.
 *
 * That separation is the card this screen was built for. The list used to be complete on a second
 * device while every file behind it was gone, so the Bewerbungsmappe could not be put together
 * there. A scan now belongs to the account, and a row says plainly which of the two it has.
 *
 * Above the list stands what the posting asks to see, so the Mappe answers the question the user
 * actually arrives with — not "what have I got" but "what is still missing for this application".
 */
@Composable
fun LockerScreen(state: AppState, viewModel: AppViewModel) {
    val colors = LocalSemanticColors.current
    val documents = state.profile?.documents.orEmpty()
    // Which documents are demanded is a property of the posting, so it is only known once one has
    // been read. With no Abgleich behind it the Mappe stays a plain list of what is on file rather
    // than inventing demands it cannot know about.
    val demands = state.match?.documents.orEmpty()
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Which document a chosen file belongs to — null means the one being written in the add card,
    // which has no id yet. One pair of launchers for the whole screen, because a launcher may only
    // be remembered in composition and every row would otherwise carry its own.
    var pickFor by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.pickScan(it, pickFor) }
    }
    val photoTarget = remember { context.photoTarget() }
    val takePhoto = rememberLauncherForActivityResult(TakePictureInto()) { taken ->
        if (taken) viewModel.pickScan(photoTarget, pickFor)
    }

    // The two facts the disclosure cannot be written without: whether this account has already
    // agreed, and who runs this installation. The operator is asked of the server rather than
    // compiled in — see LegalOptions for why an address in the APK would be a false statement.
    LaunchedEffect(state.profile?.id) {
        viewModel.loadScanConsent()
        if (state.legal == null) viewModel.loadLegal()
    }

    // The fetched copy on its way to another app, through the same handover the Mappe and the data
    // copy get: an Intent needs a context that can start an activity, and the view model holds
    // only the Application.
    val shareTitle = stringResource(R.string.scan_action_share)
    LaunchedEffect(state.pendingScanShare) {
        state.pendingScanShare?.let { file ->
            context.startActivity(scanChooser(context, file, state.openScan?.scan?.contentType, shareTitle))
            viewModel.scanShareHandled()
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("locker_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column {
                Text(stringResource(R.string.nav_locker), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.locker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }

        if (demands.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.locker_demand_title, demands.size)) }
            item {
                BewerboCard(Modifier.testTag("locker_demand_group")) {
                    demands.forEachIndexed { index, demand -> DemandRow(demand, index) }
                }
            }
        }

        itemsIndexed(documents) { index, document ->
            DocumentRow(
                document = document,
                index = index,
                deviceId = viewModel.deviceId,
                busy = state.busy != null,
                onOpenScan = { viewModel.openScan(document) },
                onAddScan = {
                    pickFor = document.id
                    pickFile.launch(SCAN_TYPES)
                },
                onDelete = { document.id?.let { viewModel.deleteDocument(it) } },
            )
        }

        if (documents.isEmpty()) {
            item {
                Callout(
                    icon = BewerboIcons.Anlagen,
                    title = stringResource(R.string.locker_empty_title),
                    body = stringResource(R.string.locker_empty_body),
                )
            }
        }

        item {
            if (!adding) {
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("locker_btn_add"),
                ) {
                    Icon(BewerboIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.locker_add),
                        modifier = Modifier.padding(start = Space.s),
                    )
                }
            } else {
                AddDocumentCard(
                    state = state,
                    viewModel = viewModel,
                    onChooseFile = {
                        pickFor = null
                        pickFile.launch(SCAN_TYPES)
                    },
                    onTakePhoto = {
                        pickFor = null
                        // The previous photograph goes before the next one is taken. It has been
                        // uploaded by now and the bytes were read into memory, so nothing needs
                        // it — and a picture of somebody's Zeugnis is not a thing to leave lying
                        // in the app's files waiting for a sign-out to clear it.
                        context.documentFile(PHOTO_FILE).delete()
                        takePhoto.launch(photoTarget)
                    },
                ) { adding = false }
            }
        }

        item {
            Callout(
                icon = BewerboIcons.EuStorage,
                title = stringResource(R.string.locker_storage_title),
                body = stringResource(R.string.locker_storage_body),
                modifier = Modifier.testTag("locker_storage_notice"),
            )
        }
    }

    // Both windows of their own, and therefore both carrying exposeTestTags through BewerboDialog.
    if (state.scanNoticeOpen) ScanNoticeDialog(state, viewModel)
    state.openScan?.let { open ->
        ScanViewerDialog(open, state, viewModel) {
            pickFor = open.id
            pickFile.launch(SCAN_TYPES)
        }
    }
}

/**
 * One document, and whether its file is here.
 *
 * The state is said in WORDS and not only in a tint, the way the demand rows above it are: "No copy
 * stored" over a row whose only difference from its neighbour is a paler icon is not something a
 * user reads. A row without a copy also says what is missing, because the absence looks like a
 * fault of this screen otherwise, and offers the one action that fixes it — WHY it is missing is
 * only said where the record answers it, see [noCopyReason].
 */
@Composable
private fun DocumentRow(
    document: StoredDocument,
    index: Int,
    deviceId: String,
    busy: Boolean,
    onOpenScan: () -> Unit,
    onAddScan: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalSemanticColors.current
    val stored = document.scan != null

    BewerboCard(Modifier.testTag("locker_item_$index")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                kindIcon(document.kind), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.s),
            ) {
                Text(document.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        document.note.ifBlank { null },
                        // A count next to a number needs its plural rule: a one-page
                        // Zeugnis read "1 pages", which the Anlagenverzeichnis itself has
                        // always got right ("1 Seite").
                        pluralStringResource(
                            R.plurals.locker_page_count,
                            document.pageCount,
                            document.pageCount,
                        ),
                        // Where that count came from, once there is a file it could have come
                        // off. The two may differ on purpose, so the row says which one is being
                        // printed rather than leaving the user to guess.
                        if (stored) stringResource(pagesSourceLabel(document)) else null,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )

                Row(
                    Modifier.padding(top = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(
                        stringResource(if (stored) R.string.locker_copy_stored else R.string.locker_no_copy),
                        if (stored) PillTone.Success else PillTone.Attention,
                        Modifier.testTag("locker_item_copy_$index"),
                    )
                    if (stored) {
                        TextButton(
                            onClick = onOpenScan,
                            enabled = !busy,
                            modifier = Modifier.testTag("locker_item_open_scan_$index"),
                        ) {
                            Text(stringResource(R.string.locker_open_scan))
                        }
                    }
                }

                if (!stored) {
                    Text(
                        stringResource(noCopyReason(document, deviceId)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier
                            .padding(top = Space.xs)
                            .testTag("locker_item_no_copy_reason_$index"),
                    )
                    OutlinedButton(
                        onClick = onAddScan,
                        enabled = !busy,
                        modifier = Modifier
                            .padding(top = Space.xs)
                            .testTag("locker_item_add_scan_$index"),
                    ) {
                        Text(stringResource(R.string.locker_add_scan))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(stringResource(documentKindLabel(document.kind)), PillTone.Success)
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("locker_item_delete_$index"),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

/**
 * One document the posting asks to see, with whether the Mappe can produce it.
 *
 * The pill fires only on an outstanding row. A satisfied demand has nothing for the user to do,
 * and a marker on every row is what taught users to stop reading the markers on the
 * Stellenanzeige. The state is still said in words on both kinds of row, so it does not rest on
 * the icon tint alone. The detail line is the posting's own sentence: the row has to be checkable
 * against the advert, not just asserted.
 */
@Composable
private fun DemandRow(demand: DemandedDocument, index: Int) {
    val onFile = stringResource(R.string.locker_demand_on_file)

    IconRow(
        icon = kindIcon(demand.kind),
        // The kind in the user's language, then what the server appended to it — the language and
        // level of a Sprachnachweis, which read the same in every language.
        title = (listOf(stringResource(documentKindLabel(demand.kind))) + demand.titleArgs)
            .joinToString(" "),
        detail = if (demand.onFile) {
            listOfNotNull(onFile, demand.quote.ifBlank { null }).joinToString("  ·  ")
        } else {
            demand.quote.ifBlank { null }
        },
        tone = if (demand.onFile) PillTone.Success else PillTone.Attention,
        trailing = if (demand.onFile) {
            null
        } else {
            {
                StatusPill(
                    stringResource(R.string.locker_demand_outstanding),
                    PillTone.Attention,
                    Modifier.testTag("locker_demand_outstanding_$index"),
                )
            }
        },
        modifier = Modifier.testTag("locker_demand_$index"),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddDocumentCard(
    state: AppState,
    viewModel: AppViewModel,
    onChooseFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalSemanticColors.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("1") }
    // Whether the number in the field is the user's own. It is what decides the rule below, and it
    // travels with the record so the server applies the same one to the upload that follows.
    var pagesStated by remember { mutableStateOf(false) }
    var kind by remember { mutableIntStateOf(0) }

    // The Pages field follows the file the moment one is chosen HERE, UNLESS the user has typed a
    // number of their own. The count goes into the Anlagenverzeichnis, so where nobody said
    // otherwise it is better read off the document than left at the "1" this field starts on — but
    // a scan of three sheets that belongs to a two-page document is the user's statement to make,
    // and it used to be overwritten here without a word. An emptied field is not an entry, so it
    // gives the number back to the file — at the next pick and not while it is being typed in.
    //
    // A file chosen for a row in the LIST is on its way to that row and never reaches this card.
    // It travels through the state's other slot, where this card's file used to sit as well and be
    // overwritten by it — first in the number above, then in the file row itself, which simply
    // vanished and left the Save filing a document with no copy. This card's file waits in a slot
    // of its own until the Save below.
    val picked = state.addFormScan
    LaunchedEffect(picked) {
        if (picked != null && !pagesStated) pages = picked.pageCount.toString()
    }

    BewerboCard(Modifier.testTag("locker_add_card")) {
        SectionLabel(stringResource(R.string.locker_add))
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.locker_field_title), title, { title = it },
            testTag = "locker_input_title")
        Box(Modifier.padding(top = Space.s))
        LabelledField(stringResource(R.string.locker_field_note), note, { note = it },
            testTag = "locker_input_note")

        Box(Modifier.padding(top = Space.s))
        Text(
            stringResource(R.string.locker_field_scan),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
        // The two buttons WRAP, for the reason the Profil screen's section rail does: a fixed row
        // measures the first button at the width it asks for and leaves the second whatever is
        // over, and at the accessibility maximum of the system font size that remainder is a
        // column too narrow for a word — "Datei auswählen" arrived as a vertical stack of single
        // letters, in front of exactly the reader who raised the font size in order to read at
        // all. Given a line of its own each label is a whole word in every interface language.
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.xs)
                .testTag("locker_scan_actions"),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            OutlinedButton(
                onClick = onTakePhoto,
                enabled = state.busy == null,
                modifier = Modifier.testTag("locker_btn_scan_photo"),
            ) {
                Text(stringResource(R.string.locker_scan_photo))
            }
            OutlinedButton(
                onClick = onChooseFile,
                enabled = state.busy == null,
                modifier = Modifier.testTag("locker_btn_scan_file"),
            ) {
                Text(stringResource(R.string.locker_scan_file))
            }
        }

        if (picked != null) PickedScanRow(picked, viewModel::discardPickedScan)

        Box(Modifier.padding(top = Space.s))
        LabelledField(
            stringResource(R.string.locker_field_pages), pages,
            { pages = it; pagesStated = it.isNotBlank() },
            testTag = "locker_input_pages",
        )
        // Which of the two numbers is standing in the field, said where the two can differ — with
        // a file chosen there is the count it has and the count the user typed, and the row above
        // names the file's. Without one there is nothing to tell apart.
        if (picked != null) {
            Text(
                stringResource(
                    if (pagesStated) R.string.locker_pages_from_you else R.string.locker_pages_from_file,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.testTag("locker_pages_source"),
            )
        }

        Box(Modifier.padding(top = Space.s))
        SegmentedControl(
            options = KINDS,
            selectedIndex = kind,
            onSelect = { kind = it },
            modifier = Modifier.testTag("locker_kind_selector"),
            tagPrefix = "locker_kind",
            label = { stringResource(documentKindLabel(it)) },
        )

        // The title is what the document IS — the server refuses a record without one, and the
        // Anlagenverzeichnis would otherwise print a numbered Anlage with nothing beside it. Saying
        // what is missing here is the same contract the Berufserfahrung form keeps, instead of
        // sending the save and closing the card on the answer that comes back.
        val missing = buildList {
            if (title.isBlank()) add(stringResource(R.string.locker_field_title))
        }
        if (missing.isNotEmpty()) {
            Text(
                stringResource(R.string.experience_missing, missing.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = colors.attention,
                modifier = Modifier
                    .padding(top = Space.s)
                    .testTag("locker_missing_hint"),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Button(
                onClick = {
                    viewModel.addDocument(
                        StoredDocument(
                            title = title, kind = KINDS[kind], note = note,
                            pageCount = pages.toIntOrNull() ?: 1,
                            pageCountStated = pagesStated,
                        ),
                    )
                    onDone()
                },
                enabled = missing.isEmpty(),
                modifier = Modifier.testTag("locker_btn_save"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(
                onClick = {
                    // A file chosen and then cancelled must not survive into the next document the
                    // user adds: it never reached the server, and it is not theirs to carry over.
                    viewModel.discardPickedScan()
                    onDone()
                },
                modifier = Modifier.testTag("locker_btn_cancel"),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

/// The file that has been chosen and has not gone anywhere: its name, its size and the page count
/// that just filled the field above.
@Composable
private fun PickedScanRow(picked: PickedScan, onRemove: () -> Unit) {
    val colors = LocalSemanticColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.s)
            .testTag("locker_picked_scan"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            BewerboIcons.Document, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = Space.s),
        ) {
            Text(picked.fileName, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOf(
                    scanTypeLabel(picked.contentType),
                    pluralStringResource(R.plurals.locker_page_count, picked.pageCount, picked.pageCount),
                    stringResource(R.string.locker_size_kb, picked.sizeKb),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        TextButton(onClick = onRemove, modifier = Modifier.testTag("locker_picked_scan_remove")) {
            Text(stringResource(R.string.locker_scan_remove))
        }
    }
}

/**
 * The disclosure — what is held about a scan and where, read BEFORE the first one leaves the phone.
 *
 * Four facts and no more: what, where, why, how long. It is shown at the moment a file is chosen
 * and not at the save, which is what makes it something the user decides rather than something
 * they are told about a transfer already under way — at this point the bytes have been read off
 * the device and gone nowhere, and the sentence under the title says so.
 *
 * "Where" names the operator this installation states, never a name compiled into the app. A
 * deployment that has not said who runs it gets the sentence that says THAT, for the reason the
 * Impressum page does: a placeholder there is a false statement about who is responsible.
 */
@Composable
private fun ScanNoticeDialog(state: AppState, viewModel: AppViewModel) {
    val picked = state.pickedScan
    val operator = state.legal?.operatorDetails

    BewerboDialog(
        onDismissRequest = viewModel::declineScans,
        testTag = "scan_notice",
        title = { Text(stringResource(R.string.scan_notice_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.scan_notice_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSemanticColors.current.muted,
                )
                NoticeFact(
                    R.string.scan_notice_what_label,
                    stringResource(
                        R.string.scan_notice_what,
                        picked?.fileName.orEmpty().ifBlank { stringResource(R.string.locker_field_scan) },
                    ),
                    "scan_notice_what",
                )
                NoticeFact(
                    R.string.scan_notice_where_label,
                    if (operator != null && operator.stated) {
                        stringResource(R.string.scan_notice_where, operator.name)
                    } else {
                        stringResource(R.string.scan_notice_where_unnamed)
                    },
                    "scan_notice_where",
                )
                NoticeFact(R.string.scan_notice_why_label, stringResource(R.string.scan_notice_why), "scan_notice_why")
                NoticeFact(
                    R.string.scan_notice_how_long_label,
                    stringResource(R.string.scan_notice_how_long),
                    "scan_notice_how_long",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::agreeToScans,
                enabled = state.busy == null,
                modifier = Modifier.testTag("scan_notice_agree"),
            ) {
                Text(stringResource(R.string.scan_notice_agree))
            }
        },
        dismissButton = {
            // "Not now" and not the mockup's "Keep it on this device only": there is no device-only
            // store behind that promise, and a button that says there is would be the very thing
            // this notice exists to prevent. Declining drops the file, which never left the phone.
            OutlinedButton(
                onClick = viewModel::declineScans,
                modifier = Modifier.testTag("scan_notice_decline"),
            ) {
                Text(stringResource(R.string.scan_notice_decline))
            }
        },
    )
}

@Composable
private fun NoticeFact(label: Int, body: String, tag: String) {
    Column(Modifier.padding(top = Space.m)) {
        SectionLabel(stringResource(label))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = Space.xs)
                .testTag(tag),
        )
    }
}

/**
 * The stored copy, opened — the half of this card that makes a scan added on one phone readable on
 * the next.
 *
 * It renders the file that actually came back from the server rather than showing a name and a
 * size, for the reason the Mappe's preview renders the real PDF: what is being asked is whether
 * the copy is the Zeugnis it claims to be, and only the pages answer that.
 */
@Composable
private fun ScanViewerDialog(
    document: StoredDocument,
    state: AppState,
    viewModel: AppViewModel,
    onReplace: () -> Unit,
) {
    val colors = LocalSemanticColors.current
    val info = document.scan
    val pages = state.scanPages

    BewerboDialog(
        onDismissRequest = viewModel::closeScan,
        testTag = "scan_viewer",
        title = { Text(document.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (info != null) {
                    Text(
                        listOf(
                            scanTypeLabel(info.contentType),
                            pluralStringResource(
                                R.plurals.locker_page_count, document.pageCount, document.pageCount,
                            ),
                            stringResource(pagesSourceLabel(document)),
                            stringResource(R.string.locker_size_kb, (info.sizeBytes + 1023) / 1024),
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.testTag("scan_viewer_detail"),
                    )
                }

                when {
                    // Still on its way. Said in words rather than left blank, because an empty box
                    // under a title reads as a file that is not there.
                    state.busy == "scan" && pages.isEmpty() -> Text(
                        stringResource(R.string.scan_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier
                            .padding(top = Space.m)
                            .testTag("scan_viewer_loading"),
                    )
                    pages.isEmpty() -> Text(
                        stringResource(R.string.scan_unreadable),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier
                            .padding(top = Space.m)
                            .testTag("scan_viewer_unreadable"),
                    )
                    else -> pages.forEachIndexed { index, page ->
                        Column(Modifier.padding(top = Space.m)) {
                            Image(
                                bitmap = page.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                                    .testTag("scan_viewer_page_$index"),
                            )
                            Text(
                                stringResource(R.string.scan_page_of, index + 1, pages.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                    }
                }

                Row(
                    Modifier.padding(top = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    OutlinedButton(
                        onClick = viewModel::shareScan,
                        enabled = pages.isNotEmpty(),
                        modifier = Modifier.testTag("scan_btn_share"),
                    ) {
                        Text(stringResource(R.string.scan_action_share))
                    }
                    OutlinedButton(onClick = onReplace, modifier = Modifier.testTag("scan_btn_replace")) {
                        Text(stringResource(R.string.scan_action_replace))
                    }
                }

                Text(
                    stringResource(R.string.scan_remove_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(top = Space.m),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { document.id?.let(viewModel::removeScan) },
                modifier = Modifier.testTag("scan_btn_remove"),
            ) {
                Text(stringResource(R.string.scan_action_remove), color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeScan, modifier = Modifier.testTag("scan_btn_close")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * The system camera, writing into a file of ours.
 *
 * [ActivityResultContracts.TakePicture] builds the intent without a grant on the URI it hands over,
 * and a camera app that cannot write to it returns false with nothing to show for it. The two flags
 * are the whole of the subclass.
 */
private class TakePictureInto : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/// Where a photographed document lands before it is read: the folder file_paths.xml shares, which
/// has to exist before another app is asked to write into it.
private fun Context.photoTarget(): Uri {
    documentsDir().mkdirs()
    return FileProvider.getUriForFile(this, "$packageName.files", documentFile(PHOTO_FILE))
}

/**
 * The stored copy on its way to another app.
 *
 * A GENERAL chooser, as the data copy gets and unlike the Mappe's mail-app one: a scan of a Zeugnis
 * goes wherever the user keeps things — a cloud folder, a message, a printer. FLAG_ACTIVITY_NEW_TASK
 * is not optional; see [emailChooser] on the Bewerbung screen for what happens without it.
 */
private fun scanChooser(context: Context, file: File, contentType: String?, title: String): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = contentType ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/// Where the page count shown for a document came from: the user's own entry, or the file behind
/// it. Asked only where a copy is stored — without one the count can be nobody's but the user's,
/// and a line saying so under every document would say nothing.
private fun pagesSourceLabel(document: StoredDocument) =
    if (document.pageCountStated) R.string.locker_pages_from_you else R.string.locker_pages_from_file

/// Why a document has no stored copy. Every row without one used to be told it had been added on
/// another device, which is not something the record says: a document filed on THIS phone with no
/// file chosen, and one whose file the server refused, are both in that state and neither came
/// from anywhere else. The claim is made only where the stamp the record carries is another
/// device's — an empty stamp is a record from before it existed and answers neither way, so it
/// gets the sentence that is true of all three: the scan is missing, and adding it is the move.
private fun noCopyReason(document: StoredDocument, deviceId: String) =
    if (document.addedOnDevice.isNotBlank() && document.addedOnDevice != deviceId) {
        R.string.locker_added_elsewhere
    } else {
        R.string.locker_no_scan_yet
    }

/// What a scan's type is called on screen. Not translated and not meant to be: PDF, JPEG and PNG
/// are the same three letters in every language the app is offered in.
private fun scanTypeLabel(contentType: String) = when (contentType) {
    "image/jpeg" -> "JPEG"
    "image/png" -> "PNG"
    else -> "PDF"
}

private fun kindIcon(kind: String) = when (kind) {
    "Arbeitszeugnis" -> BewerboIcons.Document
    "Zertifikat" -> BewerboIcons.Check
    "Sprachnachweis" -> BewerboIcons.Languages
    "AnabinAuszug" -> BewerboIcons.Anabin
    else -> BewerboIcons.Anlagen
}

/// What a kind is called on screen. The value keeps the backend's spelling. Two of them are German
/// on purpose — Arbeitszeugnis and Sprachnachweis are words a posting uses, and they are explained
/// where the user first meets them; the rest are not, so they are translated.
///
/// Every kind of DocumentKind is named, and the fallback is the neutral one rather than the last
/// branch that happened to be there: a Sonstiges document used to be labelled "anabin-Auszug",
/// which does not just read wrong, it says the wrong thing about the document. Same shape as
/// [kindIcon] above, which had it right.
fun documentKindLabel(kind: String) = when (kind) {
    "Arbeitszeugnis" -> R.string.kind_arbeitszeugnis
    "Zertifikat" -> R.string.kind_zertifikat
    "Sprachnachweis" -> R.string.kind_sprachnachweis
    "AnabinAuszug" -> R.string.kind_anabin_auszug
    else -> R.string.kind_sonstiges
}
