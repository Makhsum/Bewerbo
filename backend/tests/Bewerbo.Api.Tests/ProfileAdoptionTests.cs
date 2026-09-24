using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// The one condition behind the door's migration offer. It is worth its own test because the
/// failure it guards against is silent on both sides: the door promises to keep a Lebenslauf, the
/// registration hands out an empty profile instead, and nothing in between reports a refusal.
///
/// Against a real SQLite database for the reason <see cref="AccountErasureTests"/> uses one — what
/// is being asked is what the database actually holds about ownership.
/// </summary>
public class ProfileAdoptionTests
{
    [Fact]
    public async Task A_profile_no_account_owns_may_be_kept()
    {
        await using var db = NewDatabase();
        var nameless = Profile(db);

        Assert.True(await ProfileAdoption.IsAdoptableAsync(db, nameless));
    }

    /// <summary>
    /// The case that was wrong. A phone restored from a backup still names the profile it was
    /// signed in to — bewerbo.xml rides along while the token file beside it is excluded — so the
    /// id is live, the profile is there, and it belongs to somebody. Offering to keep it is
    /// offering somebody else's Lebenslauf, and the registration would refuse without saying so.
    /// </summary>
    [Fact]
    public async Task A_profile_that_already_has_an_account_may_not()
    {
        await using var db = NewDatabase();
        var owned = Owned(db);

        Assert.False(await ProfileAdoption.IsAdoptableAsync(db, owned));
    }

    /// <summary>
    /// The same answer as a profile that is spoken for, deliberately. A caller who could tell the
    /// two apart could ask this endpoint whether an id names an account.
    /// </summary>
    [Fact]
    public async Task A_profile_that_is_no_longer_there_may_not()
    {
        await using var db = NewDatabase();

        Assert.False(await ProfileAdoption.IsAdoptableAsync(db, Guid.NewGuid()));
    }

    /// <summary>One account's ownership must not make another user's nameless profile unkeepable.</summary>
    [Fact]
    public async Task Another_accounts_profile_does_not_speak_for_this_one()
    {
        await using var db = NewDatabase();
        Owned(db);
        var nameless = Profile(db);

        Assert.True(await ProfileAdoption.IsAdoptableAsync(db, nameless));
    }

    private static Guid Profile(BewerboDbContext db)
    {
        var profile = new Domain.Profile { FirstName = "Olena", LastName = "Kovalchuk" };
        db.Profiles.Add(profile);
        db.SaveChanges();
        db.ChangeTracker.Clear();
        return profile.Id;
    }

    private static Guid Owned(BewerboDbContext db)
    {
        var id = Profile(db);
        db.Accounts.Add(new Account
        {
            Email = $"{Guid.NewGuid():n}@example.com",
            Password = PasswordHash.Create("acht-zeichen"),
            ProfileId = id,
        });
        db.SaveChanges();
        db.ChangeTracker.Clear();
        return id;
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
