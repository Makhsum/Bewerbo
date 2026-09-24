using System.Security.Claims;
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
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// The assistant — the one feature of this API whose real answer cannot be seen on a machine like
/// this one: every installation here runs <c>writer=regeln</c>, so the model is never called.
///
/// What CAN be pinned down is everything around that call, and that is what these do: the refusal
/// where no key is configured, the language the answer is asked for in, the closed list of German
/// words the prompt carries, and the profile going in so the assistant does not ask for what the
/// form already holds. The model is stubbed rather than called for the reason
/// <see cref="ProfileOrderTests"/> uses a real database: what is being asked is what the SERVER
/// sends and how it reacts, and a live call would answer a different sentence every run.
/// </summary>
public class AssistantTurnTests
{
    /// <summary>
    /// The kind on the wire, spelled out rather than read off the controller: it is what the app's
    /// errorMessage() matches on, so a rename that only touches the server is exactly the change
    /// this has to fail on.
    /// </summary>
    private const string UnavailableKind = "assistant_unavailable";

    [Fact]
    public async Task An_installation_with_no_model_says_so_instead_of_answering()
    {
        await using var db = NewDatabase();
        var model = new NoModel();
        var controller = Routes(db, model);

        var result = await controller.Turn(Turn("Ich habe sechs Jahre in Kiew gepflegt."),
            Conversation(model), default);

        var problem = Assert.IsType<ObjectResult>(result);
        Assert.Equal(StatusCodes.Status400BadRequest, problem.StatusCode);
        Assert.Equal(UnavailableKind,
            Assert.IsType<ProblemDetails>(problem.Value).Extensions["kind"]);
    }

