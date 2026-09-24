using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// "Delete my data" has to mean all of it. This runs against a real SQLite database for the reason
/// <see cref="ProfileOrderTests"/> does — what is being tested is what the DELETE actually reaches,
/// and an in-memory fake would cascade wherever the model says it does whether the database agrees
/// or not.
/// </summary>
public class AccountErasureTests
{
    [Fact]
    public async Task Erasing_an_account_leaves_nothing_of_the_profile_behind()
    {
        await using var db = NewDatabase();
        var id = Filled(db);

        Assert.True(await AccountErasure.EraseAsync(db, id));

        Assert.Null(await db.Profiles.FindAsync(id));
        Assert.Empty(db.Experience);
        Assert.Empty(db.Education);
        Assert.Empty(db.Languages);
        Assert.Empty(db.Gaps);
        Assert.Empty(db.Documents);
        Assert.Empty(db.Applications);
    }

    /// <summary>
    /// The one record the cascade does not reach. A Posting names a ProfileId with no relationship
    /// behind it, so an erasure that trusted the cascade left the full text of every advert the user
    /// had pasted on the server under their own profile id — and nothing on the way out said so.
    /// </summary>
    [Fact]
    public async Task Erasing_an_account_takes_the_postings_that_no_cascade_reaches()
    {
        await using var db = NewDatabase();
        var id = Filled(db);

        await AccountErasure.EraseAsync(db, id);

        Assert.Empty(db.Postings);
    }

    /// <summary>
    /// The second record no cascade reaches, and the one that holds a name. An erasure that took
    /// the profile but left the <see cref="Account"/> would leave the user's e-mail address on the
    /// server after they asked to be forgotten, and leave every phone they were signed in on
    /// pointing at a profile that is gone.
    /// </summary>
    [Fact]
    public async Task Erasing_an_account_takes_the_sign_in_and_its_sessions_with_it()
    {
        await using var db = NewDatabase();
        var id = Filled(db);

        await AccountErasure.EraseAsync(db, id);

        Assert.Empty(db.Accounts);
        Assert.Empty(db.AuthTokens);
    }

    /// <summary>
    /// And the reset the account may have had in flight when it asked to be forgotten. A live code
    /// left behind would be a way to claim an address on a server that no longer holds the account
    /// it named.
    /// </summary>
    [Fact]
    public async Task Erasing_an_account_takes_a_password_reset_that_was_still_open()
    {
        await using var db = NewDatabase();
        var id = Filled(db);

        await AccountErasure.EraseAsync(db, id);

        Assert.Empty(db.PasswordResets);
    }

    [Fact]
    public async Task Erasing_another_account_leaves_this_one_untouched()
    {
        await using var db = NewDatabase();
        var mine = Filled(db);
        var theirs = Filled(db);

        await AccountErasure.EraseAsync(db, theirs);

        Assert.NotNull(await db.Profiles.FindAsync(mine));
        Assert.Single(db.Postings);
        Assert.Single(db.Applications);
        Assert.Single(db.Accounts);
    }

    [Fact]
    public async Task An_account_that_does_not_exist_is_reported_rather_than_claimed_as_erased()
    {
        await using var db = NewDatabase();

        Assert.False(await AccountErasure.EraseAsync(db, Guid.NewGuid()));
    }

    /// <summary>An account with something of every kind under it, including the two that hang off no
    /// navigation property.</summary>
    private static Guid Filled(BewerboDbContext db)
    {
        var profile = new Profile
        {
            FirstName = "Olena", LastName = "Kovalchuk", City = "Nürnberg",
            Experience = [new ExperienceEntry { Position = "Buchhalterin", From = new DateOnly(2019, 4, 1) }],
            Education = [new EducationEntry { Degree = "Bakalavr", From = new DateOnly(2013, 9, 1) }],
            Languages = [new LanguageSkill { Language = "Ukrainisch", Level = "Muttersprache" }],
            Gaps = [new GapExplanation { From = new DateOnly(2023, 8, 1), To = new DateOnly(2024, 1, 1), Reason = "Umzug" }],
            Documents = [new StoredDocument { Title = "Arbeitszeugnis", Kind = DocumentKind.Arbeitszeugnis }],
        };
        db.Profiles.Add(profile);
        db.SaveChanges();

        var posting = new Posting { ProfileId = profile.Id, Company = "Siemens AG", SourceText = "Wir suchen …" };
        db.Postings.Add(posting);
        db.Applications.Add(new Application { ProfileId = profile.Id, PostingId = posting.Id });
        db.Accounts.Add(new Account
        {
            Email = $"{Guid.NewGuid():n}@example.com",
            Password = PasswordHash.Create("acht-zeichen"),
            ProfileId = profile.Id,
            Tokens = [new AuthToken { TokenHash = SessionToken.HashOf(SessionToken.Issue()) }],
            PasswordResets =
            [
                new PasswordReset
                {
                    CodeHash = PasswordHash.Create(ResetCode.Issue()),
                    ExpiresAt = DateTimeOffset.UtcNow + ResetCode.Lifetime,
                },
            ],
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
