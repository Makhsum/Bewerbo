using Bewerbo.Api.Contracts;
using Bewerbo.Api.Data;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using Microsoft.EntityFrameworkCore;
using static Bewerbo.Api.Contracts.DtoMapping;

namespace Bewerbo.Api.Controllers;

/// <summary>
/// The Mappe: the RECORD of a document — its title, kind and page count, which is what the
/// Anlagenverzeichnis needs — and, where the user has stored one, the scanned file behind it.
///
/// The record and the file are separate on purpose and in both directions. A document may be named
/// with no scan behind it, which is what every document added on another phone looks like until its
/// owner adds the file; and a scan may be removed while the document stays on the list, because the
/// Anlagenverzeichnis goes on naming what the employer is being sent. So the scan has its own three
/// routes rather than riding in the body that creates the record.
///
/// Storing somebody's Zeugnisse at all is a data-protection decision before it is a technical one,
/// and the answer here is a rule and not a habit of the screen that happens to ask: an upload is
/// REFUSED while the account has not agreed to it — see <see cref="Account.ScansAgreedAt"/> and
/// <see cref="ProfileController"/>, which is where that agreement is given.
/// </summary>
[Route("api/documents")]
public class DocumentsController(BewerboDbContext db) : BewerboController
{
    [HttpPost("")]
    public async Task<IActionResult> Add([FromQuery, BindRequired] Guid profileId, [FromBody] DocumentDto document)
    {
        var stored = new StoredDocument
        {
            ProfileId = profileId,
            Title = document.Title,
            Kind = ParseEnum(document.Kind, DocumentKind.Sonstiges),
            Note = document.Note,
            PageCount = document.PageCount <= 0 ? 1 : document.PageCount,
        };
        db.Documents.Add(stored);
        await db.SaveChangesAsync();
        return Created($"/api/documents/{stored.Id}", stored.ToDto());
    }

    [HttpGet("")]
    public async Task<IActionResult> List([FromQuery, BindRequired] Guid profileId)
    {
        var profile = await db.FullProfileAsync(profileId);
        if (profile is null) return NotFoundProblem(ProfileController.ProfileMissing, ProfileController.ProfileMissingKind);

        var scans = await db.ScanSummariesAsync(profileId);
        return Ok(profile.Documents.Select(d => d.ToDto(scans.GetValueOrDefault(d.Id))).ToList());
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id)
    {
        var document = await db.Documents.FindAsync(id);
        if (document is null) return NotFoundProblem(DocumentMissing, DocumentMissingKind);
        db.Documents.Remove(document);
        await db.SaveChangesAsync();
        return NoContent();
    }

    // -- the scan behind a document ---------------------------------------------------------------

    /// <summary>
    /// Stores the scanned file for a document, replacing the one that was there.
    ///
    /// The bytes are the body, with the name they had on the device as a query parameter — a file
    /// name is not something to trust into a path, and it is kept only so the user recognises the
    /// file again. What the upload CLAIMS the type is, is not read at all: <see cref="ScanFile"/>
    /// decides that from the bytes, because the type is what the viewer and the export later act
    /// on and a wrong one there is a Bewerbungsmappe that will not render.
    ///
    /// The page count of the document is set from the file, because that is a fact the file knows
    /// better than the user does — and the Anlagenverzeichnis prints it. It is only ever raised
    /// from the file here, never asked of the user twice.
    ///
    /// <c>[Consumes]</c> is not decoration, and it was put here by a request that went wrong. A
    /// body declared as a FORM type is read as a form by MVC's value providers before this method
    /// runs — so <see cref="HttpRequest.Body"/> arrived empty and an 11 MB upload came back as
    /// "Form value count limit 1024 exceeded" instead of as the refusal this route writes. The
    /// attribute answers 415 to anything that is not one of these four before a byte is read.
    /// Declaring one of them is a claim about the REPRESENTATION and nothing more: what the file
    /// actually is still comes from the bytes, which is why octet-stream is on the list.
    /// </summary>
    [HttpPost("{id:guid}/scan")]
    [Consumes(ScanFile.Pdf, ScanFile.Jpeg, ScanFile.Png, "application/octet-stream")]
    public async Task<IActionResult> StoreScan(Guid id, [FromQuery] string? name)
    {
        var document = await db.Documents.Include(d => d.Scan).FirstOrDefaultAsync(d => d.Id == id);
        if (document is null) return NotFoundProblem(DocumentMissing, DocumentMissingKind);

        // The rule, before a single byte is kept: this account has read what holding a scan means.
        // Asked of the SERVER so that it holds for every client and every rewrite of the screen —
        // the disclosure is what the user is owed, not the dialog that happens to show it.
        var agreed = await db.Accounts
            .AnyAsync(a => a.ProfileId == document.ProfileId && a.ScansAgreedAt != null);
        if (!agreed) return RefusedProblem(ConsentMissing, ConsentMissingKind);

        var content = await ReadCappedAsync(Request.Body, ScanFile.MaxBytes);
        if (content is null) return RefusedProblem(RefusalDetail(ScanFile.TooLargeKind), ScanFile.TooLargeKind);

        var check = ScanFile.Check(content);
        if (!check.Accepted) return RefusedProblem(RefusalDetail(check.RefusedKind!), check.RefusedKind!);

        // Replaced rather than added beside: one scan per document is what the model says, and
        // "Replace" in the viewer is this same route called a second time.
        if (document.Scan is not null) db.DocumentScans.Remove(document.Scan);

        db.DocumentScans.Add(new DocumentScan
        {
            DocumentId = document.Id,
            ContentType = check.ContentType,
            FileName = FileNameOf(name),
            Content = content,
            SizeBytes = content.Length,
        });
        document.PageCount = check.PageCount;
        await db.SaveChangesAsync();

        var scans = await db.ScanSummariesAsync(document.ProfileId);
        return Ok(document.ToDto(scans.GetValueOrDefault(document.Id)));
    }

