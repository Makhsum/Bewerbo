using System.Text.RegularExpressions;
using Bewerbo.Api.Llm;

namespace Bewerbo.Api.Services;

/// <summary>
/// Reads a German job posting without a model: the fields the README calls out — contact person,
/// company, Referenznummer, Stellenbezeichnung, Eintritt — are named in German postings in a small
/// number of conventional ways, and those conventions are matchable.
///
/// Every match yields the exact substring it came from, so the client can show the field against
/// the words it was read from. Where the parser is guessing it says so with confidence "pruefen"
/// rather than presenting a guess as fact.
/// </summary>
public static partial class PostingParser
{
    public static PostingExtract Parse(string text)
    {
        var fields = new List<ExtractedField>();

        AddIfFound(fields, ContactPerson(text));
        AddIfFound(fields, ContactRole(text));
        AddIfFound(fields, Company(text));
        AddIfFound(fields, CompanyAddress(text));
        AddIfFound(fields, Reference(text));
        AddIfFound(fields, JobTitle(text));
        AddIfFound(fields, StartDate(text));

        return new PostingExtract
        {
            Fields = fields,
            Requirements = Requirements(text),
            EmployerType = GuessEmployerType(text),
        };
    }

    // -- the individual fields ------------------------------------------------------------------

    private static ExtractedField? ContactPerson(string text)
    {
        // "an Frau Dr. Annika Weber", "Ihre Ansprechpartnerin ist Herr Klaus Meier"
        var m = ContactRx().Match(text);
        if (!m.Success) return null;
        var value = Tidy(m.Groups["name"].Value);
        return new ExtractedField
        {
            Key = "contact",
            Value = value,
            Quote = value,
            Confidence = "sicher",
        };
    }

    private static ExtractedField? ContactRole(string text)
    {
        var m = ContactRoleRx().Match(text);
        if (!m.Success) return null;
        var value = Tidy(m.Groups["role"].Value);
        return new ExtractedField { Key = "contactRole", Value = value, Quote = value, Confidence = "sicher" };
    }

    private static ExtractedField? Company(string text)
    {
        // Matched line by line: run over the whole text, the pattern walks backwards across a line
        // break and turns "…Stuttgart-Vaihingen\n\nDie Schwarzwald Technik GmbH" into one company.
        foreach (var line in text.Split('\n'))
        {
            var m = CompanyRx().Match(line);
            if (!m.Success) continue;

            var value = Tidy(m.Value);
            // "Die Schwarzwald Technik GmbH sucht …" — the article belongs to the sentence, not to
            // the company, and it would be addressed on the envelope if it were left in.
            foreach (var article in new[] { "Die ", "Der ", "Das ", "Bei ", "Beim " })
            {
                if (value.StartsWith(article, StringComparison.Ordinal)) value = value[article.Length..];
            }

            return new ExtractedField { Key = "company", Value = value, Quote = value, Confidence = "sicher" };
        }
        return null;
    }

    private static ExtractedField? CompanyAddress(string text)
    {
        // "Industriestrasse 8, 70563 Stuttgart" — street with number, then postcode and town.
        var m = AddressRx().Match(text);
        if (!m.Success) return null;
        var value = Tidy(m.Value);
        return new ExtractedField { Key = "companyAddress", Value = value, Quote = value, Confidence = "sicher" };
    }

    private static ExtractedField? Reference(string text)
    {
        var m = ReferenceRx().Match(text);
        if (!m.Success) return null;
        var code = Tidy(m.Groups["code"].Value);
        return new ExtractedField
        {
            Key = "reference",
            Value = code,
            // Quote the whole "Referenznummer SBT-2026-0417", which is what the reader recognises.
            Quote = Tidy(m.Value),
            // A reference number that lands wrong in the Betreffzeile is an immediate tell, so it is
            // always offered for checking rather than taken on trust.
            Confidence = "pruefen",
        };
    }

