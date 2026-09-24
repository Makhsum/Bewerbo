using System.Net;
using System.Text.Json;
using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using static Bewerbo.Api.Contracts.DtoMapping;

namespace Bewerbo.Api.Controllers;

[Route("api/postings")]
[IdNames(OwnedResource.Posting)]
public class PostingsController(BewerboDbContext db) : BewerboController
{
    [HttpPost("parse")]
    public async Task<IActionResult> Parse([FromBody] ParsePostingRequest request,
        [FromServices] ILanguageModel model, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(request.Text)) return InvalidRequest("text", "Kein Anzeigentext.");
        // The profile this advert is being filed under travels in the BODY, where OwnershipFilter
        // does not see it — and what is stored here is the full text of an advert, under the id it
        // names. Refused as a profile that is not there, the way every other mismatch is.
        if (request.ProfileId != SignedInProfileId)
        {
            return NotFoundProblem(ProfileController.ProfileMissing, ProfileController.ProfileMissingKind);
        }

        // A model reads a posting better than a regex does — but the rule-based parser is a
        // complete implementation, not a placeholder, so both paths produce the same shape and
        // the same evidence discipline.
        var extract = model.IsConfigured
            ? await model.CompleteAsync<PostingExtract>(
                  "Lies die Stellenanzeige. Gib zu jedem Feld den EXAKTEN Textausschnitt an, aus dem " +
                  "du es gelesen hast — wörtlich, keine Zeichenpositionen.",
                  request.Text, OutputSchemas.PostingSchemaName, OutputSchemas.Posting, ct)
              ?? PostingParser.Parse(request.Text)
            : PostingParser.Parse(request.Text);

        var posting = new Posting
        {
            ProfileId = request.ProfileId,
            SourceText = request.Text,
            EmployerType = ParseEnum(request.EmployerType ?? extract.EmployerType, EmployerType.Mittelstand),
            RequirementsJson = JsonSerializer.Serialize(extract.Requirements),
        };
        ApplyFields(posting, extract.Fields);

        db.Postings.Add(posting);
        await db.SaveChangesAsync();

