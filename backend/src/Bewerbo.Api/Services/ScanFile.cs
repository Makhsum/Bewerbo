using UglyToad.PdfPig;

namespace Bewerbo.Api.Services;

/// <summary>
/// What a scan was accepted as, or why it was not — one answer, because the three refusals are one
/// decision made on the same bytes.
///
/// <see cref="RefusedKind"/> is null on an accepted file and carries the kind the route answers
/// with otherwise, the way every other refusal of this API travels: the server never learns the
/// interface language, so the screen writes the sentence. See
/// <see cref="Controllers.BewerboController.RefusedProblem"/>.
/// </summary>
public record ScanCheck(string ContentType, int PageCount, string? RefusedKind)
{
    public bool Accepted => RefusedKind is null;

    internal static ScanCheck Refused(string kind) => new("", 0, kind);
}

/// <summary>
/// The rules a scan has to pass before it is stored: what it is, how big it is, and how many pages
/// it turned out to have.
///
/// The type is read from the BYTES and never from the Content-Type the upload named. A client that
/// says "application/pdf" over a video file is not unusual enough to be treated as an error on the
/// client's side, and the type is what decides how the export and the viewer later treat the file —
/// a wrong one there is a Bewerbungsmappe that will not render.
///
/// The two caps exist for different reasons and are therefore two. <see cref="MaxBytes"/> is about
/// the server: the bytes go into a database column and a phone will happily hand over a 40 MB photo
/// of one sheet of paper. <see cref="MaxPages"/> is about the application: a Zeugnis is one to three
/// pages, and something with fifty in it is not a document an employer was asked to be sent.
/// </summary>
public static class ScanFile
{
    /// <summary>The largest scan that may be stored. Generous for a photographed Zeugnis, and far
    /// short of what an unchecked upload into a database column costs.</summary>
    public const int MaxBytes = 10 * 1024 * 1024;

    /// <summary>The most pages one document's scan may carry.</summary>
    public const int MaxPages = 20;

    public const string Pdf = "application/pdf";
    public const string Jpeg = "image/jpeg";
    public const string Png = "image/png";

    /// <summary>The bytes are none of the three types the Mappe stores.</summary>
    public const string UnsupportedKind = "scan_type_unsupported";

    /// <summary>Over <see cref="MaxBytes"/>.</summary>
    public const string TooLargeKind = "scan_too_large";

    /// <summary>Over <see cref="MaxPages"/>.</summary>
    public const string TooManyPagesKind = "scan_too_many_pages";

    /// <summary>
    /// Reads <paramref name="content"/> and says whether it may be stored.
    ///
    /// The order is deliberate: size first, because a file over the cap is refused without its
    /// pages being counted, and a PDF is not opened to be told it was too big to keep anyway.
    /// </summary>
    public static ScanCheck Check(byte[] content)
    {
        if (content.Length > MaxBytes) return ScanCheck.Refused(TooLargeKind);

        // An empty body has no signature and is refused as the wrong type rather than as an empty
        // one: zero bytes are not a PDF, and the user's answer to both is to choose another file.
        var contentType = TypeOf(content);
        if (contentType is null) return ScanCheck.Refused(UnsupportedKind);

        var pageCount = PageCountOf(content, contentType);
        // Zero means the bytes begin like a PDF and are not one — a truncated upload, or a file
        // renamed to .pdf. Reported as the wrong type rather than as no pages, because that is
        // what the user has to do something about.
        if (pageCount == 0) return ScanCheck.Refused(UnsupportedKind);
        if (pageCount > MaxPages) return ScanCheck.Refused(TooManyPagesKind);

        return new ScanCheck(contentType, pageCount, null);
    }

    /// <summary>
    /// What the bytes are, by their signature, or null for anything else. Three types and no more:
    /// they are what a phone camera and a document scanner produce and what the export can append.
    /// </summary>
    private static string? TypeOf(byte[] content)
    {
        if (StartsWith(content, [0x25, 0x50, 0x44, 0x46])) return Pdf;                      // %PDF
        if (StartsWith(content, [0xFF, 0xD8, 0xFF])) return Jpeg;
        if (StartsWith(content, [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])) return Png;
        return null;
    }

    /// <summary>
    /// How many pages the file holds — the PDF's own count, and one for a picture.
    ///
    /// This is what pre-fills the Pages field of the add card, so that the number in the
    /// Anlagenverzeichnis comes off the document rather than off the user's memory of it. Zero
    /// means the PDF could not be opened at all; <see cref="Check"/> turns that into a refusal.
    /// </summary>
    public static int PageCountOf(byte[] content, string contentType)
    {
        if (contentType != Pdf) return 1;

        try
        {
            using var document = PdfDocument.Open(content);
            return document.NumberOfPages;
        }
        catch
        {
            return 0;
        }
    }

    private static bool StartsWith(byte[] content, byte[] signature)
    {
        if (content.Length < signature.Length) return false;
        for (var i = 0; i < signature.Length; i++)
        {
            if (content[i] != signature[i]) return false;
        }

        return true;
    }
}
