package de.bewerbo.app.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bewerbo.app.R
import de.bewerbo.app.ui.uiLanguageOrDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Why a call failed, in the two pieces the snackbar needs. [kind] is what the message says, looked
 * up in the user's language by the screen; [detail] is the German the server sent, shown only for
 * a kind this build does not know.
 *
 * A view model has no resources to read, and it must not: the interface language is the app's own
 * preference and it is applied in the composition. So the failure travels as a kind here for the
 * same reason a [NextStep] does — see `errorMessage` on the UI side.
 */
data class ErrorMessage(val kind: String, val detail: String = "")

data class AppState(
    val loading: Boolean = true,
    val error: ErrorMessage? = null,
    /// What the backend said about itself — which writer is in use.
    val writer: String = "regeln",
    val profile: ProfileView? = null,
    val timeline: Timeline = Timeline(),
    /// Null until it has been fetched. An empty default would have the Übersicht answer "nothing
    /// outstanding" over a profile it has not read yet, and offer a first step it then changes.
    val overview: Overview? = null,
    val posting: PostingView? = null,
    /// The advert text that has been brought in but not read yet — pasted, fetched from a link or
    /// recognised in a picture, and editable in all three cases before [AppViewModel.parsePosting]
    /// is asked for anything.
    ///
    /// State and not the screen's own `remember`, because two of the three ways in leave the app:
    /// choosing a picture opens the system picker, and a Compose state that the activity's
    /// recreation takes with it would lose the advert the user just photographed.
    val postingDraft: String = "",
    /// The conversation with the assistant, oldest first — what the screen draws and what the
    /// next turn is sent as. Client-side and nothing else: the server keeps no conversation, so this
    /// list IS the assistant's memory, and it lives no longer than the process does.
    val assistant: List<AssistantTurn> = emptyList(),
    /// What is in the assistant's composer. State and not the screen's own `remember`, for the
    /// reason [postingDraft] is: choosing a picture of an old Lebenslauf opens the system picker and
    /// leaves the app, and the text read out of it would go with a recreated activity.
    val assistantDraft: String = "",
    val match: MatchView? = null,
    val application: ApplicationView? = null,
    /// The application the user opened and this phone could not fetch — its id, or null while no
    /// fetch has failed. [AppViewModel.openApplication] clears the application before it calls, so
    /// a call that never answers leaves behind the very null a phone with no application at all
    /// has, and the Bewerbung screen answered it with its empty state: it told a user whose letter
    /// was written to go back and do the Abgleich they had just finished.
    ///
    /// Kept as state rather than left to the error snackbar for the reason [previewFailure] is:
    /// that one is gone in seconds and the screen it leaves behind says the opposite of what
    /// happened. The id and not a flag, because asking again is the one thing the user can do
    /// about it and the retry has to know WHICH application to ask for.
    val unfetchedApplication: String? = null,
    val review: Review? = null,
    val ats: AtsResult? = null,
    /// The anabin entries offered for ONE qualification, keyed by that entry's id — the same shape
    /// as [dutyOutcomes] and for the same reason: held for the whole screen, one list was drawn
    /// under every education card, so an offer looked up for one degree could be confirmed onto
    /// another. [confirmEquivalence] drops the offers of the entry it stored.
    val degrees: Map<String, List<DegreeEquivalence>> = emptyMap(),
    /// The German wording proposed for a gap reason while the user is still typing it, keyed by the
    /// gap's start date — one entry per gap card on the screen. It is what [explainGap] then
    /// stores, so the sentence the user read is the sentence the Lebenslauf gets.
    val gapWording: Map<String, String> = emptyMap(),
    /// The duties of a position written as results, beside the originals and with the user's choice
    /// per line, keyed by the entry's id — one entry per experience card that has asked for them.
    /// Present only while the user is deciding: [acceptDutyOutcomes] stores what they chose and
    /// [discardDutyOutcomes] drops it, so nothing here ever reaches a document on its own.
    val dutyOutcomes: Map<String, List<DutyChoice>> = emptyMap(),
    /// The exported file rendered page by page — what the Bewerbung screen's preview pages through.
    /// Empty until it has been fetched, and re-fetched whenever the chosen parts change.
    val previewPages: List<android.graphics.Bitmap> = emptyList(),
    /// Why there are no pages, or null while nothing has failed: [AppViewModel.PREVIEW_NOT_FETCHED]
    /// when the file never arrived, [AppViewModel.PREVIEW_NOT_DRAWN] when it did and could not be
    /// rasterised. A kind and not a flag, because the screen says a different sentence for each —
    /// the same shape [busy] has. Kept as state rather than left to the error snackbar: that one is
    /// gone in seconds and the preview would go on standing there empty with no way back.
    val previewFailure: String? = null,
    /// The chosen parts the preview currently in hand was asked for — what [refreshPreview] checks
    /// its finished pages against before publishing them, the way the scan viewer checks the id of
    /// the document it is showing. The drawing happens off the main thread, so the chosen parts can
    /// have changed by the time it ends.
    val previewParts: String? = null,
    /// Set when the user asked to send the Mappe. The screen hands it to a mail app and clears it.
    val pendingEmail: EmailDraft? = null,
    /// Set when the user asked for the copy of what is held about them. The settings screen hands
    /// it to a chooser and clears it — the same handover [pendingEmail] gets, for the same reason:
    /// a file in the app’s own storage is reachable from nowhere else.
    val pendingExport: File? = null,
    /// Set when the user asked for the Lebenslauf from the conversation. The assistant hands it to a
    /// chooser and clears it — the same handover [pendingExport] gets, and its own field because the
    /// two are picked up on different screens and one is a PDF where the other is a JSON copy.
    val pendingCv: File? = null,
    /// Whether this account has agreed to Bewerbo holding the scans of its documents. Null until
    /// the Documents screen has asked, and treated as "not yet" while it is — the disclosure must
    /// not be skipped because an answer has not arrived.
    val scanConsent: Boolean? = null,
    /// The file the user chose as a scan, read off the device and STILL ON IT. Nothing has been
    /// sent while this is set: it is held here while the disclosure is read, and dropped if the
    /// user decides against it. State and not the screen's own `remember`, for the reason
    /// [postingDraft] is — choosing a file opens the system picker and leaves the app.
    ///
    /// The pick that is on its way somewhere, and no more than that: once the disclosure has been
    /// answered it is either uploaded to [pickedScanFor] or handed to [addFormScan], and this
    /// field is let go either way. See [AppViewModel.deliverPickedScan].
    val pickedScan: PickedScan? = null,
    /// Which document the picked scan belongs to, or null when it is for the document currently
    /// being added and there is no id yet. That is the difference between "upload it now" and
    /// "hold it until Save".
    val pickedScanFor: String? = null,
    /// The file chosen in the add card, waiting there for its Save — the only place it can wait,
    /// because the record it belongs to does not exist yet.
    ///
    /// A slot of its own and not [pickedScan], because the list above the open form stays usable:
    /// a scan added to a row in it travels through [pickedScan] and used to overwrite the file the
    /// form was holding, so the form's file row disappeared and the Save filed a document with no
    /// copy at all. Two places that hold a file at the same time need two fields.
    val addFormScan: PickedScan? = null,
    /// Whether the disclosure is up. Set the moment a scan is picked by an account that has not
    /// agreed yet, cleared by agreeing or declining — see [AppViewModel.pickScan].
    val scanNoticeOpen: Boolean = false,
    /// The document whose stored copy is open in the viewer, and that copy rendered page by page.
    /// Empty pages while it is being fetched.
    val openScan: StoredDocument? = null,
    val scanPages: List<android.graphics.Bitmap> = emptyList(),
    /// Whether the copy open in the viewer is being replaced. True from the moment the new file is
    /// on its way until the record it produced has been fetched and rendered, and the viewer says so
    /// for as long as it is: the detail line and the pages it is still holding describe the copy on
    /// the way OUT, and a reader who takes them for the current one cannot tell that the replacement
    /// happened at all. See [AppViewModel.deliverPickedScan].
    val openScanReplacing: Boolean = false,
    /// The fetched scan, ready to be handed to another app. The screen passes it to a chooser and
    /// clears it — the same handover [pendingExport] gets, for the same reason.
    val pendingScanShare: File? = null,
    /// What the server holds about the account, for the settings screen to list. Null until the
    /// settings have been opened — no other screen reads it, so nothing fetches it before then.
    val accountData: DataExport? = null,
    /// Who runs this installation and whether a model outside it writes the Anschreiben. Fetched
    /// with [accountData]; null while it has not been.
    val legal: LegalInfo? = null,
    /// The address of the account this device is signed in to, and the ONE thing that says there
    /// is one: null while [loading] is false is the door. It is also what the settings card shows
    /// in place of the account key that used to stand there — see the door's own screen.
    val accountEmail: String? = null,
    /// The nameless profile this phone was working on before it had an account — what
    /// "Create an account" keeps and what signing in to another account leaves behind. Null on a
    /// phone that never ran a build without the door, which is every phone after the first sign-out.
    val adoptableProfileId: String? = null,
    /// The address a reset mail has just been asked for, and the one thing that moves the door's
    /// reset panel from "which address?" to "the code out of the mail". Null everywhere else; the
    /// server never says whether an account was there, so this only records that we ASKED.
    val resetRequestedFor: String? = null,
    /// The language the interface is drawn in — a tag from UI_LANGUAGES, kept on the device.
    val uiLanguage: String = "en",
    val showDinGrid: Boolean = false,
    val lastSavedFile: String? = null,
    val busy: String? = null,
)