    /// <summary>
    /// A model that answered with nothing is the same fact to the user as one that is not there —
    /// the assistant did not answer — so it comes back under the same kind. The screen has one
    /// sentence to say about both, which is the point.
    /// </summary>
    [Fact]
    public async Task A_model_that_answers_with_nothing_is_refused_as_unavailable()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply());
        var controller = Routes(db, model);

        var result = await controller.Turn(Turn("Ich bin Krankenschwester."), Conversation(model), default);

        var problem = Assert.IsType<ObjectResult>(result);
        Assert.Equal(UnavailableKind,
            Assert.IsType<ProblemDetails>(problem.Value).Extensions["kind"]);
    }

    [Fact]
    public async Task An_answer_carries_what_was_understood_and_what_is_missing()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply
        {
            Reply = "Зрозуміло: шість років догляду за пацієнтами в Києві.",
            Missing = ["Рік початку роботи", "Рівень німецької мови"],
        });
        var controller = Routes(db, model);

        var result = await controller.Turn(
            Turn("Я шість років працювала у лікарні в Києві."), Conversation(model), default);

        var reply = Assert.IsType<AssistantReplyDto>(Assert.IsType<OkObjectResult>(result).Value);
        Assert.StartsWith("Зрозуміло", reply.Reply);
        Assert.Equal(2, reply.Missing.Count);
    }

    /// <summary>
    /// A turn with nothing in it is refused before the model is paid for it. Whitespace is the case
    /// that matters: it is what a client sends when the composer holds only a newline.
    /// </summary>
    [Fact]
    public async Task A_turn_with_no_text_in_it_never_reaches_the_model()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply { Reply = "sollte nie gefragt werden" });
        var controller = Routes(db, model);

        var result = await controller.Turn(
            new AssistantTurnRequest("uk", [new AssistantMessageDto(true, "   ")]),
            Conversation(model), default);

        Assert.IsType<ObjectResult>(result);
        Assert.Equal("", model.LastSystem);
    }

    /// <summary>
    /// The interface language the client named has to reach the prompt: it is the only thing that
    /// can decide the language of an answer to a photographed document, where there is no wording
    /// of the user's own to tell it from.
    /// </summary>
    [Fact]
    public async Task The_interface_language_the_client_named_reaches_the_prompt()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply { Reply = "Готово" });
        var controller = Routes(db, model);

        await controller.Turn(new AssistantTurnRequest("ru", [new AssistantMessageDto(true, "Привет")]),
            Conversation(model), default);

        Assert.Contains("»ru«", model.LastSystem);
    }

    /// <summary>
    /// The guard nothing else can catch. GermanTermsTest scans strings.xml, and model prose never
    /// passes through it — so the only thing standing between a Russian reply and the word
    /// "Sprachkurs" in the middle of it is this list being in the system prompt.
    /// </summary>
    [Fact]
    public async Task The_prompt_names_the_only_German_words_the_answer_may_contain()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply { Reply = "ok" });
        var controller = Routes(db, model);

        await controller.Turn(Turn("Hallo"), Conversation(model), default);

        foreach (var term in new[] { "Lebenslauf", "Anschreiben", "Bewerbungsmappe", "anabin", "DIN 5008" })
        {
            Assert.Contains(term, model.LastSystem);
        }
    }

    /// <summary>
    /// The profile goes into the prompt so the assistant does not ask again for what the user has
    /// already typed into the form: the two ways in fill one profile.
    /// </summary>
    [Fact]
    public async Task What_the_profile_already_holds_goes_into_the_prompt()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply { Reply = "ok" });
        var controller = Routes(db, model);

        await controller.Turn(Turn("Und weiter?"), Conversation(model), default);

        Assert.Contains("Olena", model.LastUser);
        Assert.Contains("Nürnberg", model.LastUser);
        Assert.Contains("Pflegefachkraft", model.LastUser);
        Assert.Contains("Und weiter?", model.LastUser);
    }

    private static AssistantTurnRequest Turn(string text) =>
        new("de", [new AssistantMessageDto(true, text)]);

    private static AssistantConversation Conversation(ILanguageModel model) =>
        new(model, new NullLogger<AssistantConversation>());

    /// <summary>
    /// The controller as a request reaches it, the way <see cref="ResourceOwnershipTests"/> builds
    /// one: the database, and the session claim that says whose profile this is. The assistant is
    /// addressed by nothing else — there is no profile id in its body to stand in for the session.
    /// </summary>
    private static AssistantController Routes(BewerboDbContext db, ILanguageModel model)
    {
        var identity = new ClaimsIdentity(
            [new Claim(SessionAuthentication.ProfileIdClaim, db.Profiles.Single().Id.ToString())],
            SessionAuthentication.Scheme);

        return new AssistantController(db, model)
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext { User = new ClaimsPrincipal(identity) },
            },
        };
    }

    /// <summary>
    /// SQLite in memory, as <see cref="ApplicationSourceTests"/> uses it, holding the one profile
    /// the signed-in caller owns — with something in it, so the prompt has something to carry.
    /// </summary>
    private static BewerboDbContext NewDatabase()
    {
        var connection = new SqliteConnection("DataSource=:memory:");
        connection.Open();
        var db = new BewerboDbContext(
            new DbContextOptionsBuilder<BewerboDbContext>().UseSqlite(connection).Options);
        db.Database.EnsureCreated();

        var profile = new Profile
        {
            FirstName = "Olena", LastName = "Kovalchuk", City = "Nürnberg", InputLanguage = "uk",
        };
        db.Profiles.Add(profile);
        db.SaveChanges();
        db.Experience.Add(new ExperienceEntry
        {
            ProfileId = profile.Id,
            Position = "Pflegefachkraft",
            Employer = "Stadtklinik Kyjiw",
            From = new DateOnly(2018, 4, 1),
        });
        db.SaveChanges();
        db.ChangeTracker.Clear();
        return db;
    }

    /// <summary>
    /// A model that records what it was asked and answers what the test put in it. The counterpart
    /// of <see cref="NoModel"/>, which is the unconfigured case.
    /// </summary>
    private sealed class StubModel(AssistantReply answer) : ILanguageModel
    {
        public bool IsConfigured => true;
        public string LastSystem { get; private set; } = "";
        public string LastUser { get; private set; } = "";

        public Task<T?> CompleteAsync<T>(string system, string user, string schemaName, string schemaJson,
            CancellationToken ct = default)
        {
            LastSystem = system;
            LastUser = user;
            return Task.FromResult((T?)(object)answer);
        }
    }
}
