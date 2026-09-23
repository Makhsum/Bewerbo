using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// The order the user put things in is the order they get them back in. Not a preference: the
/// Sprachen of a Lebenslauf say "Ukrainisch — Muttersprache" first because the person decided they
/// should, and an Include with no OrderBy hands them back in Guid order, which reads as a different
/// person. These run against a real SQLite database because that is where the bug was — the query,
/// not the mapping.
/// </summary>
public class ProfileOrderTests
{
    [Fact]
    public async Task The_Sprachen_come_back_in_the_order_they_were_saved()
    {
        await using var db = NewDatabase();
        var profile = Saved(db, p =>
        {
            p.Languages =
            [
                new LanguageSkill { Ordinal = 0, Language = "Ukrainisch", Level = "Muttersprache" },
                new LanguageSkill { Ordinal = 1, Language = "Deutsch", Level = "B2" },
                new LanguageSkill { Ordinal = 2, Language = "Englisch", Level = "B1" },
            ];
        });

        var loaded = await db.FullProfileAsync(profile.Id);

        Assert.Equal(
            ["Ukrainisch", "Deutsch", "Englisch"],
            loaded!.Languages.Select(l => l.Language));
    }

    [Fact]
    public async Task The_Anlagen_come_back_in_the_order_they_were_added_to_the_Mappe()
    {
        await using var db = NewDatabase();
        var added = new DateTimeOffset(2026, 9, 1, 8, 0, 0, TimeSpan.Zero);
        var profile = Saved(db, p =>
        {
            p.Documents =
            [
                new StoredDocument { Title = "Erst", Kind = DocumentKind.Arbeitszeugnis, AddedAt = added },
                new StoredDocument { Title = "Dann", Kind = DocumentKind.Sprachnachweis, AddedAt = added.AddMinutes(1) },
                new StoredDocument { Title = "Zuletzt", Kind = DocumentKind.AnabinAuszug, AddedAt = added.AddMinutes(2) },
            ];
        });

        var loaded = await db.FullProfileAsync(profile.Id);

        Assert.Equal(["Erst", "Dann", "Zuletzt"], loaded!.Documents.Select(d => d.Title));
    }

    [Fact]
    public async Task Berufserfahrung_comes_back_newest_first()
    {
        await using var db = NewDatabase();
        var profile = Saved(db, p =>
        {
            p.Experience =
            [
                new ExperienceEntry { Position = "Älter", From = new DateOnly(2019, 4, 1), To = new DateOnly(2023, 7, 31) },
                new ExperienceEntry { Position = "Neuer", From = new DateOnly(2023, 9, 1) },
            ];
        });

        var loaded = await db.FullProfileAsync(profile.Id);

        Assert.Equal(["Neuer", "Älter"], loaded!.Experience.Select(e => e.Position));
    }

    /// <summary>
    /// Two jobs that began in the same month are the case a single OrderBy still leaves open, and
    /// the one that made this hard to see: the list was right most of the time.
    /// </summary>
    [Fact]
    public async Task Entries_sharing_a_start_date_come_back_in_the_same_order_twice()
    {
        await using var db = NewDatabase();
        var sameMonth = new DateOnly(2021, 1, 1);
        var profile = Saved(db, p =>
        {
            p.Experience =
            [
                new ExperienceEntry { Position = "A", From = sameMonth },
                new ExperienceEntry { Position = "B", From = sameMonth },
                new ExperienceEntry { Position = "C", From = sameMonth },
            ];
        });

        var first = await db.FullProfileAsync(profile.Id);
        db.ChangeTracker.Clear();
        var second = await db.FullProfileAsync(profile.Id);

        Assert.Equal(
            first!.Experience.Select(e => e.Position),
            second!.Experience.Select(e => e.Position));
    }

    private static Profile Saved(BewerboDbContext db, Action<Profile> fill)
    {
        var profile = new Profile { FirstName = "Olena", LastName = "Kovalchuk" };
        fill(profile);
        db.Profiles.Add(profile);
        db.SaveChanges();
        db.ChangeTracker.Clear();
        return profile;
    }

    /// <summary>
    /// SQLite in memory, which the API already depends on for its own fallback provider. The
    /// connection is held open by the context for as long as the test needs it: closing it drops
    /// the database.
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