/**
 * Whether this installation HAS an assistant.
 *
 * The writer the backend named at launch decides it, and nothing else: with no key configured the
 * rule-based writer produces a correct German Lebenslauf, but a conversation it cannot have. So the
 * bar item stays and the screen says so — see the assistant's own screen — and the Übersicht goes
 * on offering the form as the first step rather than something that is not there.
 */
val AppState.hasAssistant: Boolean get() = writer == "model"

/**
 * Whether the application the user just opened is still on its way.
 *
 * [AppViewModel.openApplication] clears the application BEFORE it fetches the new one, so that one
 * employer's Anschreiben is never shown under another's name — which leaves the same null standing
 * for "not fetched yet" as for "there is none". This is the difference between the two, and both the
 * Bewerbung screen and the rail above it read it here: without it the last screen before sending
 * spent the fetch telling a user who had finished the Abgleich to go back and do it.
 *
 * The fetch names itself in [AppState.busy] and openApplication is the only call that carries
 * [AppViewModel.APPLICATION], so this wait ends exactly when that one does — a failed fetch
 * included, because launch() clears the name whichever way the call went. What the failed one
 * leaves standing is [AppState.unfetchedApplication]: a third thing the same null can mean, and
 * the only one of the three the user can do something about.
 */
val AppState.isOpeningApplication: Boolean
    get() = application == null && busy == AppViewModel.APPLICATION

/**
 * Whether the Anschreiben the user just asked for is being written right now.
 *
 * The fourth thing the same null means, and the one the user waits longest for: the Abgleich's
 * "Anschreiben schreiben" navigates to this screen in the same onClick that starts the call, so the
 * letter is asked for and does not exist yet. Where a model writes it that is many seconds, and the
 * screen spent them drawing its empty state — "Noch kein Anschreiben. Führen Sie zuerst den Abgleich
 * durch." — under a rail marking that very Abgleich done. One frame, two opposite statements, and
 * the one the user is left reading sends them back to a step they had just finished.
 *
 * [AppViewModel.LETTER] names the write, and the application being null is what tells the two calls
 * that carry that name apart: `regenerateLetter` replaces a letter that is on the screen, so this
 * wait is `generateLetter`'s alone — the one where there is nothing to draw instead.
 */
val AppState.isWritingLetter: Boolean
    get() = application == null && busy == AppViewModel.LETTER

/**
 * What the app hands to a mail app: the file to attach, the Betreffzeile as the subject and the
 * covering note that goes in the body.
 *
 * Subject and body stay GERMAN in every interface language, for the same reason the Anschreiben
 * itself does — the person who opens this mail is a German employer, not the applicant.
 *
 * [recipient] is empty when the posting named no address; the mail app then asks for one, as it did
 * before there was anything to fill in.
 */
data class EmailDraft(val file: File, val recipient: String, val subject: String, val body: String)

