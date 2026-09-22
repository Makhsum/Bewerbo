namespace Bewerbo.Api.Recognition;

public record DegreeEquivalence(
    string Country,
    string ForeignDegree,
    string GermanEquivalent,
    string AnabinRating,
    string Note);

/// <summary>
/// anabin has no public API, so this is a seeded table for the most common countries of origin plus
/// free entry. Nothing here is ever written into a Lebenslauf until the user confirms it: a wrong
/// equivalence claim in an application is worse than no claim at all.
/// </summary>
public static class AnabinCatalog
{
    public static readonly IReadOnlyList<DegreeEquivalence> Seed =
    [
        new("UA", "Диплом спеціаліста (Облік і оподаткування)", "Bachelorabschluss (Rechnungswesen)",
            "H+", "Hochschule als H+ gelistet; Abschluss gleichwertig zu einem deutschen Bachelor."),
        new("UA", "Диплом бакалавра", "Bachelorabschluss", "H+",
            "Vierjähriges Bachelorstudium, als gleichwertig eingestuft."),
        new("UA", "Диплом магістра", "Masterabschluss", "H+",
            "Konsekutiver Masterabschluss, als gleichwertig eingestuft."),
        new("RU", "Диплом специалиста", "Diplom / Masterabschluss", "H+/-",
            "Einstufung hängt von der Hochschule ab — ZAB-Bewertung empfohlen."),
        new("RU", "Диплом бакалавра", "Bachelorabschluss", "H+",
            "Als gleichwertig zu einem deutschen Bachelor eingestuft."),
        new("TR", "Lisans Diploması", "Bachelorabschluss", "H+",
            "Vierjähriges Lisans-Studium, als gleichwertig eingestuft."),
        new("TR", "Yüksek Lisans Diploması", "Masterabschluss", "H+",
            "Als gleichwertig zu einem deutschen Master eingestuft."),
        new("SY", "إجازة جامعية", "Bachelorabschluss", "H+/-",
            "Einzelfallprüfung; ZAB-Zeugnisbewertung dringend empfohlen."),
        new("IN", "Bachelor of Technology", "Bachelorabschluss", "H+",
            "Vierjähriger B.Tech, als gleichwertig eingestuft."),
        new("PL", "Licencjat", "Bachelorabschluss", "H+",
            "Als gleichwertig zu einem deutschen Bachelor eingestuft."),
    ];

    public static IEnumerable<DegreeEquivalence> ForCountry(string? country) =>
        string.IsNullOrWhiteSpace(country)
            ? Seed
            : Seed.Where(d => d.Country.Equals(country, StringComparison.OrdinalIgnoreCase));

    public static IEnumerable<DegreeEquivalence> Search(string? country, string? query)
    {
        var pool = ForCountry(country);
        if (string.IsNullOrWhiteSpace(query)) return pool;
        return pool.Where(d =>
            d.ForeignDegree.Contains(query, StringComparison.OrdinalIgnoreCase) ||
            d.GermanEquivalent.Contains(query, StringComparison.OrdinalIgnoreCase));
    }
}
