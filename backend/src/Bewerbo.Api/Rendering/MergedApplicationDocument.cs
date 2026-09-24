using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Services;
using QuestPDF.Fluent;
using QuestPDF.Infrastructure;
using UglyToad.PdfPig.Writer;

namespace Bewerbo.Api.Rendering;

/// <summary>Which parts of the Bewerbungsmappe go into the exported file.</summary>
[Flags]
public enum ApplicationParts
{
    None = 0,
    Anschreiben = 1,
    Lebenslauf = 2,
    Anlagenverzeichnis = 4,
    /// <summary>
    /// The stored copies of the documents, appended after the Anlagenverzeichnis — in the order
    /// that page lists them, because an employer reads the list and then the attachments.
    /// </summary>
    Scans = 8,
    All = Anschreiben | Lebenslauf | Anlagenverzeichnis | Scans,
}

/// <summary>
/// Puts the Bewerbungsmappe together as ONE file, in the order a German reader expects to open it:
/// Anschreiben, then Lebenslauf, then the Anlagenverzeichnis.
///
/// One file matters more than it sounds: an application that arrives as four attachments gets
/// opened as four attachments, and the letter is usually the one that never gets read.
/// </summary>
public static class MergedApplicationDocument
{
    public static byte[] Render(
        Profile profile,
        Posting posting,
        LetterContent letter,
        // Which writer produced letter, for the line the Anschreiben carries. Two of them and not
        // one: the letter is the stored one and the Lebenslauf is written on the way in here, so a
        // Mappe really can carry a letter written by rule beside a Lebenslauf from the model.
        string letterWriter,
        CvContent cv,
        // Which writer produced cv, for the line the Lebenslauf carries. Beside the content it is
        // about rather than at the end with the options: a caller that has a CvContent has just
        // run a writer and knows which one it was.
        string cvWriter,
        IReadOnlyList<StoredDocument> documents,
        DateOnly date,
        ApplicationParts parts = ApplicationParts.All,
        bool showInspector = false,
        IReadOnlyList<DocumentScan>? scans = null)
    {
        var pieces = new List<IDocument>();

        if (parts.HasFlag(ApplicationParts.Anschreiben))
        {
            pieces.Add(new Din5008LetterDocument(letter, profile, posting, date, letterWriter, showInspector));
        }
        if (parts.HasFlag(ApplicationParts.Lebenslauf))
        {
            pieces.Add(new LebenslaufDocument(cv, profile, profile.Template, cvWriter));
        }
        if (parts.HasFlag(ApplicationParts.Anlagenverzeichnis) && documents.Count > 0)
        {
            pieces.Add(new AnlagenverzeichnisDocument(documents, profile));
        }

        var appended = parts.HasFlag(ApplicationParts.Scans)
            ? ScanPages(documents, scans)
            : [];

        if (pieces.Count == 0 && appended.Count == 0)
        {
            throw new ArgumentException("An application with no parts cannot be exported.", nameof(parts));
        }

        // The scans are the only part that is not drawn by QuestPDF: they are files that already
        // exist. QuestPDF's own Document.Merge takes IDocument and so cannot append one, which is
        // why the written parts are rendered first and PdfPig's PdfMerger puts the result and the
        // scans together. PdfPig is already a dependency — AtsTextCheck reads the finished file
        // with it — so appending a Zeugnis costs no new package.
        var written = pieces.Count switch
        {
            0 => null,
            1 => pieces[0].GeneratePdf(),
            _ => Document.Merge(pieces).GeneratePdf(),
        };

        if (appended.Count == 0) return written!;
        return PdfMerger.Merge(written is null ? appended : [written, .. appended]);
    }

    /// <summary>
    /// The stored copies as PDF, in the order the Anlagenverzeichnis lists them — so that "Anlage
    /// 3" on that page and the third appended scan are the same document.
    ///
    /// A document with no copy stored is simply skipped. That is not silence: the export card says
    /// how many of the documents have one and names the ones that do not, because a Mappe that
    /// quietly leaves out a Zeugnis the Anlagenverzeichnis promises is worse than one that does not
    /// offer to include any.
    /// </summary>
    private static List<byte[]> ScanPages(
        IReadOnlyList<StoredDocument> documents, IReadOnlyList<DocumentScan>? scans)
    {
        if (scans is null || scans.Count == 0) return [];

        var byDocument = scans.ToDictionary(s => s.DocumentId);
        return AnlagenverzeichnisDocument.InListedOrder(documents)
            .Select(d => byDocument.GetValueOrDefault(d.Id))
            .Where(s => s is not null)
            .Select(s => s!.ContentType == ScanFile.Pdf
                ? s.Content
                : new ScanPageDocument(s.Content).GeneratePdf())
            .ToList();
    }

