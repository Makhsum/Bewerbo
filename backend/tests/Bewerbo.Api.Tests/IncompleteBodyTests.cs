using System.Security.Claims;
using System.Text.Json;
using Bewerbo.Api.Contracts;
using Bewerbo.Api.Controllers;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// What a write route answers a body that is not complete — one class of client mistake, and until
/// now two different answers to it.
///
/// A positional record deserialises an omitted member as null even where the type says it is not
/// nullable, so leaving a text field out of the JSON does not fail the binder: it fails later,
/// against a NOT NULL column, as a 500 with an empty body. The profile sections were taught to say
/// which field is missing instead; <c>POST /api/documents</c> was not, and a client met both shapes
/// for the same mistake. The bodies here are DESERIALISED rather than constructed, because what is
/// being asked is what the omission actually produces — a hand-written <c>null!</c> would assert
/// against the assumption instead of against System.Text.Json.
/// </summary>
public class IncompleteBodyTests
{
    [Fact]
    public async Task A_document_body_without_a_title_is_refused_and_the_field_is_named()
    {
        await using var db = NewDatabase();
        var profile = ProfileOf(db);

        var refusal = await Documents(db).Add(profile, DocumentFrom("""
            { "kind": "Arbeitszeugnis", "note": "", "pageCount": 1 }
            """));

        Assert.IsType<BadRequestObjectResult>(refusal);
        Assert.Contains("title", FieldsAtFault(refusal));
    }

    /// <summary>
    /// The acceptance criterion the status code alone does not cover: a refusal is not a half-write.
    /// The check stands before anything is handed to the change tracker, so there is nothing to
    /// undo — but that is the kind of ordering a later edit moves without noticing.
    /// </summary>
    [Fact]
    public async Task A_refused_document_leaves_no_record_behind()
    {
        await using var db = NewDatabase();
        var profile = ProfileOf(db);

        await Documents(db).Add(profile, DocumentFrom("""
            { "kind": "Arbeitszeugnis", "note": "", "pageCount": 1 }
            """));

        Assert.Empty(db.Documents);
    }

    /// <summary>
    /// The other half of the answer, and the reason this is not simply "every field is required":
    /// a note is something a document may be without. Left out it is no note, the way an employer
    /// or a workload left out of a position is — see <see cref="ProfileController.PatchExperience"/>.
    /// It reached the database as null and came back as the same 500 the missing title did.
    /// </summary>
    [Fact]
    public async Task A_document_body_without_a_note_is_stored_with_no_note()
    {
        await using var db = NewDatabase();
        var profile = ProfileOf(db);

        var created = await Documents(db).Add(profile, DocumentFrom("""
            { "title": "Klinikum Ost", "kind": "Arbeitszeugnis", "pageCount": 1 }
            """));

        Assert.IsType<CreatedResult>(created);
        Assert.Equal("", db.Documents.Single().Note);
    }

    /// <summary>
    /// The card's second criterion, asked of both routes at once: one client mistake, one shape.
    /// Both answer 400 with the field at fault under "errors", which is where the framework's own
    /// binding failures land — so a client reads all three the same way.
    /// </summary>
    [Fact]
    public async Task The_refusal_has_the_shape_the_profile_sections_already_use()
    {
        await using var db = NewDatabase();
        var profile = ProfileOf(db);

        var document = await Documents(db).Add(profile, DocumentFrom("""
            { "kind": "Arbeitszeugnis", "note": "", "pageCount": 1 }
            """));
        var experience = await Profiles(db, profile).PatchExperience(profile, ExperienceFrom("""
            [{ "employer": "Klinikum Ost", "location": "Nürnberg", "from": "2020-01-01" }]
            """));

        Assert.IsType<BadRequestObjectResult>(experience);
        Assert.IsType<BadRequestObjectResult>(document);
        Assert.Contains("position", FieldsAtFault(experience));
        Assert.Contains("title", FieldsAtFault(document));
    }

    // -- helpers ----------------------------------------------------------------------------------

    /// <summary>The body as the binder hands it over: parsed from JSON, with the member left out.</summary>
    private static DocumentDto DocumentFrom(string json) =>
        JsonSerializer.Deserialize<DocumentDto>(json, Wire)!;

    private static ExperienceDto[] ExperienceFrom(string json) =>
        JsonSerializer.Deserialize<ExperienceDto[]>(json, Wire)!;

    private static readonly JsonSerializerOptions Wire = new(JsonSerializerDefaults.Web);

    /// <summary>The fields a refusal names, out of the "errors" member both routes report under.</summary>
    private static IEnumerable<string> FieldsAtFault(IActionResult refusal) =>
        Assert.IsType<ValidationProblemDetails>(Assert.IsType<BadRequestObjectResult>(refusal).Value).Errors.Keys;

    /// <summary>
    /// The controller as a request reaches it, with the services <see cref="ControllerBase.Problem"/>
    /// and <see cref="ControllerBase.ValidationProblem"/> build their answer out of — a bare
    /// <see cref="DefaultHttpContext"/> has no ProblemDetailsFactory, and the shape these tests are
    /// about is exactly the one that factory writes.
    /// </summary>
    private static DocumentsController Documents(BewerboDbContext db) =>
        new(db) { ControllerContext = RequestContext() };

    private static ProfileController Profiles(BewerboDbContext db, Guid profileId)
    {
        var identity = new ClaimsIdentity(
            [new Claim(SessionAuthentication.ProfileIdClaim, profileId.ToString())],
            SessionAuthentication.Scheme);
        var context = RequestContext();
        context.HttpContext.User = new ClaimsPrincipal(identity);
        return new ProfileController(db, new NoModel()) { ControllerContext = context };
    }

    private static ControllerContext RequestContext() =>
        new() { HttpContext = new DefaultHttpContext { RequestServices = Mvc } };

    private static readonly IServiceProvider Mvc =
        new ServiceCollection().AddLogging().AddControllers().Services.BuildServiceProvider();

    /// <summary>A profile to file the document under, as registration leaves one.</summary>
    private static Guid ProfileOf(BewerboDbContext db)
    {
        var profile = new Domain.Profile { FirstName = "Olena", LastName = "Kovalchuk" };
        db.Profiles.Add(profile);
        db.Accounts.Add(new Account { Email = "olena@example.de", ProfileId = profile.Id });
        db.SaveChanges();
        db.ChangeTracker.Clear();
        return profile.Id;
    }

    private static BewerboDbContext NewDatabase()
    {
        var connection = new SqliteConnection("DataSource=:memory:");
        connection.Open();
        var db = new BewerboDbContext(
            new DbContextOptionsBuilder<BewerboDbContext>().UseSqlite(connection).Options);
        db.Database.EnsureCreated();
        return db;
    }
}
