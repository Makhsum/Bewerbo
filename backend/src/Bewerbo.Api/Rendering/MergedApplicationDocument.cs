using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using QuestPDF.Fluent;
using QuestPDF.Infrastructure;

namespace Bewerbo.Api.Rendering;

/// <summary>Which parts of the Bewerbungsmappe go into the exported file.</summary>
[Flags]
public enum ApplicationParts
{
    None = 0,
    Anschreiben = 1,
    Lebenslauf = 2,
    Anlagenverzeichnis = 4,
    All = Anschreiben | Lebenslauf | Anlagenverzeichnis,
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
        CvContent cv,
        IReadOnlyList<StoredDocument> documents,
        DateOnly date,
        ApplicationParts parts = ApplicationParts.All,
        bool showInspector = false)
    {
        var pieces = new List<IDocument>();

        if (parts.HasFlag(ApplicationParts.Anschreiben))
        {
            pieces.Add(new Din5008LetterDocument(letter, profile, posting, date, showInspector));
        }
        if (parts.HasFlag(ApplicationParts.Lebenslauf))
        {
            pieces.Add(new LebenslaufDocument(cv, profile, profile.Template));
        }
        if (parts.HasFlag(ApplicationParts.Anlagenverzeichnis) && documents.Count > 0)
        {
            pieces.Add(new AnlagenverzeichnisDocument(documents, profile));
        }

        if (pieces.Count == 0)
        {
            throw new ArgumentException("An application with no parts cannot be exported.", nameof(parts));
        }

        return pieces.Count == 1
            ? pieces[0].GeneratePdf()
            : Document.Merge(pieces).GeneratePdf();
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