    /// <summary>
    /// The scanned file itself — what makes a document added on one phone openable on the next.
    ///
    /// The only route that reads the bytes column, which is why every other answer about a document
    /// carries a <see cref="DocumentScanDto"/> and not the file.
    /// </summary>
    [HttpGet("{id:guid}/scan")]
    public async Task<IActionResult> GetScan(Guid id)
    {
        var scan = await db.DocumentScans.FirstOrDefaultAsync(s => s.DocumentId == id);
        return scan is null
            ? NotFoundProblem(ScanMissing, ScanMissingKind)
            : File(scan.Content, scan.ContentType, scan.FileName);
    }

    /// <summary>
    /// Removes the stored copy and keeps the document.
    ///
    /// Two things and not one, and the user is told so where they press it: the Anlagenverzeichnis
    /// goes on naming the Zeugnis, because it is still being sent — what leaves is the copy this
    /// installation was holding. It is also the "remove the copies" the disclosure promises.
    /// </summary>
    [HttpDelete("{id:guid}/scan")]
    public async Task<IActionResult> DeleteScan(Guid id)
    {
        var scan = await db.DocumentScans.FirstOrDefaultAsync(s => s.DocumentId == id);
        if (scan is null) return NotFoundProblem(ScanMissing, ScanMissingKind);

        db.DocumentScans.Remove(scan);
        await db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// The whole body, or null when there is more of it than <paramref name="max"/>.
    ///
    /// Capped WHILE it is read rather than after. A 200 MB body must not be pulled into memory in
    /// full just to be told it was too large — which is exactly what a check on the finished array
    /// would do, and what an attacker would do it with.
    /// </summary>
    private static async Task<byte[]?> ReadCappedAsync(Stream body, int max)
    {
        using var buffer = new MemoryStream();
        var chunk = new byte[81920];
        int read;
        while ((read = await body.ReadAsync(chunk)) > 0)
        {
            if (buffer.Length + read > max) return null;
            buffer.Write(chunk, 0, read);
        }

        return buffer.ToArray();
    }

    /// <summary>
    /// The name to keep for an uploaded file. Only the last segment and only what a file name may
    /// contain: this string is shown back to the user and written into a download's
    /// Content-Disposition, and neither wants a path in it.
    /// </summary>
    private static string FileNameOf(string? name)
    {
        var candidate = Path.GetFileName(name ?? "").Trim();
        if (candidate.Length == 0) return DefaultFileName;

        var cleaned = new string(candidate
            .Where(c => !Path.GetInvalidFileNameChars().Contains(c) && !char.IsControl(c))
            .ToArray());
        return cleaned.Length == 0 ? DefaultFileName : cleaned[..Math.Min(cleaned.Length, 120)];
    }

    /// <summary>The German beside a refusal kind, kept as the fallback every other route keeps one.</summary>
    private static string RefusalDetail(string kind) => kind switch
    {
        ScanFile.TooLargeKind => $"Die Datei ist größer als {ScanFile.MaxBytes / (1024 * 1024)} MB.",
        ScanFile.TooManyPagesKind => $"Die Datei hat mehr als {ScanFile.MaxPages} Seiten.",
        _ => "Nur PDF, JPEG und PNG können als Scan gespeichert werden.",
    };

    internal const string DocumentMissing = "Es gibt kein Dokument mit dieser Id.";
    internal const string DocumentMissingKind = "document_missing";

    internal const string ScanMissing = "Für dieses Dokument ist keine Kopie gespeichert.";
    internal const string ScanMissingKind = "scan_missing";

    internal const string ConsentMissing = "Für dieses Konto ist der Speicherung von Kopien noch nicht zugestimmt worden.";
    internal const string ConsentMissingKind = "scan_consent_missing";

    /// <summary>What a file with no usable name is called. Not localised: it is a file name.</summary>
    private const string DefaultFileName = "Scan";
}
