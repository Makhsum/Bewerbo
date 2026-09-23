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
    val match: MatchView? = null,
    val application: ApplicationView? = null,
    val review: Review? = null,
    val ats: AtsResult? = null,
    val degrees: List<DegreeEquivalence> = emptyList(),
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
     * Opens the profile this device is working on, creating one on first run.
     *
     * The id is kept in shared preferences rather than being asked for: the product's user has
     * enough forms to fill in already, and an account is not what they came for.
     */
    private fun bootstrap() = launch("start") {
        val health = runCatching { api.health() }.getOrNull()
        _state.update { it.copy(writer = health?.writer ?: "regeln") }

        val stored = prefs().getString("profileId", null)

        val profile = if (stored != null) {
            runCatching { api.profile(stored) }.getOrNull() ?: api.createProfile(Person())
        } else {
            api.createProfile(Person())
        }
        prefs().edit().putString("profileId", profile.id).apply()

        _state.update { it.copy(profile = profile, loading = false) }
        refreshDerived()
        restoreWorkInProgress()
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
     * Erases the account and everything held under it — Art. 17 DSGVO — and opens an empty one.
     *
     * A new account straight afterwards, because there is no app without one: every screen reads
     * from a profile, and leaving the user on a deleted id would look exactly like the start failure
     * [retryStart] exists for. The interface language is deliberately kept: it is a preference of
     * this device, not something held about the person.
     */
    fun deleteAccount() = launch("account") {
        val id = profileId() ?: return@launch
        api.deleteAccount(id)
        prefs().edit().remove("profileId").remove("postingId").remove("applicationId").apply()
        _state.update { AppState(uiLanguage = it.uiLanguage, writer = it.writer) }
        bootstrap()
    }

    /**
     * Continues on this device with an account that already exists, named by its key.
     *
     * This is what makes it an account of the user's OWN rather than a record of one phone: the key
     * the settings screen shows is what carries a profile to a new device, and the app has no
     * password to ask for because it never had one.
     *
     * The key is checked here before the call, because a mistyped one is the ordinary case and the
     * router refuses a non-Guid id before a controller sees it — that answer carries no kind, so the
     * snackbar would have had nothing to say. The profile is fetched BEFORE anything is written
     * down: a key for an account that is gone must leave the user on the one they were on.
     */
    fun useAccount(key: String) = launch("account") {
        val trimmed = key.trim()
        if (runCatching { java.util.UUID.fromString(trimmed) }.isFailure) {
            _state.update { it.copy(error = ErrorMessage(ACCOUNT_KEY_INVALID)) }
            return@launch
        }

        val profile = api.profile(trimmed)
        prefs().edit()
            .putString("profileId", profile.id)
            .remove("postingId")
            .remove("applicationId")
            .apply()
        _state.update {
            AppState(
                uiLanguage = it.uiLanguage, writer = it.writer, profile = profile, loading = false,
            )
        }
        refreshDerived()
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
        api.explainGap(id, GapUpdate(gap.from, gap.to, reason))
        refreshDerived()
    }

    fun lookUpDegrees(country: String?) = launch("degrees") {
        _state.update { it.copy(degrees = api.degrees(country)) }
    }

    fun generateCv() = launch("cv") {
        val id = profileId() ?: return@launch
        val name = "Lebenslauf.pdf"
        val file = api.lebenslaufPdf(id, getApplication<Application>().documentFile(name))
        _state.update { it.copy(lastSavedFile = describe(file)) }
    }

    // -- posting ---------------------------------------------------------------------------

    fun parsePosting(text: String) = launch("posting") {
        val id = profileId() ?: return@launch
        val posting = api.parsePosting(ParsePostingRequest(id, text))
        // A new posting invalidates the match and the letter that were written against the old one.
        _state.update { it.copy(posting = posting, match = null, application = null, review = null, ats = null, previewPages = emptyList()) }
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
            it.copy(posting = null, match = null, application = null, review = null, ats = null, previewPages = emptyList())
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
        /// The kind for a key the user typed that is not an account key at all. Client-side, like
        /// [UNREACHABLE]: no server answer carries it.
        const val ACCOUNT_KEY_INVALID = "account_key_invalid"

        /// The name the exported copy is saved under. Not localised on purpose: it is a file name
        /// the user may have to name to somebody, and it is the same file whichever language the
        /// interface is in.
        private const val ACCOUNT_DATA_FILE = "Bewerbo-Daten.json"

        /// The kind for "the server was not reached at all", which no ProblemDetails can carry.
        const val UNREACHABLE = "unreachable"
    }
}