    /// <summary>
    /// <c>Bewerbung_Vorname_Nachname_Stellenbezeichnung.pdf</c> — the name the card asks for. Every
    /// part is transliterated and stripped, because this name has to survive a mail client, a
    /// Windows share and an applicant tracking system that was written in 2009.
    /// </summary>
    public static string FileName(Profile profile, Posting posting)
    {
        var parts = new[] { "Bewerbung", profile.FirstName, profile.LastName, posting.JobTitle }
            .Select(FileNamePart)
            .Where(p => p.Length > 0);
        return string.Join("_", parts) + ".pdf";
    }

    private static string FileNamePart(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "";

        // "(m/w/d)" and everything after it is not part of what the job is called.
        var cut = raw.IndexOf('(');
        var text = (cut > 0 ? raw[..cut] : raw).Trim();

        var builder = new System.Text.StringBuilder();
        foreach (var c in text)
        {
            if (char.IsLetterOrDigit(c) && c < 128) { builder.Append(c); continue; }
            switch (c)
            {
                case 'ä': builder.Append("ae"); break;
                case 'ö': builder.Append("oe"); break;
                case 'ü': builder.Append("ue"); break;
                case 'Ä': builder.Append("Ae"); break;
                case 'Ö': builder.Append("Oe"); break;
                case 'Ü': builder.Append("Ue"); break;
                case 'ß': builder.Append("ss"); break;
                case ' ' or '-' or '_': builder.Append('-'); break;
                default:
                    // The audience is people who moved to Germany, so a Cyrillic name is the normal
                    // case, not the edge case. Dropping it would leave "Bewerbung__Stelle.pdf".
                    var latin = Transliterate(c);
                    if (latin.Length > 0 && char.IsUpper(c))
                    {
                        latin = char.ToUpperInvariant(latin[0]) + latin[1..];
                    }
                    builder.Append(latin);
                    break;
            }
        }

        return builder.ToString().Trim('-');
    }

    /// <summary>
    /// Russian and Ukrainian Cyrillic to the Latin form a German office actually writes — the
    /// spelling that appears on a German residence permit, so the file name matches the papers.
    /// </summary>
    private static string Transliterate(char c) => c switch
    {
        'а' or 'А' => "a", 'б' or 'Б' => "b", 'в' or 'В' => "w",
        'г' or 'Г' => "g", 'ґ' or 'Ґ' => "g", 'д' or 'Д' => "d",
        'е' or 'Е' => "e", 'є' or 'Є' => "je", 'ё' or 'Ё' => "jo",
        'ж' or 'Ж' => "sch", 'з' or 'З' => "s", 'и' or 'И' => "i",
        'і' or 'І' => "i", 'ї' or 'Ї' => "ji", 'й' or 'Й' => "j",
        'к' or 'К' => "k", 'л' or 'Л' => "l", 'м' or 'М' => "m",
        'н' or 'Н' => "n", 'о' or 'О' => "o", 'п' or 'П' => "p",
        'р' or 'Р' => "r", 'с' or 'С' => "s", 'т' or 'Т' => "t",
        'у' or 'У' => "u", 'ф' or 'Ф' => "f", 'х' or 'Х' => "ch",
        'ц' or 'Ц' => "z", 'ч' or 'Ч' => "tsch", 'ш' or 'Ш' => "sch",
        'щ' or 'Щ' => "schtsch", 'ы' or 'Ы' => "y", 'э' or 'Э' => "e",
        'ю' or 'Ю' => "ju", 'я' or 'Я' => "ja",
        // The soft and hard signs have no German letter and are simply not written.
        'ь' or 'Ь' or 'ъ' or 'Ъ' or '\'' => "",
        _ => "",
    };
}
