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
    /// The pair the user decides about — the German and the words it was read from — has to reach
    /// the client whole. A proposal whose source is dropped on the way is a German sentence with
    /// nothing to compare it against, which is the thing this card exists to prevent.
    /// </summary>
    [Fact]
    public async Task A_proposal_reaches_the_client_with_the_users_own_wording_beside_the_german()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply
        {
            Reply = "Зрозуміло.",
            Proposals =
            [
                new AssistantProposal
                {
                    Kind = "berufserfahrung", Source = "я працювала у лікарні в Києві",
                    Title = "Pflegefachkraft", Detail = "Stadtklinik Kyjiw",
                    From = "2018-04", To = "",
                },
            ],
        });
        var controller = Routes(db, model);

        var result = await controller.Turn(
            Turn("Я шість років працювала у лікарні в Києві, з 2018 року."), Conversation(model), default);

        var reply = Assert.IsType<AssistantReplyDto>(Assert.IsType<OkObjectResult>(result).Value);
        var proposal = Assert.Single(reply.Proposals);
        Assert.Equal("berufserfahrung", proposal.Kind);
        Assert.Equal("я працювала у лікарні в Києві", proposal.Source);
        Assert.Equal("Pflegefachkraft", proposal.Title);
        Assert.Equal("Stadtklinik Kyjiw", proposal.Detail);
        // Widened to the shape PATCH /sections/berufserfahrung parses, and nothing else touched.
        Assert.Equal("2018-04-01", proposal.From);
        Assert.Equal("", proposal.To);
    }

    /// <summary>
    /// A proposal the profile's own routes would refuse is dropped rather than drawn: a card
    /// offering a save that then fails is worse than one that was never offered. The three ways it
    /// can be unusable, in one answer — an unknown section, no name, and no start date on a station.
    /// </summary>
    [Fact]
    public async Task A_proposal_the_profile_could_not_take_is_dropped_before_it_is_offered()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply
        {
            Reply = "ok",
            Proposals =
            [
                new AssistantProposal
                {
                    Kind = "hobbys", Source = "я люблю шахи", Title = "Schach", From = "2010-01",
                },
                new AssistantProposal
                {
                    Kind = "berufserfahrung", Source = "я працювала", Title = "", From = "2010-01",
                },
                new AssistantProposal
                {
                    Kind = "berufserfahrung", Source = "колись давно у магазині",
                    Title = "Verkäuferin", From = "",
                },
                new AssistantProposal
                {
                    Kind = "sprachen", Source = "українська рідна",
                    Title = "Ukrainisch", Detail = "Muttersprache",
                },
            ],
        });
        var controller = Routes(db, model);

        var result = await controller.Turn(Turn("Erzähl ich mal."), Conversation(model), default);

        var reply = Assert.IsType<AssistantReplyDto>(Assert.IsType<OkObjectResult>(result).Value);
        // The language survives without a date; a language has none, and none is asked of it.
        var proposal = Assert.Single(reply.Proposals);
        Assert.Equal("sprachen", proposal.Kind);
        Assert.Equal("", proposal.From);
    }

    /// <summary>
    /// The header of the Lebenslauf is the one thing the conversation could not supply before, and
    /// a document with no name on it is not one the user can keep. It travels beside the sentence it
    /// was read from, as a proposal does — the user is deciding about a pair here too.
    /// </summary>
    [Fact]
    public async Task The_person_the_conversation_named_reaches_the_client_with_that_sentence()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply
        {
            Reply = "Записала адресу.",
            Person = new AssistantPerson
            {
                Source = "живу на Hauptstraße 12 у Штутгарті, мій телефон 0711 445566",
                Street = "Hauptstraße 12", PostalCode = "70173", Phone = "0711 445566",
            },
        });
        var controller = Routes(db, model);

        var result = await controller.Turn(
            Turn("Живу на Hauptstraße 12 у Штутгарті, мій телефон 0711 445566."),
            Conversation(model), default);

        var reply = Assert.IsType<AssistantReplyDto>(Assert.IsType<OkObjectResult>(result).Value);
        var person = Assert.IsType<AssistantPersonDto>(reply.Person);
        Assert.StartsWith("живу на Hauptstraße 12", person.Source);
        Assert.Equal("Hauptstraße 12", person.Street);
        Assert.Equal("70173", person.PostalCode);
        Assert.Equal("0711 445566", person.Phone);
    }

    /// <summary>
    /// A field the form already holds is not offered again. Accepting appends everywhere else in
    /// this API; for the person it would OVERWRITE, and a card that quietly replaced the Anschrift
    /// the user typed would be a card they had no reason to read carefully.
    /// </summary>
    [Fact]
    public async Task A_person_field_the_profile_already_holds_is_not_offered_back()
    {
        await using var db = NewDatabase();
        // The profile in the database is Olena Kovalchuk in Nürnberg, with no street and no e-mail.
        var model = new StubModel(new AssistantReply
        {
            Reply = "ok",
            Person = new AssistantPerson
            {
                Source = "Олена Ковальчук, Київ, olena@example.ua",
                FirstName = "Olena", LastName = "Kovalchuk", City = "Kyjiw",
                Street = "Chreschtschatyk 1", Email = "olena@example.ua",
            },
        });
        var controller = Routes(db, model);

        var result = await controller.Turn(Turn("Олена Ковальчук, Київ."), Conversation(model), default);

        var reply = Assert.IsType<AssistantReplyDto>(Assert.IsType<OkObjectResult>(result).Value);
        var person = Assert.IsType<AssistantPersonDto>(reply.Person);
        Assert.Equal("", person.FirstName);
        Assert.Equal("", person.LastName);
        // The city on file is Nürnberg; the one in the sentence is where the user came FROM.
        Assert.Equal("", person.City);
        Assert.Equal("Chreschtschatyk 1", person.Street);
        Assert.Equal("olena@example.ua", person.Email);
    }

    /// <summary>
    /// Nothing left to decide, so no card at all — not an empty one. A card with a quoted sentence
    /// and no field under it asks the user to accept nothing.
    /// </summary>
    [Fact]
    public async Task A_turn_that_names_nothing_the_profile_lacks_offers_no_person_at_all()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply
        {
            Reply = "ok",
            Person = new AssistantPerson
            {
                Source = "мене звати Олена Ковальчук", FirstName = "Olena", LastName = "Kovalchuk",
            },
        });
        var controller = Routes(db, model);

        var result = await controller.Turn(Turn("Мене звати Олена Ковальчук."), Conversation(model), default);

        var reply = Assert.IsType<AssistantReplyDto>(Assert.IsType<OkObjectResult>(result).Value);
        Assert.Null(reply.Person);
    }

    /// <summary>
    /// The prompt has to say that title and detail are German and that source is the user's own
    /// words, because nothing downstream can tell the two apart — a proposal whose German half
    /// came back in Ukrainian would be saved into the profile exactly as it arrived.
    /// </summary>
    [Fact]
    public async Task The_prompt_says_which_half_of_a_proposal_is_german_and_which_is_quoted()
    {
        await using var db = NewDatabase();
        var model = new StubModel(new AssistantReply { Reply = "ok" });
        var controller = Routes(db, model);

        await controller.Turn(Turn("Hallo"), Conversation(model), default);

        Assert.Contains("AUF DEUTSCH", model.LastSystem);
        Assert.Contains("WÖRTLICH", model.LastSystem);
        foreach (var section in new[] { "berufserfahrung", "ausbildung", "sprachen" })
        {
            Assert.Contains(section, model.LastSystem);
        }
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
