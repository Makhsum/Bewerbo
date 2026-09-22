using System.Text.Json;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Services;
using Microsoft.EntityFrameworkCore;
using static Bewerbo.Api.Endpoints.DtoMapping;

namespace Bewerbo.Api.Endpoints;

public static class PostingEndpoints
{
    public static void MapPostingEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/postings").WithTags("Stellenanzeige");

        group.MapPost("/parse", async (
            ParsePostingRequest request, BewerboDbContext db, ILanguageModel model, CancellationToken ct) =>
        {
            if (string.IsNullOrWhiteSpace(request.Text)) return Results.BadRequest("Kein Anzeigentext.");

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

            return Results.Ok(ToDto(posting));
        });

        group.MapGet("/{id:guid}", async (Guid id, BewerboDbContext db) =>
            await db.Postings.FindAsync(id) is { } posting ? Results.Ok(ToDto(posting)) : Results.NotFound());

        // Every extracted field is correctable — that is the point of showing it against its source.
        group.MapPatch("/{id:guid}/fields", async (Guid id, CorrectFieldRequest request, BewerboDbContext db) =>
        {
            var posting = await db.Postings.FindAsync(id);
            if (posting is null) return Results.NotFound();

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
            return Results.Ok(ToDto(posting));
        });

        group.MapPatch("/{id:guid}/employer-type", async (Guid id, string type, BewerboDbContext db) =>
        {
            var posting = await db.Postings.FindAsync(id);
            if (posting is null) return Results.NotFound();
            posting.EmployerType = ParseEnum(type, EmployerType.Mittelstand);
            await db.SaveChangesAsync();
            return Results.Ok(ToDto(posting));
        });

        group.MapPost("/{id:guid}/match", async (Guid id, BewerboDbContext db) =>
        {
            var posting = await db.Postings.FindAsync(id);
            if (posting is null) return Results.NotFound();

            var profile = await ProfileEndpoints.Full(db, posting.ProfileId);
            if (profile is null) return Results.NotFound();

            var requirements = JsonSerializer.Deserialize<List<ExtractedRequirement>>(posting.RequirementsJson) ?? [];
            var match = RequirementMatcher.Match(profile, requirements);

            return Results.Ok(new MatchDto(
                posting.Id, posting.Company, posting.Reference,
                match.Covered, match.Total, match.Percent,
                match.Requirements.Select(r => new RequirementDto(
                    r.Text, StateName(r.State), r.Evidence, r.Action)).ToList()));
        });
    }

    internal static string StateName(RequirementState state) => state switch
    {
        RequirementState.Belegt => "belegt",
        RequirementState.Offen => "offen",
        _ => "nicht_belegt",
    };

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
