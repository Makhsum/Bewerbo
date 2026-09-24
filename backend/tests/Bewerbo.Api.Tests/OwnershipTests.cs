using System.Reflection;
using System.Security.Claims;
using Bewerbo.Api.Contracts;
using Bewerbo.Api.Controllers;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// Who a record belongs to — the question every route of this API is now answered against.
///
/// Against a real SQLite database for the reason <see cref="ProfileAdoptionTests"/> uses one: what
/// is being asked is what the database actually holds about ownership, and a fake would answer out
/// of the model rather than out of the rows.
/// </summary>
public class ResourceOwnershipTests
{
    [Fact]
    public async Task A_profile_is_owned_by_itself()
    {
        await using var db = NewDatabase();
        var profile = ProfileWithEverything(db);

        Assert.Equal(profile, await ResourceOwnership.OwnerOfAsync(db, OwnedResource.Profile, profile));
    }

    [Fact]
    public async Task A_posting_an_application_and_a_document_name_the_profile_they_were_filed_under()
    {
        await using var db = NewDatabase();
        var profile = ProfileWithEverything(db);

        Assert.Equal(profile, await ResourceOwnership.OwnerOfAsync(
            db, OwnedResource.Posting, db.Postings.Single().Id));
        Assert.Equal(profile, await ResourceOwnership.OwnerOfAsync(
            db, OwnedResource.Application, db.Applications.Single().Id));
        Assert.Equal(profile, await ResourceOwnership.OwnerOfAsync(
            db, OwnedResource.Document, db.Documents.Single().Id));
    }

    /// <summary>
    /// A record that is not there answers null, which is the same thing a record belonging to
    /// somebody else answers as far as the caller is concerned: neither equals their own profile
    /// id, and the filter turns both into the 404 the route would have written anyway.
    /// </summary>
    [Theory]
    [InlineData(OwnedResource.Profile)]
    [InlineData(OwnedResource.Posting)]
    [InlineData(OwnedResource.Application)]
    [InlineData(OwnedResource.Document)]
    public async Task An_id_that_names_nothing_is_owned_by_nobody(OwnedResource resource)
    {
        await using var db = NewDatabase();
        ProfileWithEverything(db);

        Assert.Null(await ResourceOwnership.OwnerOfAsync(db, resource, Guid.NewGuid()));
    }

    /// <summary>The whole point: one user's records never answer with another user's profile.</summary>
    [Fact]
    public async Task Another_profiles_records_are_not_this_ones()
    {
        await using var db = NewDatabase();
        var mine = ProfileWithEverything(db);
        var theirs = ProfileWithEverything(db);

        var theirPosting = db.Postings.Single(p => p.ProfileId == theirs).Id;

        Assert.Equal(theirs, await ResourceOwnership.OwnerOfAsync(db, OwnedResource.Posting, theirPosting));
        Assert.NotEqual(mine, await ResourceOwnership.OwnerOfAsync(db, OwnedResource.Posting, theirPosting));
    }

