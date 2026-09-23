package de.bewerbo.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The wire shapes, mirroring Endpoints/Dtos.cs on the server. Kept as plain data classes so a
// screen never holds a network type it cannot construct in a preview.

@Serializable
data class Person(
    val inputLanguage: String = "ru",
    val firstName: String = "",
    val lastName: String = "",
    val street: String = "",
    val postalCode: String = "",
    val city: String = "",
    val phone: String = "",
    val email: String = "",
    val birthDate: String? = null,
    val template: String = "Klassisch",
)

@Serializable
data class Experience(
    val id: String? = null,
    val position: String = "",
    val employer: String = "",
    val location: String = "",
    val from: String = "",
    val to: String? = null,
    val workload: String = "",
    val industry: String = "",
    val duties: String = "",
    val referenceOnFile: Boolean = false,
) {
    val dutyLines: List<String> get() = duties.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

@Serializable
data class Education(
    val id: String? = null,
    val degree: String = "",
    val institution: String = "",
    val location: String = "",
    val country: String = "",
    val from: String = "",
    val to: String? = null,
    val anabinAssessment: String? = null,
    val germanEquivalent: String? = null,
    val equivalenceConfirmed: Boolean = false,
    val zabAssessmentPending: Boolean = false,
)

@Serializable
data class LanguageSkill(
    val id: String? = null,
    val language: String = "",
    val level: String = "",
    val certificateOnFile: Boolean = false,
)

@Serializable
data class StoredDocument(
    val id: String? = null,
    val title: String = "",
    val kind: String = "Sonstiges",
    val note: String = "",
    val pageCount: Int = 1,
)

@Serializable
data class Translation(
    val available: Boolean = false,
    val pending: Int = 0,
    val pendingExamples: List<String> = emptyList(),
)

@Serializable
data class ProfileView(
    val id: String,
    val person: Person = Person(),
    val experience: List<Experience> = emptyList(),
    val education: List<Education> = emptyList(),
    val languages: List<LanguageSkill> = emptyList(),
    val documents: List<StoredDocument> = emptyList(),
    val completeness: Int = 0,
    val translation: Translation = Translation(),
)

@Serializable
data class TimelinePeriod(
    val kind: String,
    val label: String,
    val from: String,
    val to: String,
    val ongoing: Boolean,
)

@Serializable
data class Gap(
    val from: String,
    val to: String,
    val months: Int,
    val explained: Boolean = false,
    val reason: String? = null,
    val germanWording: String? = null,
)

@Serializable
data class Timeline(
    val firstYear: Int = 0,
    val lastYear: Int = 0,
    val periods: List<TimelinePeriod> = emptyList(),
    val gaps: List<Gap> = emptyList(),
)

@Serializable
data class GapUpdate(val from: String, val to: String, val reason: String, val germanWording: String? = null)

@Serializable
data class ParsePostingRequest(val profileId: String, val text: String, val employerType: String? = null)

@Serializable
data class ReadLinkRequest(val url: String)

/// An advert the server read out of a page, before anything was parsed or stored — the text the
/// user is about to check.
@Serializable
data class PostingText(val text: String = "")

@Serializable
data class EvidenceField(
    val key: String,
    val value: String,
    val quote: String = "",
    val confidence: String = "sicher",
    /// -1 when the quote could not be found verbatim. The field then shows with no highlight
    /// rather than against words it did not come from.
    val spanStart: Int = -1,
    val spanLength: Int = 0,
)

@Serializable
data class PostingView(
    val id: String,
    val sourceText: String = "",
    val employerType: String = "Mittelstand",
    val fields: List<EvidenceField> = emptyList(),
    val requirements: List<String> = emptyList(),
) {
    fun field(key: String): EvidenceField? = fields.firstOrNull { it.key == key }
}

@Serializable
data class CorrectFieldRequest(val key: String, val value: String)

@Serializable
data class Requirement(
    val text: String,
    /// belegt | offen | nicht_belegt
    val state: String,
    val evidence: String = "",
    val action: String = "",
    /// For offen: the language entry whose Nachweis closes this requirement.
    val language: String = "",
    /// What [evidence] and [action] SAY, so the screen writes them in the user's language.
    /// The two strings above keep the German the letter writer quotes.
    val evidenceKind: String = "",
    val evidenceArgs: List<String> = emptyList(),
    val actionKind: String = "",
    val actionArgs: List<String> = emptyList(),
)

@Serializable
data class DemandedDocument(
    /// Arbeitszeugnis | Zertifikat | Sprachnachweis | AnabinAuszug
    val kind: String,
    val title: String,
    /// The posting's own sentence that asks for it.
    val quote: String = "",
    /// False means outstanding: the posting wants it and the Mappe has nothing for it.
    val onFile: Boolean = false,
    /// What follows the kind name in [title]: the language and level of a Sprachnachweis, nothing
    /// for the other kinds. The screen writes the kind in the user's language and appends these.
    val titleArgs: List<String> = emptyList(),
)

@Serializable
data class MatchView(
    val postingId: String,
    val company: String = "",
    val reference: String = "",
    val covered: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    val requirements: List<Requirement> = emptyList(),
    /// The documents this posting demands, for the Mappe to mark.
    val documents: List<DemandedDocument> = emptyList(),
)

@Serializable
data class CreateApplicationRequest(val profileId: String, val postingId: String, val tone: String? = null)

@Serializable
data class Letter(
    val salutation: String = "",
    val subject: String = "",
    val paragraphs: List<String> = emptyList(),
    val closing: String = "",
    val attachments: List<String> = emptyList(),
)

@Serializable
data class ApplicationView(
    val id: String,
    val profileId: String = "",
    val postingId: String = "",
    val tone: String = "Sachlich",
    val status: String = "Entwurf",
    /// "model" or "regeln" — which writer produced this letter.
    val source: String = "regeln",
    val letter: Letter = Letter(),
    val fileName: String = "",
    val requirements: List<Requirement> = emptyList(),
)

@Serializable
data class ReviewCheck(
    val key: String,
    val title: String,
    /// ok | hinweis | fehler
    val verdict: String,
    val detail: String = "",
    val items: List<String> = emptyList(),
    /// What the check found, for the screen to write in the user's language; [items] quote the
    /// German letter and stay as they are.
    val detailKind: String = "",
    val detailArgs: List<String> = emptyList(),
)

@Serializable
data class Review(
    val passed: Boolean = false,
    val hintCount: Int = 0,
    val checks: List<ReviewCheck> = emptyList(),
    /// The keys of the checks the backend left out because the text is too short for them to say
    /// anything. They are not in [checks], and the summary must not read as a clean pass over them.
    val notChecked: List<String> = emptyList(),
)

/// One machine-readability check. [verdict] is "ok", "fehler" or "ungeprueft"; the third one says
/// the check reads a profile field that is empty, and [target] is then the screen it is filled in
/// on — the same route a [NextStep] carries.
@Serializable
data class AtsFinding(
    val key: String,
    val label: String,
    val verdict: String = "",
    val detail: String = "",
    val detailKind: String = "",
    val detailArgs: List<String> = emptyList(),
    val target: String = "",
)

@Serializable
data class AtsResult(
    val passed: Boolean = false,
    val pageCount: Int = 0,
    val sizeBytes: Int = 0,
    val fileName: String = "",
    val findings: List<AtsFinding> = emptyList(),
)

@Serializable
data class NextStep(
    val key: String,
    val title: String,
    val detail: String = "",
    /// attention | info
    val severity: String = "info",
    /// The destination this step deep-links to.
    val target: String = "profil",
    /// Which step this is, and what to put into its sentence. [title] and [detail] carry the
    /// server's German wording; it cannot know the interface language, so the screen renders from
    /// these two instead and falls back to the German only for a kind it does not know.
    val kind: String = "",
    val args: List<String> = emptyList(),
)

@Serializable
data class ActiveApplication(
    val id: String,
    val jobTitle: String = "",
    val company: String = "",
    val reference: String = "",
    val status: String = "Entwurf",
    val sentAt: String? = null,
    /// What THIS application still needs — as opposed to [Overview.nextSteps], which is what the
    /// profile needs. Same shape, so the Übersicht draws both rows the same way.
    val openSteps: List<NextStep> = emptyList(),
)

@Serializable
data class Overview(
    val displayName: String = "",
    val city: String = "",
    val applicationCount: Int = 0,
    /// Whether the profile carries enough for an application to be worth beginning. False is what
    /// makes the Übersicht offer the profile as the first step instead of the flow.
    val canStartApplication: Boolean = false,
    /// What is still missing before the Anschreiben may be written, as keys — empty when it may.
    /// A stricter question than [canStartApplication]: the flow can be worth beginning before the
    /// letterhead is filled in, the letter cannot be written then. The Abgleich names these and
    /// keeps its button disabled until the list is empty.
    val letterBlockers: List<String> = emptyList(),
    val profileCompleteness: Int = 0,
    val gapsExplained: Int = 0,
    val gapsTotal: Int = 0,
    val evidenceOnFile: Int = 0,
    val evidenceExpected: Int = 0,
    val nextSteps: List<NextStep> = emptyList(),
    val applications: List<ActiveApplication> = emptyList(),
    val documents: List<StoredDocument> = emptyList(),
)

@Serializable
data class DegreeEquivalence(
    val country: String,
    val foreignDegree: String,
    val germanEquivalent: String,
    val anabinRating: String,
    val note: String,
)

@Serializable
data class StatusRequest(val status: String)

@Serializable
data class Health(val status: String = "", val writer: String = "regeln")

@Serializable
data class GapWordingResponse(@SerialName("reason") val reason: String = "", val german: String = "")

/// One duty line beside the result proposed for it. [outcome] is empty where the rewrite does not
/// fit that line — the card says so and keeps the original, rather than showing a guess.
@Serializable
data class DutyOutcome(val original: String = "", val outcome: String = "")

@Serializable
data class DutyOutcomesRequest(val duties: String = "")

@Serializable
data class DutyOutcomesResponse(val lines: List<DutyOutcome> = emptyList())

/// A ProblemDetails, as the API answers every failure. [kind] says WHICH failure it is, so the
/// snackbar can be written in the user's language; [detail] is the German the server sent, kept as
/// the fallback for a kind this build does not know. Same division as [NextStep].
@Serializable
data class ApiProblem(val detail: String = "", val kind: String = "")

/// One kind of thing the server holds about the account, and how much of it. [key] and not a
/// sentence, for the reason a [NextStep] carries a kind: the settings screen writes the name in the
/// user's language. "person" counts the personal details that are filled in, not records.
@Serializable
data class DataCategory(val key: String, val count: Int)

/// What is stored about the account, as the settings screen shows it. Only the part that is drawn
/// is modelled — the rest of the export is the file the user takes away, and it is written to disk
/// as the server sent it rather than re-serialised from here.
@Serializable
data class DataExport(
    val accountId: String = "",
    val exportedAt: String = "",
    val categories: List<DataCategory> = emptyList(),
)

/// The operator of this installation, for the Impressum. [stated] false means the deployment has
/// not said who it is; the page then says so instead of showing empty lines that read as an address.
@Serializable
data class LegalOperator(
    val stated: Boolean = false,
    val name: String = "",
    val street: String = "",
    val postalCode: String = "",
    val city: String = "",
    val country: String = "",
    val email: String = "",
    val represented: String = "",
    val register: String = "",
)

/// What the legal pages cannot be written without asking the server, because it belongs to the
/// deployment and not to the app. [modelProcessor] is the host that writes the Anschreiben, empty
/// when the rule-based writer runs and nothing the user typed leaves the server.
@Serializable
data class LegalInfo(
    @SerialName("operator") val operatorDetails: LegalOperator = LegalOperator(),
    val modelProcessor: String = "",
)
