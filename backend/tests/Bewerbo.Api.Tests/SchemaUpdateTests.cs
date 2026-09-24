using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// An installation that has been running since an earlier version, and what it is missing.
///
/// Against a real SQLite database for the reason <see cref="ResourceOwnershipTests"/> uses one, and
/// here there is no alternative at all: what is asked is what the DATABASE holds, and the whole
/// defect was that the model and the database disagreed about it. A fake would answer out of the
/// model and pass every one of these while the bug stood.
///
/// The older version is made by taking away what it did not have — the three tables the sign-in
/// added and one enum column — from a database EF has just created. That is what such a machine
/// looks like, and building it by subtraction keeps the rest of the schema exactly what EF writes
/// rather than what a hand-written CREATE TABLE in a test file would drift into.
/// </summary>
public class SchemaUpdateTests
{
    [Fact]
    public void An_empty_database_is_created_whole_and_has_nothing_left_to_add()
    {
        // The second acceptance criterion of the card, and the one that would be easy to break:
        // whatever is done for the installation that is behind must leave the ordinary start alone.
        using var connection = OpenConnection();
        using var db = ContextOver(connection);
        db.Database.EnsureCreated();

        Assert.Empty(db.BringSchemaUpToDate());
    }

    [Fact]
    public void A_database_from_before_the_sign_in_receives_the_tables_and_the_column_it_never_got()
    {
        using var db = OldInstallation(out var connection);
        using (connection)
        {
            var changes = db.BringSchemaUpToDate();

            Assert.Contains("table Accounts", changes);
            Assert.Contains("table AuthTokens", changes);
            Assert.Contains("table PasswordResets", changes);
            Assert.Contains("column Profiles.Template", changes);
        }
    }

    [Fact]
    public void The_tables_it_adds_are_tables_a_sign_in_can_actually_be_written_into()
    {
        // The symptom this closes was a 500 on every sign-in, so the assertion is not that a table
        // exists but that the records the sign-in stores go in and come back — through the cascade
        // from the account to its sessions, which is part of what the table was missing.
        using var db = OldInstallation(out var connection);
        using (connection)
        {
            db.BringSchemaUpToDate();

            var account = new Account
            {
                Email = "olena.k@example.com",
                Password = "600000.aa.bb",
                ProfileId = db.Profiles.Select(p => p.Id).Single(),
            };
            account.Tokens.Add(new AuthToken { AccountId = account.Id, TokenHash = "cc" });
            db.Accounts.Add(account);
            db.SaveChanges();
            db.ChangeTracker.Clear();

            var stored = db.Accounts.Include(a => a.Tokens).Single();
            Assert.Equal("olena.k@example.com", stored.Email);
            Assert.Single(stored.Tokens);
        }
    }

    [Fact]
    public void The_profile_that_was_already_there_survives_it()
    {
        // The first acceptance criterion's second half, and the only thing about this change that
        // would be genuinely expensive to get wrong.
        using var db = OldInstallation(out var connection);
        using (connection)
        {
            db.BringSchemaUpToDate();
            db.ChangeTracker.Clear();

            var profile = db.Profiles.Include(p => p.Experience).Single();
            Assert.Equal("Olena", profile.FirstName);
            Assert.Equal("Kovalenko", profile.LastName);
            Assert.Equal("Pflegefachkraft", Assert.Single(profile.Experience).Position);
        }
    }

    [Fact]
    public void A_row_written_before_a_column_existed_stays_readable_through_it()
    {
        // The trap in adding a column to a table that already holds rows, and the reason the default
        // goes through the value converter: every enum here is stored BY NAME, and the empty text
        // value is the name of no member. A profile that came back with an unreadable Vorlage would
        // be the same 500 one table further on.
        using var db = OldInstallation(out var connection);
        using (connection)
        {
            db.BringSchemaUpToDate();
            db.ChangeTracker.Clear();

            var profile = db.Profiles.Single();
            Assert.Equal(CvTemplate.Klassisch, profile.Template);

            profile.Template = CvTemplate.Fachlich;
            db.SaveChanges();
            db.ChangeTracker.Clear();
            Assert.Equal(CvTemplate.Fachlich, db.Profiles.Single().Template);
        }
    }

    [Fact]
    public void What_a_row_gets_in_a_new_enum_column_is_the_name_of_the_member()
    {
        // What the test above cannot see, and what it missed for a whole run: Enum.Parse accepts a
        // NUMBER, so a column filled with 0 comes back as Klassisch and every round trip through EF
        // passes while the column holds something that is the name of nothing. Storing enums by name
        // is what makes renumbering one carry no data — a row that carries the number instead means
        // a different member the day a value is inserted in front of it. Ask the DATABASE what is in
        // the column; the context would parse it back either way.
        using var db = OldInstallation(out var connection);
        using (connection)
        {
            db.BringSchemaUpToDate();

            using var command = connection.CreateCommand();
            command.CommandText = "SELECT \"Template\" FROM \"Profiles\"";
            Assert.Equal(nameof(CvTemplate.Klassisch), command.ExecuteScalar() as string);
        }
    }

    [Fact]
    public void The_start_after_the_one_that_caught_up_finds_nothing_left_to_do()
    {
        // Every start runs this, so the second one has to be a no-op — an addition that repeated
        // itself would fail the whole start on the next boot.
        using var db = OldInstallation(out var connection);
        using (connection)
        {
            Assert.NotEmpty(db.BringSchemaUpToDate());
            Assert.Empty(db.BringSchemaUpToDate());
        }
    }

    // -- the installation that is behind -----------------------------------------------------------

    /// <summary>
    /// A database holding a profile with one job in it, and the schema of a version that knew
    /// neither the sign-in nor the Lebenslauf-Vorlage. The context handed back is the CURRENT one:
    /// the model is what the running backend has, the database is what the machine has, and that
    /// disagreement is the whole subject of this file.
    /// </summary>
    private static BewerboDbContext OldInstallation(out SqliteConnection connection)
    {
        connection = OpenConnection();
        var db = ContextOver(connection);
        db.Database.EnsureCreated();

        var profile = new Profile { FirstName = "Olena", LastName = "Kovalenko" };
        profile.Experience.Add(new ExperienceEntry
        {
            ProfileId = profile.Id,
            Position = "Pflegefachkraft",
            Employer = "Stadtklinik Augsburg",
        });
        db.Profiles.Add(profile);
        db.SaveChanges();
        db.ChangeTracker.Clear();

        // The dependants before the table they hang off, so the foreign keys SQLite enforces on this
        // connection are never left pointing at nothing.
        db.Database.ExecuteSqlRaw("DROP TABLE \"AuthTokens\"");
        db.Database.ExecuteSqlRaw("DROP TABLE \"PasswordResets\"");
        db.Database.ExecuteSqlRaw("DROP TABLE \"Accounts\"");
        db.Database.ExecuteSqlRaw("ALTER TABLE \"Profiles\" DROP COLUMN \"Template\"");

        return db;
    }

    private static SqliteConnection OpenConnection()
    {
        // The in-memory database lives exactly as long as the connection does, which is why the
        // connection is held open across the whole test — see ResourceOwnershipTests.
        var connection = new SqliteConnection("DataSource=:memory:");
        connection.Open();
        return connection;
    }

    private static BewerboDbContext ContextOver(SqliteConnection connection) => new(
        new DbContextOptionsBuilder<BewerboDbContext>().UseSqlite(connection).Options);
}
