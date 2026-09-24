using System.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Metadata;
using Microsoft.EntityFrameworkCore.Migrations;
using Microsoft.EntityFrameworkCore.Migrations.Operations;
using Microsoft.EntityFrameworkCore.Storage;

namespace Bewerbo.Api.Data;

/// <summary>
/// What an installation that already holds data is missing, and the statements that give it to it.
///
/// <c>EnsureCreated</c> creates the whole schema and only while there is no database at all; against
/// one that exists it does NOTHING and answers false. So a machine that has been running since an
/// earlier version never receives a table a later one added — the sign-in's Accounts, AuthTokens and
/// PasswordResets were the first three, and every call that touched them answered 500 with a message
/// about a table that does not exist. Nothing on the way in said so, which is what made it read as a
/// broken feature rather than a schema left behind.
///
/// This closes that without introducing EF migrations. Migrations are provider-specific — this model
/// runs on PostgreSQL in production and on SQLite on every dev machine, see Program.cs — and each
/// database already out there would need a baseline applied to it by hand, which is exactly the
/// manual step the card exists to remove. What happens instead is read off the model at startup: the
/// tables and columns EF expects, minus the ones the database already has, generated as DDL by the
/// provider's OWN migrations SQL generator. Nothing is ever dropped, altered or renamed, so there is
/// no path through here that can lose a row.
///
/// The limit that follows from that is deliberate and worth knowing: this ADDS. A column whose type
/// changed, a table that was renamed, a constraint that got stricter — none of those are seen, and
/// none of them may be shipped without a migration written by hand. What the model has gained from
/// one version to the next, in this product so far, is a new entity or a new field.
/// </summary>
public static class SchemaUpdate
{
    /// <summary>
    /// Creates every table and column the model has and the database has not, and answers what it
    /// created — one entry per table and per column, so the caller can say on the way up WHICH
    /// installation was behind and in what. An empty answer means the database was already current,
    /// which is the normal case and the one a fresh <c>EnsureCreated</c> leaves behind.
    /// </summary>
    public static IReadOnlyList<string> BringSchemaUpToDate(this BewerboDbContext db)
    {
        // The DESIGN-TIME model and not db.Model. The model the context runs queries against is
        // trimmed of everything only schema work needs, and the differ throws on the first table it
        // asks a question of. This is the same model the EF tooling would diff.
        var designTimeModel = db.GetService<IDesignTimeModel>().Model;
        var model = designTimeModel.GetRelationalModel();
        var present = ReadPresentSchema(db);

        var operations = new List<MigrationOperation>();
        var changes = new List<string>();

        // The tables first, because a column of a table that is not there yet is created with it.
        var missingTables = model.Tables
            .Where(table => !present.HasTable(table.Name))
            .Select(table => table.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        if (missingTables.Count > 0)
        {
            // Diffing the model against NOTHING is how EF describes a schema from scratch: one
            // CreateTable per table, with its keys and its foreign keys inside it, followed by the
            // CreateIndex operations. Keeping only the operations that name a missing table leaves
            // exactly the new ones, in the order the differ already sorted them into — a new table
            // that points at another new one still comes after it.
            var differ = db.GetService<IMigrationsModelDiffer>();
            operations.AddRange(differ.GetDifferences(null, model)
                .Where(operation => TableOf(operation) is { } name && missingTables.Contains(name)));
            changes.AddRange(missingTables.OrderBy(name => name, StringComparer.Ordinal)
                .Select(name => $"table {name}"));
        }

        // Then the columns of the tables that were already there.
        foreach (var table in model.Tables.Where(table => !missingTables.Contains(table.Name)))
        {
            foreach (var column in table.Columns.Where(column => !present.HasColumn(table.Name, column.Name)))
            {
                operations.Add(AddColumn(table, column));
                changes.Add($"column {table.Name}.{column.Name}");
            }
        }

        if (operations.Count == 0)
        {
            return [];
        }

        var commands = db.GetService<IMigrationsSqlGenerator>().Generate(operations, designTimeModel);
        db.GetService<IMigrationCommandExecutor>()
            .ExecuteNonQuery(commands, db.GetService<IRelationalConnection>());

        return changes;
    }

    /// <summary>
    /// Which table an operation belongs to, or null for one that belongs to none. Only the
    /// table-scoped kinds a from-scratch diff of THIS model produces are listed; a sequence or a
    /// schema would come back null and be left out, which is the right answer for a model that has
    /// neither and an honest one for a model that grows them — an operation this does not recognise
    /// is skipped, never guessed at.
    /// </summary>
    private static string? TableOf(MigrationOperation operation) => operation switch
    {
        CreateTableOperation create => create.Name,
        CreateIndexOperation index => index.Table,
        AddForeignKeyOperation foreignKey => foreignKey.Table,
        AddPrimaryKeyOperation primaryKey => primaryKey.Table,
        AddUniqueConstraintOperation unique => unique.Table,
        AddCheckConstraintOperation check => check.Table,
        _ => null,
    };

    /// <summary>
    /// One column, described the way the provider's generator wants it.
    ///
    /// The default value is what makes this safe on a table that already holds rows: both PostgreSQL
    /// and SQLite refuse to add a NOT NULL column without one, because the rows already there would
    /// have nothing in it. A nullable column gets no default and the old rows get null, which is the
    /// honest answer — the version that wrote them did not know the field.
    /// </summary>
    private static AddColumnOperation AddColumn(ITable table, IColumn column) => new()
    {
        Table = table.Name,
        Schema = table.Schema,
        Name = column.Name,
        ClrType = column.ProviderClrType,
        ColumnType = column.StoreType,
        IsNullable = column.IsNullable,
        DefaultValue = column.IsNullable ? null : EmptyValueFor(column),
    };

    /// <summary>
    /// What a row written before the column existed gets to carry: the empty value of the type the
    /// ENTITY declares, put through the conversion on its way into the database.
    ///
    /// The conversion is the whole point of doing it this way round rather than off the store type.
    /// Every enum in this model is stored by name — see <see cref="BewerboDbContext.OnModelCreating"/>
    /// — so the empty value of a text column would be the empty string, and an empty string is not
    /// the name of any member: the old rows would be unreadable the moment EF tried to materialise
    /// them. The empty CvTemplate is Klassisch, and "Klassisch" is what belongs in the column.
    /// </summary>
    private static object EmptyValueFor(IColumn column)
    {
        var mapping = column.PropertyMappings.First();
        var property = mapping.Property;
        var clrType = Nullable.GetUnderlyingType(property.ClrType) ?? property.ClrType;

        object empty = clrType == typeof(string) ? ""
            : clrType == typeof(byte[]) ? Array.Empty<byte>()
            : Activator.CreateInstance(clrType) ?? "";

        // The converter of the COLUMN's mapping and not only the property's. HasConversion<string>()
        // names the provider type and lets the type mapping build the converter, so
        // GetValueConverter() answers null for every enum in this model — and the raw enum reached
        // the generator, which wrote its NUMBER into a column that holds names.
        var converter = property.GetValueConverter() ?? mapping.TypeMapping.Converter;
        return converter?.ConvertToProvider(empty) ?? empty;
    }

    /// <summary>
    /// The tables and columns the database actually has, as a table name and a table.column per row.
    ///
    /// The one place in here that has to know which provider it is talking to, and it branches the
    /// same way Program.cs does. PostgreSQL answers out of the SQL-standard information_schema;
    /// SQLite has none and is asked through sqlite_master instead. ADO.NET's own
    /// <c>GetSchema("Tables")</c> would have spared the branch and was tried first — Microsoft.Data
    /// .Sqlite does not define that collection and throws.
    ///
    /// Names only, and compared case-insensitively, which is what makes this safe to ask of a
    /// PostgreSQL that holds more than this product: the question is "does Accounts exist HERE", and
    /// only the schema the connection actually writes into is read.
    /// </summary>
    private static PresentSchema ReadPresentSchema(BewerboDbContext db)
    {
        var sql = db.Database.IsSqlite()
            ? "SELECT m.name, c.name FROM sqlite_master m JOIN pragma_table_info(m.name) c WHERE m.type = 'table'"
            : "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = current_schema()";

        var connection = db.Database.GetDbConnection();
        var wasClosed = connection.State != ConnectionState.Open;
        if (wasClosed)
        {
            db.Database.OpenConnection();
        }

        try
        {
            using var command = connection.CreateCommand();
            command.CommandText = sql;

            var tables = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var columns = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using var reader = command.ExecuteReader();
            while (reader.Read())
            {
                var table = reader.GetString(0);
                tables.Add(table);
                columns.Add($"{table}.{reader.GetString(1)}");
            }

            return new PresentSchema(tables, columns);
        }
        finally
        {
            if (wasClosed)
            {
                db.Database.CloseConnection();
            }
        }
    }

    private record PresentSchema(HashSet<string> Tables, HashSet<string> Columns)
    {
        public bool HasTable(string table) => Tables.Contains(table);

        public bool HasColumn(string table, string column) => Columns.Contains($"{table}.{column}");
    }
}
