package de.bewerbo.app.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AppState(
    val loading: Boolean = true,
    val error: String? = null,
    /// What the backend said about itself — which writer is in use.
    val writer: String = "regeln",
    val profile: ProfileView? = null,
    val timeline: Timeline = Timeline(),
    val overview: Overview = Overview(),
    val posting: PostingView? = null,
    val match: MatchView? = null,
    val application: ApplicationView? = null,
    val review: Review? = null,
    val ats: AtsResult? = null,
    val degrees: List<DegreeEquivalence> = emptyList(),
    val showDinGrid: Boolean = false,
    val lastSavedPdf: String? = null,
    val busy: String? = null,
)

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
     * Brings back the posting and the letter the user was last working on.
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

        _state.update { it.copy(posting = posting, application = pair) }
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
        _state.update { it.copy(lastSavedPdf = describe(file)) }
    }

    // -- posting ---------------------------------------------------------------------------

    fun parsePosting(text: String) = launch("posting") {
        val id = profileId() ?: return@launch
        val posting = api.parsePosting(ParsePostingRequest(id, text))
        // A new posting invalidates the match and the letter that were written against the old one.
        _state.update { it.copy(posting = posting, match = null, application = null, review = null, ats = null) }
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
            it.copy(posting = null, match = null, application = null, review = null, ats = null)
        }
    }

    fun correctField(key: String, value: String) = launch("field") {
        val posting = _state.value.posting ?: return@launch
        _state.update { it.copy(posting = api.correctField(posting.id, CorrectFieldRequest(key, value))) }
    }

    fun setEmployerType(type: String) = launch("employer") {
        val posting = _state.value.posting ?: return@launch
        _state.update { it.copy(posting = api.setEmployerType(posting.id, type)) }
    }

    fun matchRequirements() = launch("match") {
        val posting = _state.value.posting ?: return@launch
        _state.update { it.copy(match = api.match(posting.id)) }
    }

    // -- application -----------------------------------------------------------------------

    fun generateLetter(tone: String) = launch("letter") {
        val id = profileId() ?: return@launch
        val posting = _state.value.posting ?: return@launch
        val application = api.createApplication(CreateApplicationRequest(id, posting.id, tone))
        _state.update { it.copy(application = application, review = null, ats = null) }
        prefs().edit().putString("applicationId", application.id).apply()
        runChecks(application.id)
    }

    fun regenerateLetter(tone: String) = launch("letter") {
        val application = _state.value.application ?: return@launch
        val updated = api.regenerate(application.id, tone)
        _state.update { it.copy(application = updated, review = null, ats = null) }
        runChecks(updated.id)
    }

    private suspend fun runChecks(applicationId: String) {
        val review = api.review(applicationId)
        val ats = api.atsCheck(applicationId)
        _state.update { it.copy(review = review, ats = ats) }
    }

    fun toggleDinGrid() = _state.update { it.copy(showDinGrid = !it.showDinGrid) }

    fun savePdf(parts: String? = null) = launch("pdf") {
        val application = _state.value.application ?: return@launch
        val name = application.fileName.ifBlank { "Bewerbung.pdf" }
        val file = api.applicationPdf(
            application.id, parts, getApplication<Application>().documentFile(name),
        )
        _state.update { it.copy(lastSavedPdf = describe(file)) }

        // The export panel shows the page count and size from the last ATS check. Adding a
        // document to the Mappe changes both — without this the panel keeps claiming the figures
        // of a file that no longer exists.
        runChecks(application.id)
    }

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
        refreshDerived()
    }

    fun deleteDocument(documentId: String) = launch("document") {
        val id = profileId() ?: return@launch
        api.deleteDocument(documentId)
        _state.update { it.copy(profile = api.profile(id)) }
        refreshDerived()
    }

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
                _state.update {
                    it.copy(error = failure.message ?: failure::class.simpleName ?: "Fehler", loading = false)
                }
            }
        _state.update { it.copy(busy = null) }
    }
}
