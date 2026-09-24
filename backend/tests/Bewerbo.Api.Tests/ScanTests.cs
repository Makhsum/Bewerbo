using System.Text.RegularExpressions;
using Bewerbo.Api.Contracts;
using Bewerbo.Api.Controllers;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;
using UglyToad.PdfPig;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// The scan behind a document: what may be stored, and where it lands in the exported Mappe.
///
/// A topic file of its own, as <see cref="AuthTests"/> is, because these are two different kinds of
/// rule that happen to be about one thing — what <see cref="ScanFile"/> accepts is a rule about the
/// server, and where the appended pages come out is a rule about the document an employer opens.
/// </summary>
public class ScanTests
{
    // -- what may be stored -----------------------------------------------------------------------

    [Fact]
    public void A_pdf_is_accepted_with_the_page_count_it_actually_has()
    {
        // The count comes off the file, and this is why: it is what pre-fills the Pages field of
        // the add card and it is what the Anlagenverzeichnis prints. A user who typed "1" for a
        // three-page Zeugnis has told an employer the wrong thing about what they were sent.
        var check = ScanFile.Check(PdfOf(3));

        Assert.True(check.Accepted);
        Assert.Equal(ScanFile.Pdf, check.ContentType);
        Assert.Equal(3, check.PageCount);
    }

    [Fact]
    public void A_photograph_is_accepted_as_one_page()
    {
        var check = ScanFile.Check(OnePixelPng());

        Assert.True(check.Accepted);
        Assert.Equal(ScanFile.Png, check.ContentType);
        Assert.Equal(1, check.PageCount);
    }

    [Fact]
    public void The_type_is_read_off_the_bytes_and_not_off_what_the_upload_called_it()
    {
        // A Word document renamed to .pdf, which is the everyday version of this. Nothing in the
        // request is consulted, so there is nothing here to name the file with: the signature is
        // the whole of the answer.
        var check = ScanFile.Check("PK\u0003\u0004 this is a .docx"u8.ToArray());

        Assert.False(check.Accepted);
        Assert.Equal(ScanFile.UnsupportedKind, check.RefusedKind);
    }

    [Fact]
    public void A_file_that_only_begins_like_a_pdf_is_refused_as_the_wrong_type()
    {
        // The signature matches and the file is not one — a truncated upload. Reported as the
        // wrong type rather than as "no pages", because "choose another file" is the answer to
        // both and "this document has no pages" is not something a user can act on.
        var check = ScanFile.Check("%PDF-1.7 and then nothing"u8.ToArray());

        Assert.False(check.Accepted);
        Assert.Equal(ScanFile.UnsupportedKind, check.RefusedKind);
    }

    [Fact]
    public void An_empty_body_is_refused_rather_than_stored_as_a_scan()
    {
        Assert.False(ScanFile.Check([]).Accepted);
    }

    [Fact]
    public void A_file_over_the_cap_is_refused_as_too_large()
    {
        var oversized = new byte[ScanFile.MaxBytes + 1];
        // A real PDF signature on it, so that what is being tested is the size and not the type.
        "%PDF"u8.CopyTo(oversized);

        Assert.Equal(ScanFile.TooLargeKind, ScanFile.Check(oversized).RefusedKind);
    }

    [Fact]
    public void A_document_with_more_pages_than_the_cap_is_refused()
    {
        Assert.Equal(ScanFile.TooManyPagesKind, ScanFile.Check(PdfOf(ScanFile.MaxPages + 1)).RefusedKind);
    }

    // -- whose number the page count is -----------------------------------------------------------

    /// <summary>
    /// The case the card is about. The user filed a Zeugnis of two pages and then chose a scan of
    /// three sheets for it — a cover sheet, a copy folded twice, a reprint of the same page. The
    /// number they typed is a statement about what the employer is being sent, and the upload used
    /// to replace it without a word.
    /// </summary>
    [Fact]
    public async Task A_stated_page_count_survives_the_scan_that_is_uploaded_next()
    {
        await using var db = NewDatabase();
        var document = DocumentWithConsent(db, pageCount: 2, stated: true);

        var stored = await Routes(db, PdfOf(3)).StoreScan(document, "Zeugnis.pdf");

        Assert.Equal(2, Assert.IsType<DocumentDto>(Assert.IsType<OkObjectResult>(stored).Value).PageCount);
        Assert.Equal(2, db.Documents.Find(document)!.PageCount);
    }