    private static ExtractedField? JobTitle(string text)
    {
        // The Stellenbezeichnung is conventionally the first line and carries the (m/w/d) marker,
        // so the LINE the marker sits on is the title — read up to the marker, not backwards from
        // it. Walking back over capitalised words instead dropped the front of every title that
        // holds punctuation ("Senior Softwareentwickler C#/.NET (m/w/d)" arrived as "NET (m/w/d)")
        // or a lowercase word ("Fachkraft für Lagerlogistik (m/w/d)" as "Lagerlogistik (m/w/d)"),
        // and still reported "sicher" — so the chip never asked anyone to correct it, and the
        // wrong Bezeichnung went on into the Betreffzeile, the Anschreiben and the export name.
        var marker = GenderMarkerRx().Match(text);
        if (marker.Success)
        {
            var lineStart = text.LastIndexOf('\n', marker.Index) + 1;
            var markerLine = text[lineStart..(marker.Index + marker.Length)];

            // A posting that announces the role inside a sentence — "Klinikum München sucht eine
            // Pflegefachkraft (m/w/d)" — must not hand the whole sentence over as the title. Only
            // that case cuts anything, and only as far as the article the vacancy verb introduces.
            var lead = VacancyLeadRx().Match(markerLine);
            if (lead.Success) markerLine = markerLine[(lead.Index + lead.Length)..];

            var value = Tidy(markerLine);
            if (value.Length > 0)
            {
                return new ExtractedField
                {
                    Key = "title",
                    Value = value,
                    Quote = value,
                    // A whole headline is the title as written. Cutting a sentence down to the role
                    // it names is a reading, so it goes to the user to confirm rather than passing
                    // as certain.
                    Confidence = lead.Success ? "pruefen" : "sicher",
                };
            }
        }

        var firstLine = text.Split('\n', StringSplitOptions.RemoveEmptyEntries).FirstOrDefault()?.Trim();
        if (string.IsNullOrWhiteSpace(firstLine)) return null;
        var headline = firstLine.Split('—', '–', '|')[0].Trim();
        return new ExtractedField { Key = "title", Value = headline, Quote = headline, Confidence = "pruefen" };
    }

    private static ExtractedField? StartDate(string text)
    {
        var m = StartRx().Match(text);
        if (!m.Success) return null;
        return new ExtractedField
        {
            Key = "start", Value = Tidy(m.Value), Quote = Tidy(m.Value), Confidence = "sicher",
        };
    }

    // -- requirements ---------------------------------------------------------------------------

    /// <summary>
    /// The sentences and bullets the posting states as expectations. These are the words the
    /// Anschreiben has to answer — listing virtues instead is the mistake the product prevents.
    /// </summary>
    private static List<ExtractedRequirement> Requirements(string text)
    {
        var found = new List<ExtractedRequirement>();

        void Add(string raw)
        {
            var line = Tidy(raw);
            if (line.Length is > 6 and < 160 &&
                !found.Any(f => f.Text.Equals(line, StringComparison.OrdinalIgnoreCase)))
            {
                found.Add(new ExtractedRequirement { Text = line, Quote = line });
            }
        }

        foreach (Match m in BulletRx().Matches(text)) Add(m.Groups["item"].Value);

        // Prose postings state the same thing in a sentence: "Wir erwarten sichere
        // DATEV-Kenntnisse, Deutsch mindestens B2 und ein sicheres Auftreten."
        foreach (Match m in ExpectationRx().Matches(text))
        {
            foreach (var part in SplitList(m.Groups["list"].Value)) Add(part);
        }

        // "Sie wirken an Monats- und Jahresabschluessen nach HGB mit" — the duties are requirements
        // too, and they are the ones a letter can answer most concretely.
        foreach (Match m in DutyRx().Matches(text))
        {
            foreach (var clause in SplitClauses(m.Groups["duty"].Value)) Add(Nominalise(clause));
        }

        return found;
    }

    /// <summary>
    /// "wirken an X mit und sind Ansprechpartner für Y" is two requirements, not one. Splitting on
    /// "und" alone would also cut "Monats- und Jahresabschlüsse", so the split is only made where a
    /// second finite verb follows.
    /// </summary>
    private static IEnumerable<string> SplitClauses(string duty) =>
        Regex.Split(duty, @"\s+und\s+(?=(?:sind|werden|haben|betreuen|verantworten|erstellen)\b)",
                RegexOptions.IgnoreCase)
            .Where(c => !string.IsNullOrWhiteSpace(c));