/**
 * The whole client state in one place.
 *
 * Bewerbo is a single linear workflow — profile, posting, match, letter, export — and each step
 * reads what the one before it produced. Splitting that across five view models would mean five
 * copies of the same profile and a bug the first time one of them went stale.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    // The session goes to the server on every call, and it is read out of the preferences each
    // time rather than held here: signing out removes it, and the next call must then be as
    // anonymous as a call from a phone that never signed in. See [sessionPrefs].
    private val api = BewerboApi { sessionPrefs().getString("authToken", null) }
    private val _state = MutableStateFlow(AppState())

    /// How many previews have been asked for. Counts the cache file of each render apart from the
    /// next one's — see [previewFile] and [refreshPreview]. Not in [AppState]: no screen reads it,
    /// and a number that changes on every chip tap would recompose the Bewerbung screen for nothing.
    private var previewRenders = 0
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        // Read straight out rather than inside bootstrap(): that one is a coroutine, and the first
        // frame would be drawn in the wrong language while it waited on the network.
        _state.update { it.copy(uiLanguage = storedUiLanguage()) }
        bootstrap()
    }

    /**
     * Opens the profile of the account this device is signed in to — or puts the door up.
     *
     * A profile used to be created here on first run and kept in shared preferences, which is what
     * made the app a record of one PHONE: a new device started empty, cleared app data took the
     * Lebenslauf with it, and whoever picked the phone up opened somebody else's application.
     * Nothing is created here any more. The token is what this device holds, the server says whose
     * it is, and a device with no token gets the door.
     */
    private fun bootstrap() = launch("start") {
        val health = runCatching { api.health() }.getOrNull()
        _state.update { it.copy(writer = health?.writer ?: "regeln") }

        val token = sessionPrefs().getString("authToken", null)
        if (token != null) {
            val session = try {
                api.session()
            } catch (_: ApiFailure) {
                // The server ANSWERED and would not have it: the session was ended on another
                // device, or the account is gone. Only THAT signs the user out — a server that
                // could not be reached at all must not take the session with it, so a failure
                // that is not a refusal goes on out of here and lands in the snackbar.
                sessionPrefs().edit().remove("authToken").apply()
                null
            }

            if (session != null) {
                enter(session)
                return@launch
            }
        }

        _state.update {
            it.copy(loading = false, accountEmail = null, adoptableProfileId = adoptableProfile())
        }
    }

    /**
     * Arrives on an account: what a register, a sign-in and a launch with a valid token all end in.
     *
     * The posting and the letter written down on this device belong to the profile it was on. Where
     * the account brought that same profile with it — "Create an account" keeping the Lebenslauf
     * this phone already held — they are still the user's own work and are picked back up. Where it
     * did not, they name records of another account and are dropped: the account's own work comes
     * off the server through the Übersicht instead.
     */
    private suspend fun enter(session: Session) {
        val carried = prefs().getString("profileId", null) == session.profileId

        sessionPrefs().edit().putString("authToken", session.token).apply()

        val edit = prefs().edit().putString("profileId", session.profileId)
        if (!carried) edit.remove("postingId").remove("applicationId")
        edit.apply()

        val profile = api.profile(session.profileId)
        _state.update {
            AppState(
                uiLanguage = it.uiLanguage, writer = it.writer, loading = false,
                accountEmail = session.email, profile = profile,
            )
        }
        refreshDerived()
        if (carried) restoreWorkInProgress()
    }

    /**
     * The nameless profile a build without the door left on this phone — the one the door offers to
     * keep.
     *
     * Confirmed against the server rather than read out of the preferences and believed: that file
     * outlives the state it was written in, and offering to keep something this phone may not keep
     * is a promise the register call cannot hold.
     *
     * Asking whether the profile EXISTS was not enough. The id lives in bewerbo.xml, which a Google
     * backup takes, while the token beside it lives in bewerbo-session.xml, which backup_rules.xml
     * deliberately excludes — so a restored or transferred phone holds the id of a profile that is
     * still there and already has an owner. The door offered to keep it, the server silently
     * refused, and the user landed on an empty Lebenslauf having read the opposite. Only the server
     * knows whether a profile is spoken for, so only the server can answer this.
     */
    private suspend fun adoptableProfile(): String? {
        val stored = prefs().getString("profileId", null) ?: return null
        val adoptable = runCatching { api.adoptable(stored) }.getOrNull()?.adoptable ?: false
        return if (adoptable) stored else null
    }

    /**
     * Brings back the posting, the Abgleich and the letter the user was last working on.
     *
     * Only the profile used to survive a restart, so a user interrupted between pasting a posting
     * and sending the Mappe came back to an empty Stellenanzeige screen and "Noch kein Anschreiben"
     * — while both records sat on the server the whole time. Losing that silently is worse than
     * losing it loudly: there is nothing on screen to suggest the work still exists.
     */
    private suspend fun restoreWorkInProgress() {
        val posting = prefs().getString("postingId", null)
            ?.let { runCatching { api.posting(it) }.getOrNull() }
        val application = prefs().getString("applicationId", null)
            ?.let { runCatching { api.application(it) }.getOrNull() }

        // A letter written against a posting that is no longer the current one would describe a
        // job the user is not looking at. Keep the pair or neither.
        val pair = if (application != null && application.postingId == posting?.id) {
            application
        } else null

        // The Abgleich is derived rather than stored, so a restart left the middle step of the flow
        // empty underneath the letter that came out of it — the rail then had the Bewerbung open
        // with the Abgleich behind it unreachable. It comes back only ALONGSIDE the letter, as it
        // does in openApplication(): where there is a letter the Abgleich behind it happened, and
        // where there is only a pasted posting the user has not made one yet.
        val match = pair?.let { runCatching { api.match(it.postingId) }.getOrNull() }

        _state.update { it.copy(posting = posting, match = match, application = pair) }
        if (pair != null) runChecks(pair.id)
    }

    private fun prefs() = getApplication<Application>()
        .getSharedPreferences("bewerbo", android.content.Context.MODE_PRIVATE)

    /**
     * The one preference that must never leave this phone: the token that proves the device is
     * signed in.
     *
     * A file of its own for exactly that reason — a backup rule excludes a FILE and not a key, and
     * allowBackup is on because the interface language is worth restoring. Without this the token
     * rode into a Google backup and whoever restored it was signed in as somebody else. See
     * res/xml/backup_rules.xml, which names this file.
     */
    private fun sessionPrefs() = getApplication<Application>()
        .getSharedPreferences("bewerbo-session", android.content.Context.MODE_PRIVATE)

    /**
     * What this installation calls itself when it files a document, so that the Mappe can tell a
     * document THIS phone filed from one that came off the user's other one.
     *
     * A random id written once and read from then on. It says nothing about the phone and nothing
     * about the user — it only has to differ from the next installation's, which is the whole of
     * what "another device" means here.
     *
     * In [sessionPrefs] and not in [prefs] for the reason that file exists: allowBackup is on, and
     * an id restored onto a new phone out of a Google backup would make that phone answer "I filed
     * this" for every document the old one filed — which is the false claim this id was added to
     * end. Signing out does not clear it; the device stays the device.
     */
    val deviceId: String by lazy {
        sessionPrefs().getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            sessionPrefs().edit().putString("deviceId", it).apply()
        }
    }

    private fun refreshDerived() = launch(null) {
        val id = profileId() ?: return@launch
        val timeline = api.timeline(id)
        val overview = api.overview(id)
        _state.update { it.copy(timeline = timeline, overview = overview) }
    }

    private fun profileId(): String? = _state.value.profile?.id

    // -- interface language ------------------------------------------------------------------

    /**
     * The language the interface is drawn in — the phone's guess on first run, the user's from
     * then on.
     *
     * The interface used to follow the phone at every start with English as the fallback, which
     * handed an English app to somebody who had come to Germany for a German application and had
     * bought their phone here. The phone is still where the first guess comes from, because it is
     * usually right; it is written down once so that overruling it sticks.
     */
    private fun storedUiLanguage(): String {
        // Run through uiLanguageOrDefault rather than trusted as it stands: allowBackup is on, so
        // this file can arrive from a build that offered a language this one does not have.
        val stored = prefs().getString("uiLanguage", null)
        val tag = uiLanguageOrDefault(stored)
        if (tag != stored) prefs().edit().putString("uiLanguage", tag).apply()
        return tag
    }

    fun setUiLanguage(tag: String) {
        prefs().edit().putString("uiLanguage", tag).apply()
        _state.update { it.copy(uiLanguage = tag) }
    }

    // -- the account and its data ------------------------------------------------------------

    /**
     * What the settings screen reads: everything held about this account, and the two facts the
     * legal pages cannot be written without.
     *
     * Fetched when that screen opens rather than at start-up. Nothing else in the app reads either
     * of them, and a user who never opens the settings should not pay for two calls at every launch.
     */
    fun loadSettings() = launch("settings") {
        val id = profileId() ?: return@launch
        _state.update { it.copy(accountData = api.accountData(id), legal = api.legal()) }
    }

    /**
     * Who runs this installation and whether a model outside it writes the Anschreiben — what the
     * legal pages cannot be written without.
     *
     * Its own call, and not a corner of [loadSettings], because the pages are reachable from the
     * DOOR as well: somebody deciding whether to create an account here has to be able to read the
     * privacy notice first, and there is no profile to read the categories of yet.
     */
    fun loadLegal() = launch("legal") {
        _state.update { it.copy(legal = api.legal()) }
    }

    /**
     * Writes the copy of everything held about the account — Art. 15 and Art. 20 DSGVO.
     *
     * It lands where a produced Lebenslauf lands, through the same helper, and is then handed on
     * through [pendingExport]. Saving alone was not a copy the user HAS: that folder is the app's
     * own private storage, mode 0600 under its uid, so the message naming the file was the last
     * they could ever see of it. The bytes are the server's answer as it arrived rather than
     * anything rebuilt here: a copy that has been through this app twice is not evidence of what
     * the server holds.
     */
    fun exportAccountData() = launch("export") {
        val id = profileId() ?: return@launch
        val file = api.accountDataFile(id, getApplication<Application>().documentFile(ACCOUNT_DATA_FILE))
        _state.update { it.copy(lastSavedFile = describe(file), pendingExport = file) }
    }

    /// Cleared once the screen has handed the copy to a chooser, so coming back to the settings
    /// does not open it a second time. Same reason as [emailHandled].
    fun exportHandled() = _state.update { it.copy(pendingExport = null) }

    /**
     * Erases the account and everything held under it — Art. 17 DSGVO — and leaves the user at the
     * door.
     *
     * An empty account used to be opened straight afterwards, because there was no app without one.
     * There is a door now, and it is the honest place to land: the account the user asked to have
     * erased is gone, and silently putting them into a new nameless one would hide exactly that.
     * The interface language is deliberately kept — it is a preference of this device, not
     * something held about the person.
     */
    fun deleteAccount() = launch("account") {
        val id = profileId() ?: return@launch
        api.deleteAccount(id)
        forgetTheAccountOnThisDevice()
    }

    // -- the door ----------------------------------------------------------------------------

    /**
     * Creates an account and enters it, keeping the Lebenslauf this phone is already holding.
     *
     * The profile the door offered to keep is named in the request rather than decided here: the
     * server binds it only while it belongs to no account, which is what keeps the offer from
     * becoming a way to claim somebody else's.
     */
    fun register(email: String, password: String) = launch("door") {
        enter(api.register(RegisterRequest(email.trim(), password, _state.value.adoptableProfileId)))
    }

    /// Comes back to an account that exists. Everything the user wrote is in it; nothing of it was
    /// ever on this phone.
    fun signIn(email: String, password: String) = launch("door") {
        enter(api.signIn(Credentials(email.trim(), password)))
    }

    /**
     * Asks for the mail that carries a reset code.
     *
     * [AppState.resetRequestedFor] is set from what the user typed and NOT from anything the server
     * said, because the server deliberately says nothing: it answers the same way for an address it
     * has never seen as for one it has. So the panel moves on to the code either way, and the user
     * reads the same sentence — which is the whole of the protection, and the reason the address is
     * carried forward rather than asked for a second time.
     *
     * The interface language goes with the request. It is the one thing the server is told about
     * it, and only because a mail has no screen behind it to write the sentence; see
     * PasswordResetMail on the server.
     */
    fun requestPasswordReset(email: String) = launch("door") {
        val address = email.trim()
        api.forgotPassword(ForgotPasswordRequest(address, _state.value.uiLanguage))
        _state.update { it.copy(resetRequestedFor = address) }
    }

    /// Spends the code on a new password and enters the account it belongs to — through [enter],
    /// exactly as a sign-in does, so the user lands on their own profile with everything in it.
    fun resetPassword(code: String, password: String) = launch("door") {
        val address = _state.value.resetRequestedFor ?: return@launch
        enter(api.resetPassword(ResetPasswordRequest(address, code.trim(), password)))
    }

    /// Leaves the reset behind — the panel closing, or the user going back to ask for another code.
    /// The refusal goes with it: it explained a form that is no longer on screen.
    fun forgetPasswordReset() =
        _state.update { it.copy(resetRequestedFor = null, error = null) }

    /**
     * Leaves the account, and leaves nothing of it on the phone.
     *
     * The server is told first so the session is genuinely revoked rather than merely forgotten —
     * but a server that cannot be reached must not keep a user signed in on a phone they are
     * handing over, so the local half runs either way. That is the whole of the promise this card
     * makes: what stays is in the account, what was on the device is gone.
     */
    fun signOut() = launch("door") {
        if (sessionPrefs().getString("authToken", null) != null) {
            runCatching { api.signOut() }
        }
        forgetTheAccountOnThisDevice()
    }

    /**
     * Everything of one account that this device had: the four preferences it wrote, the files it
     * produced, and the whole of the state on screen.
     *
     * One place for it, because signing out and erasing the account both have to leave exactly
     * nothing and a second copy of this list is a second chance to forget one of them. The
     * interface language survives, and only that — the app has to stay readable to whoever picks
     * the phone up next.
     */
    private fun forgetTheAccountOnThisDevice() {
        sessionPrefs().edit().remove("authToken").apply()
        prefs().edit()
            .remove("profileId")
            .remove("postingId")
            .remove("applicationId")
            .apply()

        // The Bewerbungsmappe, the Lebenslauf and the copy of the account data all land in the one
        // folder documentFile() writes into; the previews are rendered into the cache beside it.
        val app = getApplication<Application>()
        app.documentsDir().deleteRecursively()
        app.previewDir().deleteRecursively()

        _state.update { AppState(uiLanguage = it.uiLanguage, writer = it.writer, loading = false) }
    }

    // -- profile ---------------------------------------------------------------------------

    fun savePerson(person: Person) = launch("person") {
        val id = profileId() ?: return@launch
        _state.update { it.copy(profile = api.savePerson(id, person)) }
        refreshDerived()
    }

    fun saveExperience(entries: List<Experience>) = launch("experience") {
        val id = profileId() ?: return@launch
        _state.update { it.copy(profile = api.saveExperience(id, entries)) }
        refreshDerived()
    }

    fun saveEducation(entries: List<Education>) = launch("education") {
        val id = profileId() ?: return@launch
        _state.update { it.copy(profile = api.saveEducation(id, entries)) }
        refreshDerived()
    }

    fun saveLanguages(entries: List<LanguageSkill>) = launch("languages") {
        val id = profileId() ?: return@launch
        _state.update { it.copy(profile = api.saveLanguages(id, entries)) }
        refreshDerived()
    }

    fun explainGap(gap: Gap, reason: String) = launch("gap") {
        val id = profileId() ?: return@launch
        // The wording the user read under the input is what gets stored. The server only falls back
        // to its own suggestion when the client sends none, and deriving it a second time could
        // hand the Lebenslauf a sentence other than the one that was approved.
        api.explainGap(id, GapUpdate(gap.from, gap.to, reason, state.value.gapWording[gap.from]))
        refreshDerived()
    }

    /**
     * The German wording for what the user is typing about a gap, so they read the sentence that
     * will stand in the Lebenslauf before any document is produced.
     *
     * Deliberately NOT routed through [launch]: this runs while the user types, and that helper
     * drives the global busy flag and clears the error snackbar — a preview would flicker the one
     * and swallow the other. A suggestion that could not be fetched is simply not shown; it is not
     * a failure the user has anything to do about.
     */
    fun previewGapWording(gap: Gap, reason: String) = viewModelScope.launch {
        if (reason.isBlank()) {
            _state.update { it.copy(gapWording = it.gapWording - gap.from) }
            return@launch
        }
        val german = runCatching { api.gapWording(reason).german }.getOrNull() ?: return@launch
        _state.update { it.copy(gapWording = it.gapWording + (gap.from to german)) }
    }

    /**
     * The duties of one position written as results, for the user to read beside their own words.
     *
     * Routed through [launch], unlike [previewGapWording]: this one is asked for by a tap rather
     * than by typing, so a call that failed is a failure the user is waiting on and belongs in the
     * snackbar. Nothing is stored — [acceptDutyOutcomes] is what puts a rewrite into the profile.
     */
    fun previewDutyOutcomes(entry: Experience) = launch("duty-outcomes") {
        val id = entry.id ?: return@launch
        // Every line that HAS a rewrite starts chosen: asking for the rewrite is the user saying
        // they want it. Turning one back off is the correction, not the other way round.
        val lines = api.dutyOutcomes(entry.duties).lines
            .map { DutyChoice(it.original, it.outcome, it.outcome.isNotBlank()) }
        _state.update { it.copy(dutyOutcomes = it.dutyOutcomes + (id to lines)) }
    }

    /**
     * Which of the two readings of ONE duty line is to reach the document.
     *
     * Nothing is stored here — the choice only moves in the comparison the card is showing, and
     * [acceptDutyOutcomes] is still what writes it. Kept on [AppState] rather than in the
     * composable's own `remember`, because the card is an item of a LazyColumn and a choice made at
     * the top of a long list would be dropped by scrolling past it.
     */
    fun chooseDutyOutcome(entry: Experience, line: Int, taken: Boolean) {
        val entryId = entry.id ?: return
        val lines = state.value.dutyOutcomes[entryId] ?: return
        val chosen = lines.mapIndexed { i, choice -> if (i == line) choice.copy(taken = taken) else choice }
        _state.update { it.copy(dutyOutcomes = it.dutyOutcomes + (entryId to chosen)) }
    }

    /**
     * What the user chose, saved as the duties of that position.
     *
     * Line by line: a rewrite they turned off, and a line that had none to begin with, both keep the
     * words they were typed with. The offer is to replace what the user accepted, never to drop what
     * they did not.
     */
    fun acceptDutyOutcomes(entry: Experience, all: List<Experience>) = launch("experience") {
        val id = profileId() ?: return@launch
        val entryId = entry.id ?: return@launch
        val lines = state.value.dutyOutcomes[entryId] ?: return@launch
        val duties = lines.joinToString("\n") { it.chosen }

        val saved = api.saveExperience(
            id,
            all.map { other -> if (other.id == entryId) other.copy(duties = duties) else other },
        )
        _state.update { it.copy(profile = saved, dutyOutcomes = it.dutyOutcomes - entryId) }
        refreshDerived()
    }

    /// The user kept their own words. Only the comparison goes away; nothing was stored to undo.
    fun discardDutyOutcomes(entry: Experience) {
        val entryId = entry.id ?: return
        _state.update { it.copy(dutyOutcomes = it.dutyOutcomes - entryId) }
    }

    /// The anabin entries that could match ONE qualification, for the user to pick from. Stored
    /// against that entry, the way [previewDutyOutcomes] stores a rewrite against its position.
    fun lookUpDegrees(entry: Education) = launch("degrees") {
        val entryId = entry.id ?: return@launch
        val offers = api.degrees(entry.country.ifBlank { null })
        _state.update { it.copy(degrees = it.degrees + (entryId to offers)) }
    }

    /**
     * The equivalence the user picked out of the offers, stored on that qualification.
     *
     * The offers go with it, as in [acceptDutyOutcomes]: they were the answer to "what could this
     * degree be", and once it is answered, leaving them under the card invites a second and
     * contradicting confirmation on a claim that is worse wrong than absent.
     */
    fun confirmEquivalence(entry: Education, all: List<Education>, degree: DegreeEquivalence) =
        launch("education") {
            val id = profileId() ?: return@launch
            val entryId = entry.id ?: return@launch

            val saved = api.saveEducation(
                id,
                all.map { other ->
                    if (other.id == entryId) {
                        other.copy(
                            germanEquivalent = degree.germanEquivalent,
                            anabinAssessment = degree.anabinRating,
                            equivalenceConfirmed = true,
                        )
                    } else other
                },
            )
            _state.update { it.copy(profile = saved, degrees = it.degrees - entryId) }
            refreshDerived()
        }

    fun generateCv() = launch("cv") { saveCv() }

    /**
     * The Lebenslauf from the conversation: produced, saved, and handed out to be kept.
     *
     * The one further step the first minutes end in. It is the same document [generateCv] makes —
     * one route, one file name — and what it adds is [pendingCv]: a file in the app's own folder is
     * mode 0600 under the app's uid, so a message naming it is the last the user ever sees of it.
     * The assistant hands it to a chooser, which is where "open it outside Bewerbo" happens.
     *
     * Nothing is held back when the profile is short of something. What the document lacks is named
     * beside the button from [Overview.cvMissing], and a Lebenslauf with three of four sections
     * filled is worth more to the user than a refusal.
     */
    fun exportCv() = launch("cv") {
        // The file first, the state second. [saveCv] writes to the state itself, and a writer
        // called from INSIDE an update lambda runs twice: the lambda is retried when the value
        // changed underneath it, which is exactly what its own inner write does — one tap asked
        // the server for the Lebenslauf twice.
        val file = saveCv() ?: return@launch
        _state.update { it.copy(pendingCv = file) }
    }

    /// Cleared once the assistant has handed the Lebenslauf to a chooser, so coming back to the
    /// screen does not open it a second time. Same reason as [exportHandled].
    fun cvHandled() = _state.update { it.copy(pendingCv = null) }

    /// The Lebenslauf as a file, wherever it was asked for. Null when there is no profile to render
    /// — the caller then has nothing to hand on either.
    private suspend fun saveCv(): File? {
        val id = profileId() ?: return null
        val file = api.lebenslaufPdf(id, getApplication<Application>().documentFile(CV_FILE))
        _state.update { it.copy(lastSavedFile = describe(file)) }
        return file
    }

    // -- posting ---------------------------------------------------------------------------

    /**
     * The advert text the user has brought in, as they are editing it.
     *
     * The three ways in — pasting, a link, a picture — all end here, and the screen draws this one
     * field whichever of them filled it. That is what makes "the text that was read can be
     * corrected before it is used" a property of the screen rather than of each way in separately.
     */
    fun setPostingDraft(text: String) = _state.update { it.copy(postingDraft = text) }

    /**
     * Fetches the advert behind a link into the draft.
     *
     * The text is NOT parsed here. A page carries a navigation menu and a cookie notice beside the
     * advert, and handing that straight to the parser would read fields out of the furniture; the
     * user sees what came back and cuts it down first.
     */
    fun readPostingLink(url: String) = launch("link") {
        _state.update { it.copy(postingDraft = api.readLink(ReadLinkRequest(url)).text) }
    }

    /**
     * Reads the advert in a photo or a screenshot into the draft.
     *
     * On the device — see [readTextFromImage]. A picture with no text the recogniser could make out
     * is not an error the server knows about, so the kind is raised here, in the same shape a
     * refused call arrives in.
     */
    fun readPostingImage(uri: android.net.Uri) = launch("photo") {
        val text = readTextFromImage(getApplication(), uri)
        if (text.isBlank()) throw ApiFailure(PHOTO_UNREADABLE, "")
        _state.update { it.copy(postingDraft = text) }
    }

    // -- the assistant -----------------------------------------------------------------------

    /// What is in the assistant's composer. Held on the state rather than in the composable for the
    /// reason [setPostingDraft] is.
    fun setAssistantDraft(text: String) = _state.update { it.copy(assistantDraft = text) }

    /**
     * Sends what is in the composer and puts the answer under it.
     *
     * The user's own turn is appended and the composer cleared BEFORE the call, which is the one
     * place in this app that shows something before the server has confirmed it: a chat that hides
     * what was just sent until the answer arrives has nothing for the "preparing an answer" row to
     * sit under, and the message is the user's own words rather than a claim about stored data. A
     * call that fails leaves the turn standing — the snackbar says why, and sending again is the
     * only thing to do about it either way.
     *
     * The whole conversation goes with every turn: the server stores none of it.
     */
    fun askAssistant() = launch(ASSISTANT) {
        val said = _state.value.assistantDraft.trim()
        if (said.isEmpty()) return@launch

        val conversation = _state.value.assistant + AssistantTurn(fromUser = true, text = said)
        _state.update { it.copy(assistant = conversation, assistantDraft = "") }

        val answer = api.assistantTurn(
            AssistantTurnRequest(
                uiLanguage = _state.value.uiLanguage,
                messages = conversation.map { AssistantMessage(it.fromUser, it.text) },
            ),
        )
        _state.update {
            it.copy(
                assistant = it.assistant + AssistantTurn(
                    false, answer.reply, answer.missing,
                    // Every proposal arrives undecided. The user reads the German beside their own
                    // words and answers it one by one — nothing is taken because it was offered.
                    answer.proposals.map { proposal -> AssistantProposalChoice(proposal) },
                    // The header of the Lebenslauf, where the turn named anything the profile does
                    // not hold yet. Undecided in the same way, and null where there is nothing.
                    answer.person?.let { person -> AssistantPersonChoice(person) },
                ),
            )
        }
    }

    /**
     * Writes ONE proposal into the profile, exactly as the card showed it.
     *
     * Through the section's own PATCH route, as every write in this app goes: the assistant
     * produces content and never a storage effect, so there is one write path and the profile
     * cannot be filled from two directions. The title and the detail travel unchanged — what the
     * user read before accepting is what the profile then carries, and nothing re-derives it.
     *
     * The entry is appended to what the section already holds. A proposal is an addition to a life
     * that is being described, never a correction of one already on file; replacing an entry is
     * the form's job, and it is one tap away.
     */
    fun acceptProposal(turn: Int, index: Int) = launch(PROPOSAL) {
        val id = profileId() ?: return@launch
        val profile = state.value.profile ?: return@launch
        val choice = state.value.assistant.getOrNull(turn)?.proposals?.getOrNull(index)
            ?: return@launch
        if (choice.decision != ProposalDecision.Pending) return@launch
        val proposal = choice.proposal

        val saved = when (proposal.kind) {
            EXPERIENCE_SECTION -> api.saveExperience(
                id,
                profile.experience + Experience(
                    position = proposal.title, employer = proposal.detail,
                    from = proposal.from, to = proposal.to.ifBlank { null },
                ),
            )
            EDUCATION_SECTION -> api.saveEducation(
                id,
                profile.education + Education(
                    degree = proposal.title, institution = proposal.detail,
                    from = proposal.from, to = proposal.to.ifBlank { null },
                ),
            )
            LANGUAGES_SECTION -> api.saveLanguages(
                id,
                profile.languages + LanguageSkill(language = proposal.title, level = proposal.detail),
            )
            // A section this build does not know is one the server should not have sent; leaving
            // the card pending is the honest answer, and the next build will draw it.
            else -> return@launch
        }
        _state.update {
            it.copy(
                profile = saved,
                assistant = it.assistant.decided(turn, index, ProposalDecision.Accepted),
            )
        }
        refreshDerived()
    }

    /**
     * The user refused one proposal.
     *
     * Nothing was stored, so there is nothing to undo — the same bargain [discardDutyOutcomes]
     * strikes. The card stays and says the German was left out, rather than disappearing: the
     * user's own words are what still stand, and a card that vanished would read as if something
     * had happened to them.
     */
    fun refuseProposal(turn: Int, index: Int) = _state.update {
        it.copy(assistant = it.assistant.decided(turn, index, ProposalDecision.Kept))
    }

    /**
     * Writes the person's details the conversation named into the profile — the header the Lebenslauf
     * needs before it is a document anybody can send.
     *
     * Through [api.savePerson], the form's own route, as an accepted proposal goes through the
     * section routes. Only the fields that arrived are set, and each only where the profile has
     * nothing: the server already took out everything it holds, and this is the second half of the
     * same rule, because the profile may have gained a field while the card stood on screen.
     * Accepting is an ADDITION here as everywhere else — correcting a field that is filled in is
     * the form's job.
     */
    fun acceptPerson(turn: Int) = launch(PROPOSAL) {
        val id = profileId() ?: return@launch
        val choice = state.value.assistant.getOrNull(turn)?.person ?: return@launch
        if (choice.decision != ProposalDecision.Pending) return@launch
        val current = state.value.profile?.person ?: return@launch
        val read = choice.person

        val saved = api.savePerson(
            id,
            current.copy(
                firstName = current.firstName.ifBlank { read.firstName },
                lastName = current.lastName.ifBlank { read.lastName },
                street = current.street.ifBlank { read.street },
                postalCode = current.postalCode.ifBlank { read.postalCode },
                city = current.city.ifBlank { read.city },
                phone = current.phone.ifBlank { read.phone },
                email = current.email.ifBlank { read.email },
            ),
        )
        _state.update {
            it.copy(
                profile = saved,
                assistant = it.assistant.personDecided(turn, ProposalDecision.Accepted),
            )
        }
        refreshDerived()
    }

    /// The user left the details out. Nothing was stored, so nothing is undone — the same bargain
    /// [refuseProposal] strikes, and the card stays and says so for the same reason.
    fun refusePerson(turn: Int) = _state.update {
        it.copy(assistant = it.assistant.personDecided(turn, ProposalDecision.Kept))
    }

    /**
     * The conversation with ONE proposal's decision changed.
     *
     * By position and not by an id, unlike [dutyOutcomes]: a proposal has no id to key a map with —
     * it was never stored anywhere — and the turns are the screen's own state, so the decision
     * belongs beside the proposal it is about. Positions are stable here for the same reason: a
     * turn is only ever appended, and none is ever removed.
     */
    /// The conversation with the person card of ONE turn decided. By position, as [decided] is, and
    /// separate from it because there is at most one of these per turn and it has no index.
    private fun List<AssistantTurn>.personDecided(turn: Int, decision: ProposalDecision) =
        mapIndexed { t, one ->
            if (t != turn) one else one.copy(person = one.person?.copy(decision = decision))
        }

    private fun List<AssistantTurn>.decided(turn: Int, index: Int, decision: ProposalDecision) =
        mapIndexed { t, one ->
            if (t != turn) {
                one
            } else {
                one.copy(
                    proposals = one.proposals.mapIndexed { i, choice ->
                        if (i == index) choice.copy(decision = decision) else choice
                    },
                )
            }
        }

    /**
     * Reads an old Lebenslauf out of a photo into the composer.
     *
     * The same on-device reading a photographed advert gets — see [readPostingImage] — and it lands
     * in the composer rather than being sent, because a recogniser reading a document at an angle
     * comes back with plausible lines in the wrong order and the user is the only one who can see it.
     */
    fun readAssistantImage(uri: android.net.Uri) = launch("photo") {
        val text = readTextFromImage(getApplication(), uri)
        if (text.isBlank()) throw ApiFailure(PHOTO_UNREADABLE, "")
        _state.update { it.copy(assistantDraft = text) }
    }

    fun parsePosting(text: String) = launch("posting") {
        val id = profileId() ?: return@launch
        val posting = api.parsePosting(ParsePostingRequest(id, text))
        // A new posting invalidates the match and the letter that were written against the old one.
        // The draft goes with them: it has been read now, and it is the source text of the posting
        // on screen — keeping a second copy of it is how "Paste a different posting" would come
        // back with the last advert already in the field. So does a fetch that failed: this is a
        // new application with no letter yet, and that is the empty state and not a failure.
        _state.update { it.copy(posting = posting, postingDraft = "", match = null, application = null, unfetchedApplication = null, review = null, ats = null, previewPages = emptyList()) }
        prefs().edit().putString("postingId", posting.id).remove("applicationId").apply()
    }

    /**
     * Drops the posting so a different one can be pasted. Purely client-side: asking the server to
     * parse an empty string just earns a 400 and leaves the old posting on screen, which is what
     * "Paste a different posting" used to do.
     */
    fun clearPosting() {
        prefs().edit().remove("postingId").remove("applicationId").apply()
        _state.update {
            it.copy(posting = null, postingDraft = "", match = null, application = null, unfetchedApplication = null, review = null, ats = null, previewPages = emptyList())
        }
    }

    /**
     * Writes every field the correction dialog changed, in one pass.
     *
     * The Stellenanzeige corrects all five fields through one dialog, so each field cannot be its
     * own launch(): five coroutines patching the same posting would leave whichever answer came
     * back last on screen, which is not necessarily the one that had seen all five patches.
     */
    fun correctFields(values: Map<String, String>) = launch("field") {
        val posting = _state.value.posting ?: return@launch
        var corrected = posting
        values.forEach { (key, value) ->
            corrected = api.correctField(posting.id, CorrectFieldRequest(key, value))
        }
        _state.update { it.copy(posting = corrected) }
    }

    fun setEmployerType(type: String) = launch("employer") {
        val posting = _state.value.posting ?: return@launch
        _state.update { it.copy(posting = api.setEmployerType(posting.id, type)) }
    }

    fun matchRequirements() = launch("match") {
        val posting = _state.value.posting ?: return@launch
        _state.update { it.copy(match = api.match(posting.id)) }
    }

    /**
     * Files the Nachweis an open requirement is waiting for, from the Abgleich itself.
     *
     * Two records, one action: the document goes into the Mappe so the Anlagenverzeichnis can name
     * it, AND the language's `certificateOnFile` is set — because that flag, not the presence of a
     * document, is what the Abgleich, the Übersicht and the Anschreiben read. Writing only one of
     * them is what the old "Nachweis hochladen" chip did by sending the user to the Mappe: the
     * document was filed, the flag stayed false, and the requirement stayed open for ever.
     *
     * The Abgleich is re-run at the end, so the count on screen is the one the profile now holds.
     */
    fun fileCertificate(language: String, document: StoredDocument) = launch("document") {
        val id = profileId() ?: return@launch
        val languages = _state.value.profile?.languages.orEmpty()

        fileDocument(id, document)
        val profile = api.saveLanguages(
            id,
            languages.map { if (it.language == language) it.copy(certificateOnFile = true) else it },
        )
        _state.update { it.copy(profile = profile) }

        _state.value.posting?.let { posting ->
            _state.update { it.copy(match = api.match(posting.id)) }
        }
        refreshDerived()
    }

    // -- application -----------------------------------------------------------------------

    /**
     * Writes the Anschreiben, which is the moment an application starts existing.
     *
     * The Übersicht is refreshed at the end, as it is after every other write in here: this call
     * CREATES the application record, so without it the one screen that lists the applications kept
     * the list it had read before there were any. A user who had just written a letter came back to
     * "Noch keine Bewerbung begonnen" over a button offering to continue it, and the row that leads
     * back into an unfinished application only appeared after a restart.
     */
    fun generateLetter(tone: String) = launch(LETTER) {
        val id = profileId() ?: return@launch
        val posting = _state.value.posting ?: return@launch
        val application = api.createApplication(CreateApplicationRequest(id, posting.id, tone))
        _state.update { it.copy(application = application, review = null, ats = null, previewPages = emptyList()) }
        prefs().edit().putString("applicationId", application.id).apply()
        runChecks(application.id)
        refreshDerived()
    }

    fun regenerateLetter(tone: String) = launch(LETTER) {
        val application = _state.value.application ?: return@launch
        val updated = api.regenerate(application.id, tone)
        _state.update { it.copy(application = updated, review = null, ats = null, previewPages = emptyList()) }
        runChecks(updated.id)
    }

    /**
     * Opens an application the user started earlier, with everything that stood around it.
     *
     * The Übersicht lists every application, and "open" has to mean the whole context — the
     * Stellenanzeige it was written against, the Abgleich, the letter and its checks — or the tab
     * the user lands on shows the previous application's letter under this employer's name. It is
     * remembered exactly where [restoreWorkInProgress] looks for it, so the next restart comes
     * back to this application rather than to whatever was open before.
     */
    fun openApplication(applicationId: String) = launch(APPLICATION) {
        // Cleared before the call, not after it: until the new one has arrived, leaving the last
        // application on screen would put one employer's Anschreiben under another's name. The
        // failure of the attempt before goes with them — this one has not failed yet.
        _state.update {
            it.copy(
                application = null, match = null, review = null, ats = null,
                previewPages = emptyList(), unfetchedApplication = null,
            )
        }

        // The three calls the screen has nothing at all without, and the one place their failure is
        // written down. [runChecks] is deliberately outside: its findings are drawn beside a letter
        // that HAS arrived, and a check that could not be run is not an application that never came.
        try {
            val application = api.application(applicationId)
            val posting = api.posting(application.postingId)
            val match = api.match(posting.id)
            _state.update { it.copy(posting = posting, match = match, application = application) }

            prefs().edit()
                .putString("postingId", posting.id)
                .putString("applicationId", application.id)
                .apply()
        } catch (failure: Throwable) {
            // WHICH application did not arrive, so the Bewerbung screen says that this is a failure
            // instead of showing the empty state, and can ask for this one again. The reason itself
            // still reaches the user through [launch], the way every other refused call does — but
            // that snackbar is gone in seconds and this screen is the last one before sending.
            _state.update { it.copy(unfetchedApplication = applicationId) }
            throw failure
        }
        runChecks(applicationId)
    }

    private suspend fun runChecks(applicationId: String) {
        val review = api.review(applicationId)
        val ats = api.atsCheck(applicationId)
        _state.update { it.copy(review = review, ats = ats) }
    }

    fun toggleDinGrid() = _state.update { it.copy(showDinGrid = !it.showDinGrid) }

    /**
     * Renders the file the chosen parts would produce, page by page, for the preview.
     *
     * Only page 1 of the Anschreiben was ever shown, drawn a second time in Compose — so the
     * Lebenslauf and the Anlagenverzeichnis, which is most of what the employer opens, could not be
     * looked at before sending. This asks the export endpoint for the REAL file and rasterises it,
     * which is also what makes the part chips mean something: take the Lebenslauf out and its pages
     * leave the preview.
     *
     * The copy goes to the cache rather than beside the saved Mappe — a preview is not something
     * the user asked to keep.
     */
    fun refreshPreview(parts: String) = launch("preview") {
        val application = _state.value.application ?: return@launch

        // Cleared BEFORE the call, not replaced after it. Pages left over from the previous
        // selection are a picture of a file the chips no longer describe, and if this call fails
        // they stay there: the screen then shows a three-page Mappe under a Lebenslauf the user
        // has just taken out, and the export button sends the other one.
        _state.update {
            it.copy(previewPages = emptyList(), previewFailure = null, previewParts = parts)
        }

        // A cache file of this render's OWN. All of them went into "vorschau.pdf", and since the
        // drawing moved off the main thread two renders can be in flight at once: the download of
        // the second truncated the file the first was still reading, so the preview the user was
        // waiting for was drawn from a file that had been cut off under it. The file is deleted
        // when this render is over, whichever way it ends.
        val file = getApplication<Application>().previewFile(++previewRenders)
        try {
            runCatching {
                api.applicationPdf(application.id, parts, file)
            }.onFailure {
                previewFailed(parts, PREVIEW_NOT_FETCHED)
            }.getOrThrow()

            // Rasterising is the one step in here that is neither a call nor a state change, and
            // this is the same misplacement [showScan] had: on the main thread the whole app stands
            // still for as long as the drawing takes. The Mappe is longer than a scan — Anschreiben,
            // Lebenslauf and Anlagenverzeichnis — and this is the last screen before the user sends
            // it, so Android offered them the "Bewerbo isn't responding" dialog there of all places.
            // Only the drawing moves off; the update below stays where a state change belongs, and
            // for as long as it takes the screen says the pages are being made.
            //
            // Caught the way [renderScanPages] catches its own: a file that cannot be read is a
            // file with no pages, and there is nothing a caller could do with the exception that
            // the empty list does not say.
            val pages = runCatching {
                withContext(Dispatchers.Default) { renderPdfPages(file) }
            }.getOrDefault(emptyList())

            // Only the fetching was ever guarded, so a Mappe that arrived and could not be drawn
            // left the screen saying the pages were being made — with no page, and with no way to
            // ask for them again: the retry button is composed under a failure. This is that
            // failure. Not raised through [launch] as well, the way the download above is: the
            // snackbar would say Bewerbo could not be reached, and it was — the file is here. The
            // screen says this one itself, and goes on saying it.
            if (pages.isEmpty()) {
                previewFailed(parts, PREVIEW_NOT_DRAWN)
                return@launch
            }

            // The chips can be changed while the pages are still being drawn, and the render that
            // was started for the selection before belongs to nothing: put into the state anyway it
            // is exactly the picture of a file the chips no longer describe that the clearing above
            // exists to prevent.
            if (_state.value.previewParts == parts) _state.update { it.copy(previewPages = pages) }
        } finally {
            file.delete()
        }
    }

    /// Says why the preview has no pages — unless the chips have moved on while this render ran, in
    /// which case a newer one is already on its way and this failure describes a selection the user
    /// has left. The same check the finished pages go through in [refreshPreview].
    private fun previewFailed(parts: String, reason: String) {
        if (_state.value.previewParts == parts) {
            _state.update { it.copy(previewFailure = reason) }
        }
    }

    fun savePdf(parts: String? = null) = launch("pdf") {
        val application = _state.value.application ?: return@launch
        val name = application.fileName.ifBlank { "Bewerbung.pdf" }
        val file = api.applicationPdf(
            application.id, parts, getApplication<Application>().documentFile(name),
        )
        _state.update { it.copy(lastSavedFile = describe(file)) }

        // The export panel shows the page count and size from the last ATS check. Adding a
        // document to the Mappe changes both — without this the panel keeps claiming the figures
        // of a file that no longer exists.
        runChecks(application.id)
    }

    /**
     * Saves the Mappe and hands it to a mail app with the letter's own Betreffzeile and a covering
     * note.
     *
     * A German application arrives by e-mail far more often than through a portal, and until now
     * the file only ever landed in the app's private folder — where the user had no way to reach
     * it. It is written exactly where [savePdf] writes it, so sending also leaves the saved copy
     * and the export panel's figures stay true.
     */
    fun sendPdfByEmail(parts: String? = null) = launch("email") {
        val application = _state.value.application ?: return@launch
        val context = getApplication<Application>()
        val name = application.fileName.ifBlank { "Bewerbung.pdf" }
        val file = api.applicationPdf(application.id, parts, context.documentFile(name))

        // Composed from what the backend already wrote rather than from new prose: the salutation
        // and the closing have been through the Floskel rules, and a body invented here would not
        // have been.
        val person = _state.value.profile?.person
        val signature = "${person?.firstName.orEmpty()} ${person?.lastName.orEmpty()}".trim()
        val body = listOf(
            application.letter.salutation.let { if (it.isBlank()) "" else "$it," },
            context.getString(R.string.application_email_body),
            listOf(application.letter.closing, signature).filter { it.isNotBlank() }.joinToString("\n"),
        ).filter { it.isNotBlank() }.joinToString("\n\n")

        _state.update {
            it.copy(
                lastSavedFile = describe(file),
                // The address the posting handed the application to, when it named one. It is an
                // extracted field like any other, so a wrong one is corrected on the Stellenanzeige
                // screen rather than here — and the mail app has the last word either way.
                // The file name is the fallback subject because it already reads
                // "Bewerbung_Vorname_Nachname_Stelle" — a blank subject line would not.
                pendingEmail = EmailDraft(
                    file,
                    _state.value.posting?.field("contactEmail")?.value.orEmpty(),
                    application.letter.subject.ifBlank { name },
                    body,
                ),
            )
        }
        runChecks(application.id)
    }

    /// Cleared once the screen has handed the draft to a mail app, so coming back to the Bewerbung
    /// screen does not open the chooser a second time.
    fun emailHandled() = _state.update { it.copy(pendingEmail = null) }

    fun setStatus(status: String) = launch("status") {
        val application = _state.value.application ?: return@launch
        api.setStatus(application.id, status)
        _state.update { it.copy(application = application.copy(status = status)) }
        refreshDerived()
    }

    // -- locker ----------------------------------------------------------------------------

    /**
     * Files a document record, stamped with the device that filed it.
     *
     * Every route that creates a document goes through here, and that is the point of it: the page
     * count rule was written into the Mappe's add card alone and the Abgleich went on overwriting
     * counts for another release, because THERE ARE TWO forms that file a document. A rule about
     * what a filed document carries belongs where both of them pass.
     *
     * The stamp is what lets the Mappe say "added on another device" about the documents that were
     * — and only about those. See [deviceId] and [StoredDocument.addedOnDevice].
     */
    private suspend fun fileDocument(profileId: String, document: StoredDocument) =
        api.addDocument(profileId, document.copy(addedOnDevice = deviceId))

    /**
     * Files a document, and the scan the user picked for it where there is one.
     *
     * The record first and the file second, because the scan belongs to a document and there is no
     * id to hang it on until the record exists. If the upload then fails the record stays, and
     * that is the right way round: the Anlagenverzeichnis can already name the Zeugnis, and the row
     * says "No copy stored" with a button to try again — beside the sentence for a document filed
     * here whose scan is still missing, and not the one about another device, which this document
     * is not.
     *
     * Which is why everything that follows from the RECORD is read back BETWEEN the two calls and
     * not after both. A refused upload — too large, too many pages, a type that is not stored —
     * throws, and the throw carries past every line after it: refreshing at the end left the user
     * with the message about the file and no row at all for the document that had just been filed.
     * That reads as "nothing was saved" while the record is on the server, is named in the exported
     * Anlagenverzeichnis, and appears in the list only after the app is started again. The refusal
     * still reaches the user; it is the only thing that is meant to be missing.
     */
    fun addDocument(document: StoredDocument) = launch("document") {
        val id = profileId() ?: return@launch
        val created = fileDocument(id, document)
        // Only the file chosen IN the add card belongs to this record, and that is the whole of
        // what [AppState.addFormScan] holds. A scan added to a row in the list while this form
        // stood open is not here to be picked up by mistake — and a scan on its way to one right
        // now is not let go by this save either.
        val picked = _state.value.addFormScan

        _state.update { it.copy(profile = api.profile(id), addFormScan = null) }
        rematch()
        refreshDerived()

        // The profile alone afterwards: the Abgleich and the Übersicht read the document, not its
        // copy, so a stored scan changes nothing either of them shows.
        if (picked != null && created.id != null) {
            api.storeScan(created.id, picked)
            _state.update { it.copy(profile = api.profile(id)) }
        }
    }

    fun deleteDocument(documentId: String) = launch("document") {
        val id = profileId() ?: return@launch
        api.deleteDocument(documentId)
        _state.update { it.copy(profile = api.profile(id)) }
        rematch()
        refreshDerived()
    }

    // -- the scan behind a document ----------------------------------------------------------

    /**
     * Whether this account has already agreed to Bewerbo holding its scans — read when the
     * Documents screen opens, because that is the only screen that offers to store one.
     *
     * A failure is swallowed on purpose, which is the exception to the rule [launch] exists for.
     * Nothing was being saved: this read only decides whether the disclosure is SHOWN, and the
     * fallback of not knowing is to show it. A snackbar for merely opening a screen would be noise
     * over a question the server answers again on the upload.
     */
    fun loadScanConsent() = viewModelScope.launch {
        val id = profileId() ?: return@launch
        runCatching { api.scanConsent(id) }
            .onSuccess { consent -> _state.update { it.copy(scanConsent = consent.agreed) } }
    }

    /**
     * Reads the file the user chose and holds it — the whole of "nothing is sent until you choose
     * below".
     *
     * The disclosure is raised HERE, at the pick, and not at the save. That is what makes it the
     * thing the user reads before the first scan leaves the phone rather than a confirmation of
     * something already under way: at this point the bytes have been read off the device and gone
     * nowhere. Once agreed, [documentId] decides what happens next — an existing row's "Add the
     * scan" uploads at once, while the add card holds the file until its Save.
     *
     * A file that is none of the three types never becomes a pick: the user is told now rather
     * than after an upload. The server checks the same thing on the same bytes.
     */
    fun pickScan(uri: android.net.Uri, documentId: String? = null) = launch("scan") {
        val picked = readScan(getApplication(), uri) ?: throw ApiFailure(SCAN_UNREADABLE, "")
        _state.update { it.copy(pickedScan = picked, pickedScanFor = documentId) }

        if (_state.value.scanConsent == true) deliverPickedScan() else openScanNotice()
    }

    /// Records that the disclosure was read and agreed to, then does what was waiting on it.
    fun agreeToScans() = launch("scan") {
        val id = profileId() ?: return@launch
        val consent = api.agreeToScans(id)
        _state.update { it.copy(scanConsent = consent.agreed, scanNoticeOpen = false) }
        deliverPickedScan()
    }

    /// The other button under the disclosure. The picked file is dropped, and it never left the
    /// phone — which is what the sentence above the two buttons says.
    fun declineScans() =
        _state.update { it.copy(scanNoticeOpen = false, pickedScan = null, pickedScanFor = null) }

    /// Drops a file chosen in the add card before it was saved. Only that one: a scan on its way
    /// to a row in the list is not the add card's to cancel.
    fun discardPickedScan() = _state.update { it.copy(addFormScan = null) }

    private fun openScanNotice() = _state.update { it.copy(scanNoticeOpen = true) }

    /**
     * Sends the held file to the document it was picked for, or hands it to the add card when it
     * names none. Either way [AppState.pickedScan] is empty afterwards: it carries a pick only
     * while it is on its way.
     *
     * A pick made in the add card has no document yet, so it waits in [AppState.addFormScan] until
     * [addDocument] has a record to hang it on. It waits THERE and not where it arrived, because
     * the list above the open form keeps working: the next scan added to a row in it comes through
     * [AppState.pickedScan] and would take the form's file with it.
     *
     * The file is let go whichever way the upload ends, for the same reason [addDocument] refreshes
     * early: a refused one that stayed held was still the pick when the user next opened the add
     * card, so that card showed a file they had not chosen there and its Save offered the server
     * the very file it had just refused. A second attempt starts from a fresh choice.
     *
     * When the document being uploaded to is the one OPEN in the viewer — which is what "Ersetzen"
     * is — that window is brought along: it says the new copy is on its way while it is, and then
     * shows the record the upload returned, pages, count and size. It used to keep the copy that had
     * just been replaced, with the row behind it already naming the new page count, so the only way
     * to see that anything had happened was to close the window and open it again.
     */
    private suspend fun deliverPickedScan() {
        val state = _state.value
        val picked = state.pickedScan ?: return
        val documentId = state.pickedScanFor
        if (documentId == null) {
            _state.update { it.copy(addFormScan = picked, pickedScan = null, pickedScanFor = null) }
            return
        }
        val id = profileId() ?: return
        val replacingOpenScan = state.openScan?.id == documentId

        // Said before the upload starts and not after it: the pages under the title are the copy
        // being replaced, and the seconds a photographed Zeugnis takes to go up are exactly the
        // seconds in which they are read as the current one.
        if (replacingOpenScan) _state.update { it.copy(openScanReplacing = true) }

        try {
            val stored = try {
                api.storeScan(documentId, picked)
            } finally {
                _state.update { it.copy(pickedScan = null, pickedScanFor = null) }
            }
            _state.update { it.copy(profile = api.profile(id)) }
            // The record the upload RETURNED, not one read back out of the refreshed list: it is
            // this document with the new scan on it, so the window is refilled straight from it.
            if (replacingOpenScan) showScan(stored)
        } catch (failure: Throwable) {
            // Whatever failed, nothing is on its way any more and what is on screen is what is
            // stored. Said here rather than left standing, because a window that goes on promising
            // a copy that was refused is worse than the stale one this card is about. The failure
            // itself reaches the user through [launch], the way every other refused call does.
            _state.update { it.copy(openScanReplacing = false) }
            throw failure
        }
        rematch()
        refreshDerived()
    }

    /**
     * Fetches the stored copy of a document and renders it — the half of this card that makes a
     * scan added on one phone READABLE on another.
     *
     * The pages are cleared before the call for the reason [refreshPreview] clears its own: pages
     * left over from the document opened before are a picture of the wrong Zeugnis.
     */
    fun openScan(document: StoredDocument) = launch("scan") { showScan(document) }

    /// Puts one document's stored copy in the viewer. Two callers: opening a document from the list,
    /// and [deliverPickedScan] once a replacement has gone up — the second is the whole point of
    /// having this as its own function, because the window has to follow the copy it is showing.
    private suspend fun showScan(document: StoredDocument) {
        val id = document.id ?: return
        val info = document.scan ?: return
        _state.update {
            it.copy(openScan = document, scanPages = emptyList(), openScanReplacing = false)
        }

        val file = api.scanFile(id, getApplication<Application>().scanFile(id, info.contentType))

        // Rasterising is the one step in here that is neither a call nor a state change:
        // [renderScanPages] draws every page of the file into a bitmap, and on the main thread
        // that is the whole app standing still — a five-page Zeugnis froze it for seconds and,
        // on an app that had just been started, Android offered the reader to close it on the
        // way to their own Zeugnis. Only the drawing moves off; the update below stays where a
        // state change belongs, and for as long as the drawing takes the viewer says the scan is
        // being fetched, which it never got the chance to draw before.
        val pages = withContext(Dispatchers.Default) { renderScanPages(file, info.contentType) }

        // The window can be closed while its pages are still being drawn, and then these pages
        // belong to nothing: put into the state anyway they are the ones the NEXT window opens
        // over. [deliverPickedScan] passes the very document the update above put in the viewer,
        // so a replacement still reaches it.
        if (_state.value.openScan?.id == id) _state.update { it.copy(scanPages = pages) }
    }

    fun closeScan() =
        _state.update { it.copy(openScan = null, scanPages = emptyList(), openScanReplacing = false) }

    /**
     * Hands the fetched copy to another app.
     *
     * The file is the one [openScan] already wrote, so this shares what is on screen rather than
     * fetching a second copy — and it is under the folder FileProvider serves, which is what makes
     * a file in the app's private storage reachable at all. See [sendPdfByEmail], the same handover.
     */
    fun shareScan() {
        val document = _state.value.openScan ?: return
        val id = document.id ?: return
        val info = document.scan ?: return

        getApplication<Application>().scanFile(id, info.contentType).takeIf { it.isFile }
            ?.let { file -> _state.update { it.copy(pendingScanShare = file) } }
    }

    fun scanShareHandled() = _state.update { it.copy(pendingScanShare = null) }

    /**
     * Removes the stored copy and keeps the document.
     *
     * Two things, and the viewer says so where the button is: the Anlagenverzeichnis goes on naming
     * the Zeugnis because it is still being sent. What leaves is the copy Bewerbo was holding —
     * the "remove the copies" the disclosure promises.
     */
    fun removeScan(documentId: String) = launch("scan") {
        val id = profileId() ?: return@launch
        api.deleteScan(documentId)
        getApplication<Application>().documentsDir()
            .listFiles { file -> file.name.startsWith("scan-$documentId.") }
            ?.forEach { it.delete() }

        _state.update {
            it.copy(profile = api.profile(id), openScan = null, scanPages = emptyList())
        }
        rematch()
        refreshDerived()
    }

    /**
     * Re-reads the Abgleich after the Mappe changed, for the same reason
     * [fileCertificate] does it: what the posting demands is answered from the profile, so filing
     * or removing a document changes it. Without this the row that says a document is outstanding
     * would still say so directly above the document the user had just filed.
     *
     * A no-op until a posting has been read — there is nothing to match against before that.
     */
    private suspend fun rematch() {
        val posting = _state.value.posting ?: return
        _state.update { it.copy(match = api.match(posting.id)) }
    }

    /**
     * Runs the start again after it failed.
     *
     * Without a profile there is no app — every screen reads from it — and [bootstrap] is the only
     * thing that fetches or creates one. It runs once, from init, so a first launch that could not
     * reach the server left the Übersicht saying it was fetching for as long as the app stayed
     * open, including long after the server was answering again.
     */
    fun retryStart() = bootstrap()

    fun dismissError() = _state.update { it.copy(error = null) }

    // -- plumbing --------------------------------------------------------------------------

    private fun describe(file: File) = "${file.name} (${file.length() / 1024} KB)"

    /**
     * Every call goes through here so that a failure always ends up on screen. A silent catch is
     * how a user comes to believe the app saved something it did not.
     */
    private fun launch(busy: String?, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = busy, error = null) }
        runCatching { block() }
            .onFailure { failure ->
                // A session the server no longer knows is the one failure that is not just a
                // snackbar: it was ended on another device, or the account is gone, and every
                // further call would be refused the same way. The door goes back up and the phone
                // keeps nothing of the account — the same thing signing out does, for the same
                // reason. The kind is AuthController's "session_invalid".
                if (failure is ApiFailure && failure.kind == "session_invalid") {
                    forgetTheAccountOnThisDevice()
                }
                _state.update { it.copy(error = errorOf(failure), loading = false) }
            }
        _state.update { it.copy(busy = null) }
    }

    /**
     * What to tell the user about a failure.
     *
     * A server that refused the call names the kind itself. Everything else — no network, a
     * timeout, a body that did not parse — is one case as far as the user is concerned: the app
     * could not reach Bewerbo. It used to be the exception's own message, so a Russian screen
     * showed "Failed to connect to /10.0.2.2:5099" and, failing that, the literal "Fehler".
     */
    private fun errorOf(failure: Throwable): ErrorMessage = when (failure) {
        is ApiFailure -> ErrorMessage(failure.kind, failure.detail)
        else -> ErrorMessage(UNREACHABLE)
    }

    companion object {
        /// The name the exported copy is saved under. Not localised on purpose: it is a file name
        /// the user may have to name to somebody, and it is the same file whichever language the
        /// interface is in.
        private const val ACCOUNT_DATA_FILE = "Bewerbo-Daten.json"

        /// The name the Lebenslauf is saved under, and not localised for the reason above. One name,
        /// so producing it a second time replaces the first rather than leaving the user to work out
        /// which of two files is the current one.
        private const val CV_FILE = "Lebenslauf.pdf"

        /// The name of the assistant's busy state. Named rather than spelled out at both ends:
        /// the screen draws the "preparing an answer" row off exactly this string.
        const val ASSISTANT = "assistant"

        /// The busy state of accepting one proposal. Its own name and not [ASSISTANT]'s, because
        /// the screen tells them apart: a save must not draw the "preparing an answer" row.
        const val PROPOSAL = "proposal"

        /// The name of the fetch behind opening an application. Named for the same reason as the two
        /// above: [isOpeningApplication] is read off exactly this string, and a literal at each end
        /// is how the screen came to call a letter that was on its way a letter that did not exist.
        const val APPLICATION = "application"

        /// The name of the call that writes the Anschreiben, named for the reason [APPLICATION] is:
        /// [isWritingLetter] is read off exactly this string. It is the longest wait in the app —
        /// where a model writes the letter it stands for many seconds — and the screen spent all of
        /// them saying that there was no Anschreiben and that the finished Abgleich was still to do.
        const val LETTER = "letter"

        /// The profile sections a proposal can land in, spelled as the PATCH routes spell them —
        /// and as the server's own schema lists them. The wire word is the contract here, so it is
        /// written once rather than at each of the three branches that switch on it.
        const val EXPERIENCE_SECTION = "berufserfahrung"

        const val EDUCATION_SECTION = "ausbildung"

        const val LANGUAGES_SECTION = "sprachen"

        /// The kind for "the server was not reached at all", which no ProblemDetails can carry.
        const val UNREACHABLE = "unreachable"

        /// The kind for a picture the recogniser found no words in. Client-side, like the two
        /// above: the reading happens on the device, so no server answer can carry it.
        const val PHOTO_UNREADABLE = "photo_unreadable"

        /// The kind for a file that could not be read, or that is none of the three types a scan
        /// may be. Client-side as well: the file is looked at on the device before it is offered,
        /// so the user hears about a .docx now rather than after the upload.
        const val SCAN_UNREADABLE = "scan_unreadable"

        /// The two ways the preview ends up with no pages: the Mappe never arrived, or it arrived
        /// and could not be drawn. Named rather than spelled out at both ends, for the reason the
        /// busy states above are: the Bewerbung screen picks its sentence off exactly these
        /// strings. They are not error kinds: nothing looks them up in [errorOf], because this is
        /// said on the screen and not in a snackbar — see [AppState.previewFailure].
        const val PREVIEW_NOT_FETCHED = "not_fetched"

        const val PREVIEW_NOT_DRAWN = "not_drawn"
    }
}