        return Ok(ToDto(posting));
    }

    /// <summary>
    /// The advert text behind a link, read but not parsed and not stored.
    ///
    /// The fetch is the SERVER's, not the phone's: a job portal answers a mobile browser with a
    /// different page than it answers a client that cannot run its JavaScript, and the machine that
    /// already reads postings is the one place to keep that dealt with. What comes back goes into
    /// the same field a pasted advert goes into — the user reads it and corrects it, and
    /// <see cref="Parse"/> takes it from there exactly as before.
    /// </summary>
    [HttpPost("from-link")]
    public async Task<IActionResult> FromLink([FromBody] ReadLinkRequest request,
        [FromServices] IHttpClientFactory clients, CancellationToken ct)
    {
        var address = PostingLinkReader.ReadAddress(request.Url);
        if (address is null) return RefusedProblem("Das ist keine Web-Adresse.", LinkNotAnAddressKind);

        var client = clients.CreateClient(LinkClient);
        string body;
        try
        {
            // What the name resolves to decides whether this server may fetch it at all. An advert
            // is on the public internet, and a link that points into the network Bewerbo itself
            // runs in is refused as unreachable rather than read out to the user.
            var resolved = await Dns.GetHostAddressesAsync(address.Host, ct);
            if (resolved.Length == 0 || !Array.TrueForAll(resolved, PostingLinkReader.IsPublicAddress))
            {
                return RefusedProblem("Die Seite hinter dem Link antwortet nicht.", LinkUnreachableKind);
            }

            var response = await client.GetAsync(address, ct);
            if (!response.IsSuccessStatusCode)
            {
                return RefusedProblem("Die Seite hinter dem Link antwortet nicht.", LinkUnreachableKind);
            }

            body = await response.Content.ReadAsStringAsync(ct);
        }
        catch (Exception failure) when (failure is HttpRequestException or TaskCanceledException
            or System.Net.Sockets.SocketException)
        {
            // A dead host, a name that does not resolve, a page that never finishes loading: from
            // where the user stands these are one thing, and none of them is this server's fault to
            // report as a 500.
            return RefusedProblem("Die Seite hinter dem Link antwortet nicht.", LinkUnreachableKind);
        }

        var text = PostingLinkReader.ToText(body);
        return PostingLinkReader.IsUsable(text)
            ? Ok(new PostingTextDto(text))
            : RefusedProblem("Auf dieser Seite steht kein Anzeigentext.", LinkNoTextKind);
    }

    [HttpGet("{id:guid}")]
    public async Task<IActionResult> Get(Guid id) =>
        await db.Postings.FindAsync(id) is { } posting ? Ok(ToDto(posting)) : NotFoundProblem(PostingMissing, PostingMissingKind);

    // Every extracted field is correctable — that is the point of showing it against its source.
    [HttpPatch("{id:guid}/fields")]
    public async Task<IActionResult> PatchField(Guid id, [FromBody] CorrectFieldRequest request)
    {
        var posting = await db.Postings.FindAsync(id);
        if (posting is null) return NotFoundProblem(PostingMissing, PostingMissingKind);

        var fields = JsonSerializer.Deserialize<List<ExtractedField>>(posting.FieldsJson) ?? [];
        var index = fields.FindIndex(f => f.Key == request.Key);
        var corrected = new ExtractedField
        {
            Key = request.Key,
            Value = request.Value,
            // A corrected value is the user's, not the posting's: it keeps no quote, so it is
            // never drawn against words it did not come from.
            Quote = "",
            Confidence = "sicher",
        };
        if (index >= 0) fields[index] = corrected; else fields.Add(corrected);

        posting.FieldsJson = JsonSerializer.Serialize(fields);
        ApplyFields(posting, fields);
        await db.SaveChangesAsync();
        return Ok(ToDto(posting));
    }

    [HttpPatch("{id:guid}/employer-type")]
    public async Task<IActionResult> PatchEmployerType(Guid id, [FromQuery, BindRequired] string type)
    {
        var posting = await db.Postings.FindAsync(id);
        if (posting is null) return NotFoundProblem(PostingMissing, PostingMissingKind);
        posting.EmployerType = ParseEnum(type, EmployerType.Mittelstand);
        await db.SaveChangesAsync();
        return Ok(ToDto(posting));
    }

    [HttpPost("{id:guid}/match")]
    public async Task<IActionResult> Match(Guid id)
    {
        var posting = await db.Postings.FindAsync(id);
        if (posting is null) return NotFoundProblem(PostingMissing, PostingMissingKind);

        var profile = await db.FullProfileAsync(posting.ProfileId);
        if (profile is null) return NotFoundProblem(ProfileController.ProfileMissing, ProfileController.ProfileMissingKind);

        var requirements = JsonSerializer.Deserialize<List<ExtractedRequirement>>(posting.RequirementsJson) ?? [];
        var match = RequirementMatcher.Match(profile, requirements);

        // What papers the advert wants to see, which is a separate reading from the Abgleich:
        // it comes off the whole advert text, not only off the requirements it listed.
        var demands = DocumentDemandService.Demands(profile, posting.SourceText, requirements);

        return Ok(new MatchDto(
            posting.Id, posting.Company, posting.Reference,
            match.Covered, match.Total, match.Percent,
            match.Requirements.Select(r => new RequirementDto(
                r.Text, StateName(r.State), r.Evidence, r.Action, r.Language,
                r.EvidenceKind, r.EvidenceArgs, r.ActionKind, r.ActionArgs)).ToList(),
            demands.Select(d => new DemandedDocumentDto(
                d.Kind.ToString(), d.Title, d.Quote, d.OnFile, d.TitleArgs)).ToList()));
    }

    internal const string PostingMissing = "Es gibt keine Stellenanzeige mit dieser Id.";
    internal const string PostingMissingKind = "posting_missing";

    /// <summary>The client that fetches a page behind a link. Configured in Program.cs.</summary>
    internal const string LinkClient = "posting-link";

    // Three kinds and not one, because each is a different thing for the user to do: correct the
    // address, try again later, or paste the advert by hand after all.
    internal const string LinkNotAnAddressKind = "link_not_an_address";
    internal const string LinkUnreachableKind = "link_unreachable";
    internal const string LinkNoTextKind = "link_no_text";

    private static void ApplyFields(Posting posting, IReadOnlyList<ExtractedField> fields)
    {
        posting.FieldsJson = JsonSerializer.Serialize(fields);
        foreach (var field in fields)
        {
            switch (field.Key)
            {
                case "contact": posting.ContactName = field.Value; break;
                case "contactRole": posting.ContactRole = field.Value; break;
                case "company": posting.Company = field.Value; break;
                case "companyAddress": posting.CompanyAddress = field.Value; break;
                case "reference": posting.Reference = field.Value; break;
                case "title": posting.JobTitle = field.Value; break;
                case "start": posting.StartDate = field.Value; break;
            }
        }
    }

    private static PostingDto ToDto(Posting posting)
    {
        var fields = JsonSerializer.Deserialize<List<ExtractedField>>(posting.FieldsJson) ?? [];
        var requirements = JsonSerializer.Deserialize<List<ExtractedRequirement>>(posting.RequirementsJson) ?? [];

        var located = fields.Select(f =>
        {
            // The quote is turned into a position HERE, against the stored source text — not by the
            // extractor, and not by the client. A quote that cannot be found yields span -1, and
            // the client then shows the field with no highlight rather than the wrong one.
            var span = EvidenceLocator.Locate(posting.SourceText, f.Quote);
            return new EvidenceFieldDto(
                f.Key, f.Value, f.Quote, f.Confidence,
                span?.Start ?? -1, span?.Length ?? 0);
        }).ToList();

        return new PostingDto(
            posting.Id, posting.SourceText, posting.EmployerType.ToString(),
            located, requirements.Select(r => r.Text).ToList());
    }
}