    /// <summary>
    /// Turns the posting's second-person verb into the noun phrase a letter can quote. "wirken an
    /// den Abschlüssen mit" is not something an Anschreiben can contain as written; "Mitwirkung an
    /// den Abschlüssen" is.
    /// </summary>
    private static string Nominalise(string clause)
    {
        var trimmed = clause.Trim();
        var (verb, noun) = trimmed.Split(' ')[0].ToLowerInvariant() switch
        {
            "wirken" => ("wirken", "Mitwirkung"),
            "verantworten" => ("verantworten", "Verantwortung für"),
            "betreuen" => ("betreuen", "Betreuung von"),
            "erstellen" => ("erstellen", "Erstellung von"),
            "übernehmen" => ("übernehmen", "Übernahme von"),
            "führen" => ("führen", "Führung von"),
            "unterstützen" => ("unterstützen", "Unterstützung bei"),
            // "sind Ansprechpartner für Steuerberater" is already a noun phrase once "sind" is gone.
            "sind" => ("sind", ""),
            _ => ("", ""),
        };

        if (verb.Length == 0) return trimmed;

        var rest = trimmed[verb.Length..].Trim();
        // The separable prefix travels to the end of the clause and has no place in the noun form.
        rest = Regex.Replace(rest, @"\s+mit$", "", RegexOptions.IgnoreCase);

        return noun.Length == 0 ? rest : $"{noun} {rest}".Trim();
    }

    private static IEnumerable<string> SplitList(string clause) =>
        Regex.Split(clause, @",\s*|\s+und\s+|\s+sowie\s+")
             .Where(p => !string.IsNullOrWhiteSpace(p));

    // -- employer type --------------------------------------------------------------------------

    /// <summary>
    /// Decides what a photo and personal data would look like here. Neither is legally required
    /// (AGG); this only says what this kind of employer is used to seeing.
    /// </summary>
    private static string GuessEmployerType(string text)
    {
        if (Regex.IsMatch(text, @"\b(Landkreis|Stadtverwaltung|Behörde|Amt für|öffentlichen Dienst|TVöD|TV-L)\b",
                RegexOptions.IgnoreCase))
            return "OeffentlicherDienst";
        if (Regex.IsMatch(text, @"\b(Start-?up|Scale-?up|wir duzen|Series [A-C])\b", RegexOptions.IgnoreCase))
            return "Startup";
        if (Regex.IsMatch(text, @"\b(Konzern|weltweit führend|Geschäftsbereich|AG|SE)\b"))
            return "Konzern";
        return "Mittelstand";
    }

    private static void AddIfFound(List<ExtractedField> into, ExtractedField? field)
    {
        if (field is not null && !string.IsNullOrWhiteSpace(field.Value)) into.Add(field);
    }

    private static string Tidy(string s) => Regex.Replace(s, @"\s+", " ").Trim().Trim(',', ';', '.', ':');

    // -- the conventions, as patterns -----------------------------------------------------------

    [GeneratedRegex(@"(?:an|Ansprechpartner(?:in)?\s+ist|wenden Sie sich an|Kontakt:?)\s+(?<name>(?:Frau|Herr)\s+(?:Dr\.\s+|Prof\.\s+)*[A-ZÄÖÜ][\wäöüß-]+(?:\s+[A-ZÄÖÜ][\wäöüß-]+){0,2})",
        RegexOptions.IgnoreCase)]
    private static partial Regex ContactRx();

    [GeneratedRegex(@"(?<role>Leitung\s+[A-ZÄÖÜ][\wäöüß-]+|Personalleiter(?:in)?|Recruiter(?:in)?|Personalreferent(?:in)?)")]
    private static partial Regex ContactRoleRx();

