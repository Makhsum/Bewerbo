using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// Which writer produced a letter is told to the reader before they read it, so it has to survive
/// being put down and picked up again. It did not: nothing recorded it, and the reload handed back
/// the constant "gespeichert" — which the screen reads as "not the model" and turns into "written
/// by the rule-based writer, no language model is configured for this installation". A letter the
/// model wrote said that about itself every time it was reopened.
///
/// These run against a real SQLite database for the reason <see cref="ProfileOrderTests"/> does:
/// what was missing was the column, and a fake that keeps objects in memory has every column.
/// </summary>
public class ApplicationSourceTests
{
    [Fact]
    public async Task A_letter_the_model_wrote_still_says_so_after_a_reload()
    {
        await using var db = NewDatabase();
        var id = Saved(db, a => a.Source = "model");

        var reloaded = await db.Applications.FindAsync(id);

        Assert.Equal("model", reloaded!.Source);
    }

    [Fact]
    public async Task A_letter_the_rules_wrote_still_says_so_after_a_reload()
    {
        await using var db = NewDatabase();
        var id = Saved(db, a => a.Source = "regeln");

        var reloaded = await db.Applications.FindAsync(id);

        Assert.Equal("regeln", reloaded!.Source);
    }

    /// <summary>
    /// The screen has two branches and no third one, so an application that never had a writer
    /// written down has to land on the rules rather than on an empty string that reads as nothing.
    /// </summary>
    [Fact]
    public async Task An_application_saved_without_a_writer_comes_back_as_the_rules()
    {
        await using var db = NewDatabase();
        var id = Saved(db, application => { });

        var reloaded = await db.Applications.FindAsync(id);

        Assert.Equal("regeln", reloaded!.Source);
    }

    /// <summary>
    /// Regenerating is where the two writers can swap places — the model answers this time, or a
    /// key has been set since. The stored writer follows the letter it belongs to.
    /// </summary>
    [Fact]
    public async Task Rewriting_a_letter_with_the_other_writer_moves_the_stored_writer_with_it()
    {
        await using var db = NewDatabase();
        var id = Saved(db, a => a.Source = "regeln");

        var application = await db.Applications.FindAsync(id);
        application!.Source = "model";
        await db.SaveChangesAsync();
        db.ChangeTracker.Clear();

        Assert.Equal("model", (await db.Applications.FindAsync(id))!.Source);
    }

    /// <summary>
    /// An Application hangs off a Profile by a real relationship, so the row it needs has to be
    /// there — the same shape <see cref="AccountErasureTests"/> builds for the same reason.
    /// </summary>
    private static Guid Saved(BewerboDbContext db, Action<Application> fill)
    {
        var profile = new Profile { FirstName = "Olena", LastName = "Kovalchuk" };
        db.Profiles.Add(profile);
        db.SaveChanges();

        var posting = new Posting { ProfileId = profile.Id, Company = "Nordwind Pflege GmbH" };
        db.Postings.Add(posting);

        var application = new Application { ProfileId = profile.Id, PostingId = posting.Id };
        fill(application);
        db.Applications.Add(application);
        db.SaveChanges();
        db.ChangeTracker.Clear();
        return application.Id;
    }

    /// <summary>
    /// SQLite in memory, as <see cref="ProfileOrderTests"/> uses it. The connection is held open
    /// by the context for as long as the test needs it: closing it drops the database.
    /// </summary>
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
