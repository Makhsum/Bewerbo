package de.bewerbo.app.data

import android.content.Context
import de.bewerbo.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

/// The backend, as the client sees it. One place that knows a URL.
class BewerboApi(private val baseUrl: String = BuildConfig.API_BASE_URL) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            // Rendering a Bewerbungsmappe and reading the PDF back takes longer than a normal call.
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun health(): Health = client.get("$baseUrl/api/health").body()

    // -- profile -----------------------------------------------------------------------------

    suspend fun createProfile(person: Person): ProfileView =
        client.post("$baseUrl/api/profile") { json(person) }.body()

    suspend fun profile(id: String): ProfileView = client.get("$baseUrl/api/profile/$id").body()

    suspend fun savePerson(id: String, person: Person): ProfileView =
        client.patch("$baseUrl/api/profile/$id/sections/person") { json(person) }.body()

    suspend fun saveExperience(id: String, entries: List<Experience>): ProfileView =
        client.patch("$baseUrl/api/profile/$id/sections/berufserfahrung") { json(entries) }.body()

    suspend fun saveEducation(id: String, entries: List<Education>): ProfileView =
        client.patch("$baseUrl/api/profile/$id/sections/ausbildung") { json(entries) }.body()

    suspend fun saveLanguages(id: String, entries: List<LanguageSkill>): ProfileView =
        client.patch("$baseUrl/api/profile/$id/sections/sprachen") { json(entries) }.body()

    suspend fun timeline(id: String): Timeline = client.get("$baseUrl/api/profile/$id/timeline").body()

    suspend fun explainGap(id: String, update: GapUpdate): Gap =
        client.post("$baseUrl/api/profile/$id/gaps") { json(update) }.body()

    suspend fun degrees(country: String?): List<DegreeEquivalence> =
        client.get("$baseUrl/api/recognition/degrees" + (country?.let { "?country=$it" } ?: "")).body()

    suspend fun lebenslaufPdf(id: String, into: File): File =
        download(client.post("$baseUrl/api/profile/$id/lebenslauf"), into)

    // -- posting -----------------------------------------------------------------------------

    suspend fun parsePosting(request: ParsePostingRequest): PostingView =
        client.post("$baseUrl/api/postings/parse") { json(request) }.body()

    suspend fun posting(id: String): PostingView = client.get("$baseUrl/api/postings/$id").body()

    suspend fun correctField(postingId: String, request: CorrectFieldRequest): PostingView =
        client.patch("$baseUrl/api/postings/$postingId/fields") { json(request) }.body()

    suspend fun setEmployerType(postingId: String, type: String): PostingView =
        client.patch("$baseUrl/api/postings/$postingId/employer-type?type=$type").body()

    suspend fun match(postingId: String): MatchView =
        client.post("$baseUrl/api/postings/$postingId/match").body()

    // -- application -------------------------------------------------------------------------

    suspend fun createApplication(request: CreateApplicationRequest): ApplicationView =
        client.post("$baseUrl/api/applications") { json(request) }.body()

    suspend fun application(id: String): ApplicationView =
        client.get("$baseUrl/api/applications/$id").body()

    suspend fun regenerate(id: String, tone: String): ApplicationView =
        client.post("$baseUrl/api/applications/$id/regenerate?tone=$tone").body()

    suspend fun review(id: String): Review = client.post("$baseUrl/api/applications/$id/review").body()

    suspend fun atsCheck(id: String): AtsResult = client.post("$baseUrl/api/applications/$id/ats-check").body()

    suspend fun setStatus(id: String, status: String): Unit =
        client.post("$baseUrl/api/applications/$id/status") { json(StatusRequest(status)) }.body()

    suspend fun applicationPdf(id: String, parts: String?, into: File): File {
        val query = parts?.let { "?parts=$it" } ?: ""
        return download(client.get("$baseUrl/api/applications/$id/pdf$query"), into)
    }

    // -- locker ------------------------------------------------------------------------------

    suspend fun addDocument(profileId: String, document: StoredDocument): StoredDocument =
        client.post("$baseUrl/api/documents?profileId=$profileId") { json(document) }.body()

    suspend fun deleteDocument(id: String) {
        client.delete("$baseUrl/api/documents/$id")
    }

    suspend fun overview(profileId: String): Overview = client.get("$baseUrl/api/overview/$profileId").body()

    // -- plumbing ----------------------------------------------------------------------------

    private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun download(response: HttpResponse, into: File): File {
        into.parentFile?.mkdirs()
        into.writeBytes(response.readBytes())
        return into
    }
}

/// Where a produced PDF lands on the device, under the app's own files so nothing needs a
/// storage permission.
fun Context.documentFile(name: String): File = File(File(filesDir, "bewerbungen"), name)

/// Where the preview's copy of the Mappe goes. The cache and not [documentFile]: it is re-rendered
/// every time the chosen parts change and it is not the file the user asked to keep.
fun Context.previewFile(): File = File(cacheDir, "vorschau.pdf")
