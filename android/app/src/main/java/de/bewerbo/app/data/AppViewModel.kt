package de.bewerbo.app.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bewerbo.app.R
import de.bewerbo.app.ui.uiLanguageOrDefault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val match: MatchView? = null,
    val application: ApplicationView? = null,
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
    /// The pages could not be fetched. Kept as state rather than left to the error snackbar: that
    /// one is gone in seconds and the preview would go on standing there empty with no way back.
    val previewFailed: Boolean = false,
    /// Set when the user asked to send the Mappe. The screen hands it to a mail app and clears it.
    val pendingEmail: EmailDraft? = null,
    /// Set when the user asked for the copy of what is held about them. The settings screen hands
    /// it to a chooser and clears it — the same handover [pendingEmail] gets, for the same reason:
    /// a file in the app’s own storage is reachable from nowhere else.
    val pendingExport: File? = null,
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

    private val api = BewerboApi()
    private val _state = MutableStateFlow(AppState())
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
                api.session(token)
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
        sessionPrefs().getString("authToken", null)?.let { token ->
            runCatching { api.signOut(token) }
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
        // folder documentFile() writes into; the preview is rendered into the cache beside it.
        val app = getApplication<Application>()
        app.documentsDir().deleteRecursively()
        app.previewFile().delete()

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

    fun generateCv() = launch("cv") {
        val id = profileId() ?: return@launch
        val name = "Lebenslauf.pdf"
        val file = api.lebenslaufPdf(id, getApplication<Application>().documentFile(name))
        _state.update { it.copy(lastSavedFile = describe(file)) }
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

    fun parsePosting(text: String) = launch("posting") {
        val id = profileId() ?: return@launch
        val posting = api.parsePosting(ParsePostingRequest(id, text))
        // A new posting invalidates the match and the letter that were written against the old one.
        // The draft goes with them: it has been read now, and it is the source text of the posting
        // on screen — keeping a second copy of it is how "Paste a different posting" would come
        // back with the last advert already in the field.
        _state.update { it.copy(posting = posting, postingDraft = "", match = null, application = null, review = null, ats = null, previewPages = emptyList()) }
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
            it.copy(posting = null, postingDraft = "", match = null, application = null, review = null, ats = null, previewPages = emptyList())
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

        api.addDocument(id, document)
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
    fun generateLetter(tone: String) = launch("letter") {
        val id = profileId() ?: return@launch
        val posting = _state.value.posting ?: return@launch
        val application = api.createApplication(CreateApplicationRequest(id, posting.id, tone))
        _state.update { it.copy(application = application, review = null, ats = null, previewPages = emptyList()) }
        prefs().edit().putString("applicationId", application.id).apply()
        runChecks(application.id)
        refreshDerived()
    }

    fun regenerateLetter(tone: String) = launch("letter") {
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
    fun openApplication(applicationId: String) = launch("application") {
        // Cleared before the call, not after it: until the new one has arrived, leaving the last
        // application on screen would put one employer's Anschreiben under another's name.
        _state.update { it.copy(application = null, match = null, review = null, ats = null, previewPages = emptyList()) }

        val application = api.application(applicationId)
        val posting = api.posting(application.postingId)
        val match = api.match(posting.id)
        _state.update { it.copy(posting = posting, match = match, application = application) }

        prefs().edit()
            .putString("postingId", posting.id)
            .putString("applicationId", application.id)
            .apply()
        runChecks(application.id)
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
        _state.update { it.copy(previewPages = emptyList(), previewFailed = false) }

        val file = runCatching {
            api.applicationPdf(application.id, parts, getApplication<Application>().previewFile())
        }.onFailure {
            _state.update { state -> state.copy(previewFailed = true) }
        }.getOrThrow()

        _state.update { it.copy(previewPages = renderPdfPages(file)) }
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

    fun addDocument(document: StoredDocument) = launch("document") {
        val id = profileId() ?: return@launch
        api.addDocument(id, document)
        _state.update { it.copy(profile = api.profile(id)) }
        rematch()
        refreshDerived()
    }

    fun deleteDocument(documentId: String) = launch("document") {
        val id = profileId() ?: return@launch
        api.deleteDocument(documentId)
        _state.update { it.copy(profile = api.profile(id)) }
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

        /// The kind for "the server was not reached at all", which no ProblemDetails can carry.
        const val UNREACHABLE = "unreachable"

        /// The kind for a picture the recogniser found no words in. Client-side, like the two
        /// above: the reading happens on the device, so no server answer can carry it.
        const val PHOTO_UNREADABLE = "photo_unreadable"
    }
}
