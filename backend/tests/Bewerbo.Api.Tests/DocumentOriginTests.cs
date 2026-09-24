using System.Text.Json;
using Bewerbo.Api.Contracts;
using Bewerbo.Api.Controllers;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// Where a document was filed, and the one thing the server does with that: keep it.
///
/// The Documents screen used to say "This document was added on another device" under every
/// document that had no stored copy — a statement about the user's own data that nothing in the
/// record supported, and that was simply wrong for a document filed on this phone without a file
/// or with one the server refused. The absence of a scan was being read as an origin.
///
/// So the origin is now carried: the client stamps the record with its own installation id, the
/// server keeps that id and hands it back, and the SCREEN compares it with its own — nobody else
/// can, because only the phone knows which phone it is. A topic file of its own, as
/// <see cref="ScanTests"/> is: this is a rule about what the record means, not about what the
/// route refuses, which is <see cref="IncompleteBodyTests"/>' subject.
/// </summary>
public class DocumentOriginTests
{
    [Fact]
    public async Task The_installation_that_filed_a_document_is_kept_and_answered_back()
    {
        await using var db = NewDatabase();
        var profile = ProfileOf(db);

        var created = await Documents(db).Add(profile, DocumentFrom("""
            { "title": "Klinikum Ost", "kind": "Arbeitszeugnis", "note": "", "pageCount": 1,
              "addedOnDevice": "3f1c-this-phone" }
            """));

        Assert.Equal("3f1c-this-phone", db.Documents.Single().AddedOnDevice);
        Assert.Equal(
            "3f1c-this-phone",
            Assert.IsType<DocumentDto>(Assert.IsType<CreatedResult>(created).Value).AddedOnDevice);
    }

    /// <summary>
    /// The half that keeps the old sentence honest rather than merely absent: a body that names no
    /// device says NOTHING about where the document came from. A positional record deserialises an
    /// omitted member as null even where the type says it is not nullable — the defect
    /// <see cref="IncompleteBodyTests"/> is about — so this is asked of the deserialised body and
    /// not of a hand-built DTO, and the answer has to be the empty string and never null.
    /// </summary>
    [Fact]
    public async Task A_document_filed_without_a_device_says_nothing_about_where_it_came_from()
    {
        await using var db = NewDatabase();
        var profile = ProfileOf(db);

        await Documents(db).Add(profile, DocumentFrom("""
            { "title": "Klinikum Ost", "kind": "Arbeitszeugnis", "note": "", "pageCount": 1 }
            """));

        Assert.Equal("", db.Documents.Single().AddedOnDevice);
    }

    /// <summary>
    /// What a record written before this field existed answers — SchemaUpdate only ADDS, so every
    /// one of them carries the empty string. Both sentences the screen can write are about a device
    /// it can name, so a row that names none is left with neither, which is the third state the
    /// Documents screen has to have.
    /// </summary>
    [Fact]
    public void A_record_from_before_the_stamp_names_no_device()
    {
        Assert.Equal("", new StoredDocument { Title = "Klinikum Ost", PageCount = 1 }.ToDto().AddedOnDevice);
    }

    // -- helpers ----------------------------------------------------------------------------------

    /// <summary>The body as the binder hands it over: parsed from JSON, with the member left out.</summary>
    private static DocumentDto DocumentFrom(string json) =>
        JsonSerializer.Deserialize<DocumentDto>(json, Wire)!;

    private static readonly JsonSerializerOptions Wire = new(JsonSerializerDefaults.Web);

    private static DocumentsController Documents(BewerboDbContext db) =>
        new(db) { ControllerContext = new ControllerContext { HttpContext = new DefaultHttpContext { RequestServices = Mvc } } };

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
