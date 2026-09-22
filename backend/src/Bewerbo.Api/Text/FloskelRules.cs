namespace Bewerbo.Api.Text;

/// <summary>
/// The banned phrases, as data. Recruiters recognise generated German by exactly these, so the
/// check has to be a fixed list matched literally — a model asked to judge its own letter is
/// unfalsifiable and passes itself every time.
/// </summary>
public static class FloskelRules
{
    /// <summary>The three the README names, plus the ones that travel with them.</summary>
    public static readonly IReadOnlyList<string> Banned =
    [
        "Hiermit bewerbe ich mich",
        "hiermit bewerbe ich mich",
        "Ich bin ein Teamplayer",
        "Teamplayer",
        "kommunikationsstark",
        "Kommunikationsstark",
        "teamfähig",
        "Teamfähigkeit",
        "belastbar",
        "Belastbarkeit",
        "dynamisches Umfeld",
        "spannende Herausforderung",
        "neue Herausforderung",
        "mit großem Interesse habe ich Ihre Anzeige gelesen",
        "Mit großem Interesse habe ich Ihre Anzeige gelesen",
        "hat mein Interesse geweckt",
        "Ich würde mich freuen, von Ihnen zu hören",
        "über eine positive Rückmeldung würde ich mich freuen",
        "Motivationsschreiben",
    ];

    /// <summary>Every banned phrase that occurs in <paramref name="text"/>, each named once.</summary>
    public static IReadOnlyList<string> Find(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return [];

        var hits = new List<string>();
        foreach (var phrase in Banned)
        {
            if (text.Contains(phrase, StringComparison.OrdinalIgnoreCase) &&
                !hits.Contains(phrase, StringComparer.OrdinalIgnoreCase))
            {
                hits.Add(phrase);
            }
        }
        return hits;
    }
}