    /// <summary>
    /// The other half of the same rule, and the reason the count is read off the file at all: a
    /// user who has stated nothing is better served by the file's number than by the "1" a field
    /// defaults to, because the Anlagenverzeichnis prints it.
    /// </summary>
    [Fact]
    public async Task A_page_count_nobody_stated_is_still_filled_in_from_the_file()
    {
        await using var db = NewDatabase();
        var document = DocumentWithConsent(db, pageCount: 1, stated: false);

        await Routes(db, PdfOf(3)).StoreScan(document, "Zeugnis.pdf");

        Assert.Equal(3, db.Documents.Find(document)!.PageCount);
    }

    /// <summary>
    /// What the record answers about itself, which is what lets the screen say where the number it
    /// shows came from. Without this on the way out the two counts are indistinguishable to every
    /// client, and the user is back to being told nothing.
    /// </summary>
    [Fact]
    public void A_document_says_whether_its_page_count_is_the_users_own()
    {
        Assert.True(new StoredDocument { PageCount = 2, PageCountStated = true }.ToDto().PageCountStated);
        Assert.False(new StoredDocument { PageCount = 3 }.ToDto().PageCountStated);
    }

    // -- where the copies land in the Mappe -------------------------------------------------------

    [Fact]
    public void The_stored_copies_are_appended_in_the_order_the_Anlagenverzeichnis_lists_them()
    {
        // The rule the whole export part rests on: "Anlage 3" on the Anlagenverzeichnis and the
        // third appended scan have to be the same document. The list is grouped by kind and sorted
        // by title inside a kind, so the order the scans were ADDED in is not the order they come
        // out in — which is exactly why this is asserted on the finished file.
        var arbeitszeugnis = new StoredDocument
        {
            Title = "Klinikum Ost", Kind = DocumentKind.Arbeitszeugnis, PageCount = 1,
        };
        var zertifikat = new StoredDocument
        {
            Title = "Projektmanagement", Kind = DocumentKind.Zertifikat, PageCount = 1,
        };
        var sprachnachweis = new StoredDocument
        {
            Title = "telc B2", Kind = DocumentKind.Sprachnachweis, PageCount = 1,
        };

        // Added in the reverse of the order the page lists them in.
        var documents = new List<StoredDocument> { sprachnachweis, zertifikat, arbeitszeugnis };
        var scans = new List<DocumentScan>
        {
            ScanSaying(sprachnachweis, "SCANDRITTES"),
            ScanSaying(zertifikat, "SCANZWEITES"),
            ScanSaying(arbeitszeugnis, "SCANERSTES"),
        };

        var pdf = Export(documents, ApplicationParts.Anlagenverzeichnis | ApplicationParts.Scans, scans);
        var text = TextOf(pdf);

        var first = text.IndexOf("SCANERSTES", StringComparison.Ordinal);
        var second = text.IndexOf("SCANZWEITES", StringComparison.Ordinal);
        var third = text.IndexOf("SCANDRITTES", StringComparison.Ordinal);

        Assert.True(first >= 0 && second >= 0 && third >= 0, "Not every stored copy reached the file.");
        Assert.True(first < second, "The Arbeitszeugnis has to come before the Zertifikat.");
        Assert.True(second < third, "The Zertifikat has to come before the Sprachnachweis.");
    }

    [Fact]
    public void A_document_without_a_copy_is_skipped_rather_than_stopping_the_export()
    {
        // The everyday case this card exists for: the list is complete and only some of the files
        // are there. The Mappe still has to be producible — the export card says which document
        // has no copy, and the file itself simply does not carry that page.
        var withScan = new StoredDocument { Title = "Klinikum Ost", Kind = DocumentKind.Arbeitszeugnis };
        var withoutScan = new StoredDocument { Title = "telc B2", Kind = DocumentKind.Sprachnachweis };

        var pdf = Export(
            [withScan, withoutScan],
            ApplicationParts.Anlagenverzeichnis | ApplicationParts.Scans,
            [ScanSaying(withScan, "SCANERSTES")]);

        var text = TextOf(pdf);
        Assert.Contains("SCANERSTES", text);
        // The Anlagenverzeichnis goes on naming both, because both are being sent.
        Assert.Contains("telcB2", text);
    }

    [Fact]
    public void Leaving_the_copies_out_leaves_them_out_of_the_file()
    {
        var document = new StoredDocument { Title = "Klinikum Ost", Kind = DocumentKind.Arbeitszeugnis };

        var pdf = Export([document], ApplicationParts.Anlagenverzeichnis, [ScanSaying(document, "SCANERSTES")]);

        Assert.DoesNotContain("SCANERSTES", TextOf(pdf));
    }