    /// <summary>A profile with one of each record hanging off it, and the id of that profile.</summary>
    private static Guid ProfileWithEverything(BewerboDbContext db)
    {
        var profile = new Domain.Profile { FirstName = "Olena", LastName = "Kovalchuk" };
        db.Profiles.Add(profile);

        var posting = new Posting { ProfileId = profile.Id, SourceText = "Pflegekraft gesucht" };
        db.Postings.Add(posting);
        db.Applications.Add(new Application { ProfileId = profile.Id, PostingId = posting.Id });
        db.Documents.Add(new StoredDocument
        {
            ProfileId = profile.Id, Title = "Arbeitszeugnis", Kind = DocumentKind.Arbeitszeugnis,
        });

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

/// <summary>
/// That the rule reaches every route, checked by reading the controllers rather than by trusting
/// that nobody forgot one.
///
/// The same kind of test as OutputSchemas.AssertNoLayoutFields and the Android side's
/// EmojiFreeStringsTest: the failure it guards against is a route somebody writes NEXT year,
/// which no test written against today's routes would ever see. A controller that does not derive
/// from <see cref="BewerboController"/>, or an action that takes an id nothing declares the owner
/// of, is a hole in exactly the way the API had one before this card.
/// </summary>
public class AuthorisedRouteTests
{
    /// <summary>
    /// The three controllers a caller reaches before they have a session. Written out here so that
    /// adding a fourth is a decision somebody makes in this file rather than an attribute that
    /// slips by in a review.
    /// </summary>
    private static readonly HashSet<string> OpenToAnyone =
        [nameof(AuthController), nameof(HealthController), nameof(LegalController)];

    [Fact]
    public void Every_controller_of_this_api_is_behind_the_sign_in()
    {
        foreach (var controller in Controllers())
        {
            Assert.True(typeof(BewerboController).IsAssignableFrom(controller),
                $"{controller.Name} does not derive from BewerboController and is therefore outside [Authorize].");
        }
    }

    [Fact]
    public void Only_the_named_controllers_are_open_to_anyone()
    {
        foreach (var controller in Controllers())
        {
            var open = controller.GetCustomAttribute<AllowAnonymousAttribute>() is not null
                || controller.GetMethods().Any(m => m.GetCustomAttribute<AllowAnonymousAttribute>() is not null);

            Assert.Equal(OpenToAnyone.Contains(controller.Name), open);
        }
    }

    /// <summary>
    /// Every id that comes in through the ADDRESS is one the filter can resolve an owner for: a
    /// parameter called <c>profileId</c> is a profile by convention, and a parameter called
    /// <c>id</c> needs the controller to say what it names. A third name would be read by nobody.
    /// </summary>
    [Fact]
    public void Every_id_a_route_takes_is_one_the_filter_can_place()
    {
        foreach (var controller in Controllers().Where(c => !OpenToAnyone.Contains(c.Name)))
        {
            var declared = controller.GetCustomAttribute<IdNamesAttribute>();

            foreach (var action in controller.GetMethods().Where(m => m.DeclaringType == controller))
            {
                foreach (var id in action.GetParameters().Where(p => p.ParameterType == typeof(Guid)))
                {
                    Assert.True(id.Name is "profileId" || (id.Name is "id" && declared is not null),
                        $"{controller.Name}.{action.Name} takes a Guid '{id.Name}' that no ownership rule places.");
                }
            }
        }
    }

    private static IEnumerable<Type> Controllers() =>
        typeof(BewerboController).Assembly.GetTypes()
            .Where(t => t is { IsAbstract: false, IsClass: true } && typeof(ControllerBase).IsAssignableFrom(t));
}

/// <summary>
/// The one write route that CREATES a profile instead of naming one, and the hole a rule keyed on
/// "is the id in this address yours" cannot see: a POST carries no id, so nothing above the action
/// has anything to decide ownership of. It used to decide nobody — the record came back with a 201
/// and then answered 404 to its own creator, while <see cref="ProfileAdoption.IsAdoptableAsync"/>
/// offered what was in it to the next registration.
///
/// The action is driven directly rather than over HTTP, because what is being asked is which rows
/// it leaves behind, and a real SQLite database answers that the way
/// <see cref="ResourceOwnershipTests"/> does. Whether the caller may then NAME the id it got back
/// is <see cref="OwnershipFilter"/>'s question, and it reduces to the first test here: the filter
/// lets a profile id through exactly when it is the signed-in caller's own.
/// </summary>
public class ProfileCreationTests
{
    [Fact]
    public async Task The_profile_it_writes_onto_is_the_callers_own()
    {
        await using var db = NewDatabase();
        var theirs = AccountWithProfile(db, "kateryna@example.de");
        var mine = AccountWithProfile(db, "olena@example.de");

        var created = Assert.IsType<CreatedResult>(await Routes(db, mine).Create(Olena));

        Assert.Equal($"/api/profile/{mine}", created.Location);
        Assert.Equal("Olena", db.Profiles.Single(p => p.Id == mine).FirstName);
        Assert.Equal("", db.Profiles.Single(p => p.Id == theirs).FirstName);
    }

    /// <summary>
    /// Nothing ownerless is left behind — the half of the defect its creator never sees. An
    /// adoptable profile is one the next person to register may ask for by id and keep.
    /// </summary>
    [Fact]
    public async Task It_leaves_behind_no_profile_that_no_account_owns()
    {
        await using var db = NewDatabase();
        var mine = AccountWithProfile(db, "olena@example.de");

        await Routes(db, mine).Create(Olena);

        Assert.Equal(1, db.Profiles.Count());
        Assert.False(await ProfileAdoption.IsAdoptableAsync(db, mine));
    }

    /// <summary>
    /// The acceptance criterion in one test: the address that comes back is one its creator can
    /// use. All three of these answered 404 on the id the API had just handed out.
    /// </summary>
    [Fact]
    public async Task The_creator_can_read_change_and_delete_what_came_back()
    {
        await using var db = NewDatabase();
        var mine = AccountWithProfile(db, "olena@example.de");
        var routes = Routes(db, mine);

        var created = Assert.IsType<CreatedResult>(await routes.Create(Olena));
        var id = Assert.IsType<ProfileDto>(created.Value).Id;

        Assert.IsType<OkObjectResult>(await routes.Get(id));
        Assert.IsType<OkObjectResult>(await routes.PatchPerson(id, Olena with { City = "Fürth" }));
        Assert.IsType<NoContentResult>(await routes.Delete(id));
    }

    private static readonly PersonDto Olena = new(
        "uk", "Olena", "Kovalchuk", "Hauptstraße 3", "90402", "Nürnberg",
        "0911 123456", "olena@example.de", "1989-04-17", "Klassisch");

    /// <summary>
    /// The controller as a request reaches it: the database it writes to, and the session claim
    /// that says whose profile this is — the claim
    /// <see cref="SessionAuthenticationHandler"/> puts there for every signed-in call. The model is
    /// never asked anything on this route; see <see cref="NoModel"/>.
    /// </summary>
    private static ProfileController Routes(BewerboDbContext db, Guid profileId)
    {
        var identity = new ClaimsIdentity(
            [new Claim(SessionAuthentication.ProfileIdClaim, profileId.ToString())],
            SessionAuthentication.Scheme);

        return new ProfileController(db, new NoModel())
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext { User = new ClaimsPrincipal(identity) },
            },
        };
    }

    /// <summary>An account and the one profile it owns, as <c>POST /api/auth/register</c> leaves them.</summary>
    private static Guid AccountWithProfile(BewerboDbContext db, string email)
    {
        var profile = new Domain.Profile();
        db.Profiles.Add(profile);
        db.Accounts.Add(new Account { Email = email, ProfileId = profile.Id });
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