    // Two ways to be a company. The first is a name that ENDS in a legal form. On its own that
    // missed every employer this product's users actually apply to — a Klinikum, a Seniorenheim,
    // a Stadtverwaltung, a Universitätsklinikum carry no GmbH in the name — and the company is the
    // addressee of the Anschriftenfeld, so failing to find it leaves the letter addressed to
    // nobody. The second alternative therefore matches a name that BEGINS with the word that says
    // what kind of institution it is, and takes the proper nouns after it.
    [GeneratedRegex(
        @"[A-ZÄÖÜ][\wäöüß&.-]*(?:\s+[A-ZÄÖÜ][\wäöüß&.-]*){0,3}\s+(?:gGmbH|GmbH(?:\s*&\s*Co\.\s*KG)?|AG|SE|KG|mbH|e\.\s?V\.)" +
        @"|(?:Universitätsklinikum|Uniklinik(?:um)?|Klinikum|Klinik|Krankenhaus|Pflegeheim|Pflegedienst" +
        @"|Seniorenheim|Seniorenzentrum|Altenheim|Hospiz|Caritas|Diakonie|Johanniter|Malteser" +
        @"|Charité|Universität|Hochschule|Fachhochschule|Berufsschule|Kindertagesstätte" +
        @"|Stadtverwaltung|Stadt|Gemeinde|Landkreis|Bezirksamt|Ministerium|Bundesagentur|Landesamt)" +
        @"(?:\s+[A-ZÄÖÜ][\wäöüß&.-]*){0,3}")]
    private static partial Regex CompanyRx();

    [GeneratedRegex(@"[A-ZÄÖÜ][\wäöüß.-]*(?:stra(?:ß|ss)e|weg|allee|platz|ring|gasse)\s+\d+[a-z]?,?\s+\d{5}\s+[A-ZÄÖÜ][\wäöüß-]+",
        RegexOptions.IgnoreCase)]
    private static partial Regex AddressRx();

    [GeneratedRegex(@"(?:Referenz(?:nummer)?|Kennziffer|Stellen-?ID|Ref\.?)\s*:?\s*(?<code>[A-Z0-9][A-Z0-9/-]{3,})",
        RegexOptions.IgnoreCase)]
    private static partial Regex ReferenceRx();

    // The Stellenbezeichnung is the capitalised noun phrase immediately before the (m/w/d) marker,
    // not everything since the last full stop. A character class containing a space ran back over
    // the whole sentence, so "Klinikum Muenchen sucht eine Pflegefachkraft (m/w/d)" became the job
    // title — and that string then went into the Betreffzeile and the export file name.
    [GeneratedRegex(@"\((?:m/w/d|w/m/d|m/w/x|d/m/w)\)")]
    private static partial Regex GenderMarkerRx();

    // Only a vacancy verb makes the words before the role part of a sentence rather than part of
    // the title. Without that gate, cutting at the last article would eat the title's own — the
    // role in "Leiter der Buchhaltung (m/w/d)" is not "Buchhaltung".
    [GeneratedRegex(@"\b(?:sucht|suchen|gesucht|besetzt|besetzen|stellt\s+ein)\b(?:[^()]*?\b(?:einen|eine|einer|eines|ein|der|die|das)\b)?[\s:,-]*",
        RegexOptions.IgnoreCase)]
    private static partial Regex VacancyLeadRx();

    [GeneratedRegex(@"(?:zum nächstmöglichen Zeitpunkt|ab sofort|zum \d{1,2}\.\d{1,2}\.\d{4}|ab dem \d{1,2}\.\d{1,2}\.\d{4})",
        RegexOptions.IgnoreCase)]
    private static partial Regex StartRx();

    [GeneratedRegex(@"^[\t ]*[-*•·–]\s*(?<item>.+)$", RegexOptions.Multiline)]
    private static partial Regex BulletRx();

    // The list ends with its own LINE. Running to the next ".!?" instead let a heading like
    // "Ihr Profil:" swallow the bullet list under it and the sentence after that, so the Abgleich
    // listed the whole block as one "requirement" — dashes and all — plus a second one cut
    // mid-abbreviation out of "Ihre Bewerbung richten Sie bitte an Frau Dr. <name>". The bullets
    // are BulletRx's job; this pattern is only for the prose form that states them in a sentence.
    [GeneratedRegex(@"(?:Wir erwarten|Sie bringen mit|Ihr Profil|Das bringen Sie mit|Wir setzen voraus)\s*:?[ \t]*(?<list>[^.!?\r\n]+)",
        RegexOptions.IgnoreCase)]
    private static partial Regex ExpectationRx();

    [GeneratedRegex(@"Sie\s+(?<duty>(?:wirken|verantworten|betreuen|erstellen|übernehmen|führen|unterstützen|sind)\s+[^.,;!?]{6,140})",
        RegexOptions.IgnoreCase)]
    private static partial Regex DutyRx();
}
