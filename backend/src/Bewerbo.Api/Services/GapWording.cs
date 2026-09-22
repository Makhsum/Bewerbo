using System.Text.RegularExpressions;

namespace Bewerbo.Api.Services;

/// <summary>
/// Turns what the user typed about a gap — in Russian, Ukrainian, Turkish or English — into the
/// German wording a Lebenslauf uses for it.
///
/// A gap is not translated, it is named: German CVs have a fixed, small vocabulary for this
/// ("Umzug nach Deutschland", "Sprachkurs Deutsch B2", "Anerkennungsverfahren"), and hitting that
/// vocabulary is the whole point. A free translation of "I moved and studied the language" reads
/// foreign; "Umzug nach Deutschland, Sprachkurs B2" reads local.
///
/// Whatever this returns is only ever a SUGGESTION: it is shown to the user before it can appear in
/// a document, and the stored wording is what the user accepted.
/// </summary>
public static class GapWording
{
    private record Phrase(string German, params string[] Triggers);

    private static readonly Phrase[] Table =
    [
        new("Umzug nach Deutschland",
            "umzug", "переезд", "переїзд", "переехал", "переїхал", "relocation", "moved", "moving",
            "taşınma", "göç"),
        new("Sprachkurs Deutsch",
            "sprachkurs", "deutschkurs", "курс немецкого", "курс німецької", "языков",
            "language course", "german course", "almanca kursu", "dil kursu"),
        new("Anerkennungsverfahren",
            "anerkennung", "признание диплома", "визнання диплома", "нострификация",
            "recognition", "denklik"),
        new("Elternzeit",
            "elternzeit", "декрет", "декретн", "materni", "parental", "doğum izni"),
        new("Weiterbildung",
            "weiterbildung", "fortbildung", "курс", "kurs", "training", "umschulung", "eğitim"),
        new("Pflege eines Angehörigen",
            "pflege", "уход за", "догляд за", "care for", "bakım"),
        new("Berufliche Neuorientierung",
            "neuorientierung", "поиск работы", "пошук роботи", "job search", "iş arama"),
    ];

    /// <summary>
    /// The German wording for a reason, plus the CEFR level if the user named one ("курс B2"
    /// becomes "Sprachkurs Deutsch B2"). Returns an empty string when nothing is recognised, so the
    /// caller can ask rather than invent a reason the user never gave.
    /// </summary>
    public static string Suggest(string? reason)
    {
        if (string.IsNullOrWhiteSpace(reason)) return "";

        var lower = reason.ToLowerInvariant();
        var parts = new List<string>();

        foreach (var phrase in Table)
        {
            if (!phrase.Triggers.Any(t => lower.Contains(t, StringComparison.OrdinalIgnoreCase))) continue;

            var german = phrase.German;
            if (german.StartsWith("Sprachkurs", StringComparison.Ordinal))
            {
                var level = Regex.Match(reason, @"\b([ABC][12])\b", RegexOptions.IgnoreCase);
                if (level.Success) german = $"{german} {level.Groups[1].Value.ToUpperInvariant()}";
            }
            if (!parts.Contains(german)) parts.Add(german);
        }

        // "курс німецької" triggers both Sprachkurs and the generic Weiterbildung. The specific
        // wording is the one a German reader learns something from, so the generic one drops out.
        if (parts.Any(p => p.StartsWith("Sprachkurs", StringComparison.Ordinal)))
        {
            parts.Remove("Weiterbildung");
        }

        return string.Join(", ", parts);
    }
}