    // -- helpers ----------------------------------------------------------------------------------

    /// <summary>
    /// A document of an account that has agreed to scans being held, so the upload is not refused
    /// before it reaches the rule these tests are about.
    /// </summary>
    private static Guid DocumentWithConsent(BewerboDbContext db, int pageCount, bool stated)
    {
        var profile = new Domain.Profile { FirstName = "Olena", LastName = "Kovalchuk" };
        db.Profiles.Add(profile);
        db.Accounts.Add(new Account
        {
            Email = "olena@example.de", ProfileId = profile.Id, ScansAgreedAt = DateTimeOffset.UtcNow,
        });

        var document = new StoredDocument
        {
            ProfileId = profile.Id, Title = "Klinikum Ost", Kind = DocumentKind.Arbeitszeugnis,
            PageCount = pageCount, PageCountStated = stated,
        };
        db.Documents.Add(document);
        db.SaveChanges();
        db.ChangeTracker.Clear();
        return document.Id;
    }

    /// <summary>
    /// The controller as the upload reaches it: the database it writes to, and the bytes as the
    /// request body — this route reads the body itself rather than taking a bound parameter, so a
    /// test of it has to hand one over the same way. No session claim is needed; who may name this
    /// document is <see cref="OwnershipFilter"/>'s question and not this method's.
    /// </summary>
    private static DocumentsController Routes(BewerboDbContext db, byte[] body) =>
        new(db)
        {
            ControllerContext = new ControllerContext
            {
                HttpContext = new DefaultHttpContext { Request = { Body = new MemoryStream(body) } },
            },
        };

    private static BewerboDbContext NewDatabase()
    {
        var connection = new SqliteConnection("DataSource=:memory:");
        connection.Open();
        var db = new BewerboDbContext(
            new DbContextOptionsBuilder<BewerboDbContext>().UseSqlite(connection).Options);
        db.Database.EnsureCreated();
        return db;
    }

    /// <summary>The Mappe as the export route renders it, with only the parts this test is about.</summary>
    private static byte[] Export(
        List<StoredDocument> documents, ApplicationParts parts, List<DocumentScan> scans)
    {
        var profile = new Domain.Profile { FirstName = "Olena", LastName = "Kovalchuk", Documents = documents };
        var posting = new Posting { JobTitle = "Pflegefachkraft", Company = "Klinikum Ost" };
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 24));
        var cv = writer.WriteCvAsync(profile, timeline).Result;
        var letter = writer.WriteLetterAsync(
            profile, posting, RequirementMatcher.Match(profile, []), LetterTone.Sachlich).Result;

        return MergedApplicationDocument.Render(profile, posting, letter, cv, writer.LastSource, documents,
            new DateOnly(2026, 9, 24), parts, showInspector: false, scans: scans);
    }

    /// <summary>A stored copy that says which document it belongs to, so its position is readable.</summary>
    private static DocumentScan ScanSaying(StoredDocument document, string marker)
    {
        var content = PdfSaying(marker);
        return new DocumentScan
        {
            DocumentId = document.Id, ContentType = ScanFile.Pdf,
            FileName = $"{marker}.pdf", Content = content, SizeBytes = content.Length,
        };
    }

    private static string TextOf(byte[] pdf)
    {
        using var document = PdfDocument.Open(pdf);
        // Whitespace-stripped for the reason AtsTextCheck strips it: PdfPig returns the glyph run
        // without the spaces the eye supplies.
        return Regex.Replace(string.Join("\n", document.GetPages().Select(p => p.Text)), @"\s+", "");
    }

    private static byte[] PdfOf(int pages) => new PagesDocument(pages, null).GeneratePdf();

    private static byte[] PdfSaying(string marker) => new PagesDocument(1, marker).GeneratePdf();

    /// <summary>A PDF of a given length, optionally carrying one word that can be looked for.</summary>
    private class PagesDocument(int pages, string? marker) : IDocument
    {
        public void Compose(IDocumentContainer container)
        {
            for (var page = 1; page <= pages; page++)
            {
                var number = page;
                container.Page(p =>
                {
                    p.Size(PageSizes.A4);
                    p.Margin(20, Unit.Millimetre);
                    p.Content().Text(marker ?? $"Seite {number}");
                });
            }
        }
    }

    /// <summary>The smallest PNG there is — enough for the signature and for a renderer.</summary>
    private static byte[] OnePixelPng() => Convert.FromBase64String(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
}

