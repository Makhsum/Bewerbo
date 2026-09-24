package de.bewerbo.app.data

import android.content.Context
import de.bewerbo.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

/// The backend, as the client sees it. One place that knows a URL.
///
/// [token] is the session this device holds, read again for every call rather than handed over
/// once: the server now refuses any request that does not carry it, and a client that kept a copy
/// would go on sending one after a sign-out. A supplier that returns null is a device with no
/// session, and the calls that are open to anyone — the door, the health check, the legal pages —
/// work exactly as before.
class BewerboApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val token: () -> String?,
) {

    /// Lenient on purpose: a ProblemDetails carries members this app does not model, and a failure
    /// that cannot be parsed must still come out as a failure rather than as a parser exception.
    /// Declared above [client] because the validator below reads it.
    private val problemJson = Json { ignoreUnknownKeys = true }

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

        // Who is asking, on every call. Here and not written out per route, because the server asks
        // it of every route: the thirty-odd calls below are addressed by a profile, a posting, a
        // Bewerbung or a document id, and each of them is now refused unless the session says that
        // id is this user's. The block runs per request, so the token it sends is the one the
        // preferences hold at that moment rather than the one this object was built with.
        defaultRequest {
            token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }

        // Every non-2xx becomes an [ApiFailure] carrying the server's kind, and that is two things
        // at once. It is what lets the snackbar be written in the user's language rather than
        // showing whatever a parser threw. And it is what stops [download] from writing a refusal
        // to disk: a 400 body used to be saved as the Bewerbungsmappe under its .pdf name, with
        // the success message naming the file.
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.isSuccess()) return@validateResponse

                val problem = runCatching { problemJson.decodeFromString<ApiProblem>(response.bodyAsText()) }
                    .getOrNull()
                throw ApiFailure(problem?.kind.orEmpty(), problem?.detail.orEmpty())
            }
        }
    }

    suspend fun health(): Health = client.get("$baseUrl/api/health").body()

    // -- the door ----------------------------------------------------------------------------

    suspend fun register(request: RegisterRequest): Session =
        client.post("$baseUrl/api/auth/register") { json(request) }.body()

    suspend fun signIn(credentials: Credentials): Session =
        client.post("$baseUrl/api/auth/sign-in") { json(credentials) }.body()

    /// Asks for the reset mail. It answers the same whether or not the address has an account here,
    /// so nothing this returns says whether one was sent — the screen tells the user to look in
    /// their mail either way. See AuthController for why.
    suspend fun forgotPassword(request: ForgotPasswordRequest) {
        client.post("$baseUrl/api/auth/forgot-password") { json(request) }
    }

    /// Spends the code out of that mail on a new password, and comes back signed in: the answer is
    /// the same [Session] a sign-in returns, so the app lands exactly where a sign-in lands.
    suspend fun resetPassword(request: ResetPasswordRequest): Session =
        client.post("$baseUrl/api/auth/reset-password") { json(request) }.body()

    /// Who the token names — asked at every launch, before anything is drawn. A refusal means the
    /// session has ended and the door goes back up; see [AppViewModel.bootstrap].
    suspend fun session(): Session = client.get("$baseUrl/api/auth/session").body()

    /// Ends this device's session and no other. The same account signed in on another phone stays
    /// signed in, which is the whole reason the token is per device rather than per account.
    suspend fun signOut() {
        client.post("$baseUrl/api/auth/sign-out")
    }

    /// Whether the profile this phone is still naming may be kept by a new account — asked before
    /// the door offers to keep it, because a stored id outlives the account state it was written
    /// in. See [AppViewModel.adoptableProfile].
    suspend fun adoptable(profileId: String): Adoptable =
        client.get("$baseUrl/api/auth/adoptable/$profileId").body()

    // -- profile -----------------------------------------------------------------------------

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

    /// The German wording proposed for a reason, without storing anything — what the gap card shows
    /// under the input while the user is still typing. Takes the reason as a [parameter] rather than
    /// in the path: it is the user's own prose, in their own alphabet, and it has to be encoded.
    suspend fun gapWording(reason: String): GapWordingResponse =
        client.get("$baseUrl/api/profile/gap-wording") { parameter("reason", reason) }.body()

    /// The duties of one position written as results, without storing anything — what the
    /// experience card shows beside the originals so the user chooses which of the two is saved.
    suspend fun dutyOutcomes(duties: String): DutyOutcomesResponse =
        client.post("$baseUrl/api/profile/duty-outcomes") { json(DutyOutcomesRequest(duties)) }.body()

    suspend fun degrees(country: String?): List<DegreeEquivalence> =
        client.get("$baseUrl/api/recognition/degrees" + (country?.let { "?country=$it" } ?: "")).body()

    // -- the account's own data ---------------------------------------------------------------

    /// What the server holds about this account, for the settings screen to list.
    suspend fun accountData(id: String): DataExport =
        client.get("$baseUrl/api/profile/$id/data").body()

    /// The same answer, written to a file as it arrived. Deliberately the raw body rather than
    /// [accountData] re-serialised: what the user takes away should be what the server sent, and the
    /// client only models the part of it that it draws.
    suspend fun accountDataFile(id: String, into: File): File =
        download(client.get("$baseUrl/api/profile/$id/data"), into)

    /// Erases the account and everything held under it.
    suspend fun deleteAccount(id: String) {
        client.delete("$baseUrl/api/profile/$id")
    }

    /// Who runs this installation, and whether a model outside it writes the Anschreiben — the two
    /// facts the legal pages need and the app cannot know by itself.
    suspend fun legal(): LegalInfo = client.get("$baseUrl/api/legal").body()

    suspend fun lebenslaufPdf(id: String, into: File): File =
        download(client.post("$baseUrl/api/profile/$id/lebenslauf"), into)

    // -- posting -----------------------------------------------------------------------------

    suspend fun parsePosting(request: ParsePostingRequest): PostingView =
        client.post("$baseUrl/api/postings/parse") { json(request) }.body()

    /// The advert text behind a link. The SERVER fetches the page — see the endpoint for why — so
    /// what comes back is text, not a posting: nothing is parsed or stored until the user has read
    /// it and pressed the same button a pasted advert is read with.
    suspend fun readLink(request: ReadLinkRequest): PostingText =
        client.post("$baseUrl/api/postings/from-link") { json(request) }.body()

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

    /// Stores the scanned file for a document, replacing the one that was there. The bytes are the
    /// body and the name travels as a parameter — it is the name the file had on the device, kept
    /// only so the user recognises it. What comes back is the document with its page count read
    /// off the file, which is what the Anlagenverzeichnis then prints.
    suspend fun storeScan(documentId: String, scan: PickedScan): StoredDocument =
        client.post("$baseUrl/api/documents/$documentId/scan") {
            parameter("name", scan.fileName)
            contentType(ContentType.parse(scan.contentType))
            setBody(scan.bytes)
        }.body()

    /// The scanned file itself, into [into] — what makes a document added on another phone
    /// openable here. Through [download] like every other file this app fetches.
    suspend fun scanFile(documentId: String, into: File): File =
        download(client.get("$baseUrl/api/documents/$documentId/scan"), into)

    /// Removes the stored copy and keeps the document: the Anlagenverzeichnis goes on naming it.
    suspend fun deleteScan(documentId: String) {
        client.delete("$baseUrl/api/documents/$documentId/scan")
    }

    // -- what may be held about the scans ------------------------------------------------------

    /// Whether this account has agreed to Bewerbo holding its scans — what the Documents screen
    /// asks before it offers to store the first one.
    suspend fun scanConsent(profileId: String): ScanConsent =
        client.get("$baseUrl/api/profile/$profileId/scan-consent").body()

    /// Records that the disclosure was read and agreed to. Idempotent on the server, and the first
    /// agreement is the one kept.
    suspend fun agreeToScans(profileId: String): ScanConsent =
        client.post("$baseUrl/api/profile/$profileId/scan-consent").body()

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

