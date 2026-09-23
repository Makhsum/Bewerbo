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
)

@Serializable
data class Review(
    val passed: Boolean = false,
    val hintCount: Int = 0,
    val checks: List<ReviewCheck> = emptyList(),
)

@Serializable
data class AtsFinding(val key: String, val label: String, val found: Boolean, val detail: String = "")

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
    val readiness: Int = 0,
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