/// The one folder every produced file lands in. Named here rather than spelled out twice, because
/// signing out has to empty exactly what writing fills — see [AppViewModel.signOut].
fun Context.documentsDir(): File = File(filesDir, "bewerbungen")

/// Where a produced PDF lands on the device, under the app's own files so nothing needs a
/// storage permission.
fun Context.documentFile(name: String): File = File(documentsDir(), name)

/// Where a fetched scan lands. Under [documentsDir] rather than in the cache, and for two reasons:
/// that folder is the one FileProvider hands out content:// URIs for, so "Share a copy" can pass it
/// to another app — and it is the folder signing out empties, so a scan does not outlive the
/// session that fetched it.
fun Context.scanFile(documentId: String, contentType: String): File =
    File(documentsDir(), "scan-$documentId.${scanExtension(contentType)}")

/// The extension that belongs to a scan's type. A file handed to another app is opened by its
/// name as often as by its MIME type, so a Zeugnis called "scan-…" with no extension opens in
/// nothing.
fun scanExtension(contentType: String) = when (contentType) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    else -> "pdf"
}

/// Where the preview's copy of the Mappe goes. The cache and not [documentFile]: it is re-rendered
/// every time the chosen parts change and it is not the file the user asked to keep.
fun Context.previewFile(): File = File(cacheDir, "vorschau.pdf")

/// A call the server refused. [kind] is what to say about it in the user's language; [detail] is
/// the German the server sent, kept as the message so a log line still says what happened and as
/// the fallback for a kind this build does not know.
class ApiFailure(val kind: String, val detail: String) : Exception(detail)
