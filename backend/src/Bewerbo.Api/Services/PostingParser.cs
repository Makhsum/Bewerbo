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
        AddIfFound(fields, ContactEmail(text));
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

    private static ExtractedField? ContactEmail(string text)
    {
        // The address the application is sent TO. A German advert prints more than one — a
        // Datenschutz address, an agency's, sometimes a careers portal's — so the one asked for
        // first is the one a sentence hands the application to ("Bewerbung an …", "senden Sie …
        // an …"). Only when no sentence says it does the first address in the text stand in, and
        // then it goes out as "pruefen": it is a guess, and this one ends up in the To: line.
        var asked = ApplicationEmailRx().Match(text);
        var m = asked.Success ? asked : EmailRx().Match(text);
        if (!m.Success) return null;

        var value = Tidy(asked.Success ? asked.Groups["mail"].Value : m.Value);
        return new ExtractedField
        {
            Key = "contactEmail",
            Value = value,
            Quote = value,
            Confidence = asked.Success ? "sicher" : "pruefen",
        };
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
            // The code is read off a label the posting wrote itself — "Referenznummer", "Kennziffer",
            // "Stellen-ID" — so nothing about it is inferred. It used to report "pruefen" on the
            // grounds that a wrong Betreffzeile is an immediate tell, but that asked the user to
            // check every reference the parser had in fact read correctly, and a marker that is
            // always on is a marker nobody reads.
            Confidence = "sicher",
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
        // The labelled form first, because it is the only place a bare date may be read: every
        // other date in an advert is a deadline, and a Bewerbungsfrist offered as the day the
        // applicant would start is worse than no date at all.
        var m = StartLabelRx().Match(text);
        if (!m.Success) m = StartRx().Match(text);
        if (!m.Success) return null;
        var value = Tidy(m.Groups["date"].Value);
        return new ExtractedField
        {
            Key = "start", Value = value, Quote = value, Confidence = "sicher",
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

    /// <summary>
    /// The semicolon is what a posting reaches for when the items themselves contain commas, so a
    /// list that used it came back as ONE requirement four items long. It separates here like the
    /// comma does.
    /// </summary>
    private static IEnumerable<string> SplitList(string clause) =>
        Regex.Split(clause, @"[,;]\s*|\s+und\s+|\s+sowie\s+")
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

    // Ansprechpartner(in) is written with a colon at least as often as with "ist" — "Ihre
    // Ansprechpartnerin: Frau Dr. Katrin Sommer" is an ordinary line of a German advert, and it
    // read as nothing at all. That failure is a quiet one: the CONTACT row says "im Inserat nicht
    // genannt" and carries the ADD marker, which reads as the advert's fault rather than ours.
    // Kontakt already had its colon, which is why only this label was affected.
    //
    // The name itself is held to ONE line. \s crosses a line break, so the two trailing words the
    // pattern allows for a double surname reached into whatever the advert wrote next: a posting
    // whose contact line is followed by "Anforderungen:" produced the contact "Frau Dr. Katrin
    // Sommer Anforderungen", and that name is what the Anschreiben opens with.
    //
    // Those trailing words know where the name ENDS only by the capital letter they have to start
    // with — and RegexOptions.IgnoreCase over the whole pattern voided exactly that. On the same
    // line, "an Frau Dr. Annika Weber unter Angabe der Referenznummer" therefore read the contact
    // as "Frau Dr. Annika Weber unter"; Salutation() takes the last word for the surname, so the
    // Anschreiben opened "Sehr geehrte Frau Dr. unter" and the Anschriftenfeld carried the stray
    // word too. Only the LABEL varies in case (an advert starts a sentence with "An Frau ..."), so
    // only the label is wrapped in an inline (?i:…) — the division CompanyRx below already makes.
    [GeneratedRegex(@"(?i:an|Ansprechpartner(?:in)?(?:\s+ist|\s*:)|wenden Sie sich an|Kontakt:?)\s+(?<name>(?i:Frau|Herr)[ \t]+(?:(?i:Dr|Prof)\.[ \t]+)*[A-ZÄÖÜ][\wäöüß-]+(?:[ \t]+[A-ZÄÖÜ][\wäöüß-]+){0,2})")]
    private static partial Regex ContactRx();

    [GeneratedRegex(@"(?<role>Leitung\s+[A-ZÄÖÜ][\wäöüß-]+|Personalleiter(?:in)?|Recruiter(?:in)?|Personalreferent(?:in)?)")]
    private static partial Regex ContactRoleRx();

    // An address a sentence hands the application to. The words between the verb and the address
    // are what a German advert puts there — "Ihre Bewerbung", "Ihre Unterlagen", "diese bitte" —
    // so the gap is bounded rather than open, or "Fragen beantwortet … , bewerben Sie sich unter
    // www…" would reach across half the advert to the wrong one.
    [GeneratedRegex(@"(?:Bewerbung(?:sunterlagen)?|Unterlagen|bewerben\s+Sie\s+sich|senden\s+Sie)[^@\r\n]{0,60}?\s(?:an|unter|per\s+E-?Mail\s+an)\s+(?<mail>[\w.+-]+@[\w-]+(?:\.[\w-]+)+)",
        RegexOptions.IgnoreCase)]
    private static partial Regex ApplicationEmailRx();

    [GeneratedRegex(@"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")]
    private static partial Regex EmailRx();

    // Two ways to be a company. The first is a name that ENDS in a legal form. On its own that
    // missed every employer this product's users actually apply to — a Klinikum, a Seniorenheim,
    // a Stadtverwaltung, a Universitätsklinikum carry no GmbH in the name — and the company is the
    // addressee of the Anschriftenfeld, so failing to find it leaves the letter addressed to
    // nobody. The second alternative therefore matches a name built on the word that says what
    // kind of institution it is, and takes the proper nouns after it.
    //
    // That institution word is the HEAD OF A COMPOUND, not a word standing on its own, and reading
    // it as one was how "Die Stadtklinik Augsburg" came out as the company "Stadt": the bare
    // alternative matched the first five letters and the group that takes the following proper
    // nouns then wanted a space, which "klinik" is not. German builds these names by compounding —
    // Stadt+klinik, Universitäts+klinikum, Kreis+krankenhaus, Fach+hochschule — so the first group
    // below matches a capitalised word that ENDS in one of the stems, whatever is welded in front
    // of it. The stems are matched case-insensitively because the second element of a compound is
    // written small; the lookahead keeps the requirement that the word itself starts with a
    // capital, which is what stops it firing inside running prose.
    //
    // The words in the second group get no such treatment on purpose. They are the ones that are
    // also the tail of ordinary words the advert is full of — "Stadt" ends every second German
    // town (Darmstadt, Neustadt, Ingolstadt), and reading "Die Firma in Darmstadt sucht …" as the
    // employer "Darmstadt" would trade this bug for its mirror image. They match as whole words,
    // and the \b at each end is what run 129 had to add to ReferenceRx for the same reason: a
    // literal alternative with no anchor matches inside a longer word.
    [GeneratedRegex(
        @"[A-ZÄÖÜ][\wäöüß&.-]*(?:\s+[A-ZÄÖÜ][\wäöüß&.-]*){0,3}\s+(?:gGmbH|GmbH(?:\s*&\s*Co\.\s*KG)?|AG|SE|KG|mbH|e\.\s?V\.)" +
        @"|(?:\b(?=[A-ZÄÖÜ])(?i:[\wäöüß-]*(?:klinikum|kliniken|klinik|krankenhaus|krankenhäuser" +
        @"|pflegeheim|pflegedienst|pflegezentrum|seniorenheim|seniorenzentrum|altenheim|hospiz" +
        @"|universität|hochschule|berufsschule|fachschule|kindertagesstätte|kindergarten" +
        @"|verwaltung))\b" +
        @"|\b(?:Caritas|Diakonie|Johanniter|Malteser|Charité|Stadt|Gemeinde|Landkreis|Bezirksamt" +
        @"|Ministerium|Bundesagentur|Landesamt)\b)" +
        @"(?:\s+[A-ZÄÖÜ][\wäöüß&.-]*){0,3}")]
    private static partial Regex CompanyRx();

    [GeneratedRegex(@"[A-ZÄÖÜ][\wäöüß.-]*(?:stra(?:ß|ss)e|weg|allee|platz|ring|gasse)\s+\d+[a-z]?,?\s+\d{5}\s+[A-ZÄÖÜ][\wäöüß-]+",
        RegexOptions.IgnoreCase)]
    private static partial Regex AddressRx();

    // The label has to be a WORD, which is what the two \b anchors are for. Without them the "Ref"
    // alternative matched the ref inside an ordinary German word and read the rest of it as the
    // code: "Frau Lena Sommer, Personalreferentin" produced the Referenz "erentin", and a Referat
    // or eine Reform would have produced "erat" and "orm" the same way. That is the worst shape a
    // parser bug can take here, because Reference() reports "sicher" — correctly, since the code is
    // read off a label the posting wrote itself — so no pill ever fired and the invented code went
    // into the Betreffzeile unchallenged. The real "Kennziffer NWH-2026-07" later in the same advert
    // was never reached, because this match came first.
    [GeneratedRegex(@"\b(?:Referenz(?:nummer)?|Kennziffer|Stellen-?ID|Ref)\b\.?\s*:?\s*(?<code>[A-Z0-9][A-Z0-9/-]{3,})",
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

    // "Eintritt: 01.03.2026" — the labelled form, and the only one allowed to read a plain date.
    // An advert prints several dates and only this one is the Eintritt; the label is what tells
    // them apart, exactly as the Referenz is read off a label the posting wrote itself. The colon
    // is optional so that "Eintritt zum nächstmöglichen Zeitpunkt" keeps being read here too.
    [GeneratedRegex(@"\b(?:Eintritt(?:stermin)?|Arbeitsbeginn|Starttermin|Beginn)\b\s*:?\s*(?<date>zum nächstmöglichen Zeitpunkt|ab sofort|(?:zum|ab dem)\s+\d{1,2}\.\d{1,2}\.\d{4}|\d{1,2}\.\d{1,2}\.\d{4})",
        RegexOptions.IgnoreCase)]
    private static partial Regex StartLabelRx();

    [GeneratedRegex(@"(?<date>zum nächstmöglichen Zeitpunkt|ab sofort|zum \d{1,2}\.\d{1,2}\.\d{4}|ab dem \d{1,2}\.\d{1,2}\.\d{4})",
        RegexOptions.IgnoreCase)]
    private static partial Regex StartRx();

    [GeneratedRegex(@"^[\t ]*[-*•·–]\s*(?<item>.+)$", RegexOptions.Multiline)]
    private static partial Regex BulletRx();

    // The list ends with its own LINE. Running to the next ".!?" instead let a heading like
    // "Ihr Profil:" swallow the bullet list under it and the sentence after that, so the Abgleich
    // listed the whole block as one "requirement" — dashes and all — plus a second one cut
    // mid-abbreviation out of "Ihre Bewerbung richten Sie bitte an Frau Dr. <name>". The bullets
    // are BulletRx's job; this pattern is only for the prose form that states them in a sentence.
    // "Anforderungen" and "Voraussetzungen" are the two words a German posting most often puts over
    // this list — the first is the word the Anforderungsabgleich is named after — and neither was
    // here, so a one-paragraph advert naming four Anforderungen produced NO requirements at all and
    // the Abgleich showed "0 von 0". The verb forms ("Wir setzen voraus") were already covered; it
    // is the headings that were missing.
    [GeneratedRegex(@"(?:Wir erwarten|Sie bringen mit|Ihr Profil|Das bringen Sie mit|Wir setzen voraus|Anforderungen|Voraussetzungen)\s*:?[ \t]*(?<list>[^.!?\r\n]+)",
        RegexOptions.IgnoreCase)]
    private static partial Regex ExpectationRx();

    [GeneratedRegex(@"Sie\s+(?<duty>(?:wirken|verantworten|betreuen|erstellen|übernehmen|führen|unterstützen|sind)\s+[^.,;!?]{6,140})",
        RegexOptions.IgnoreCase)]
    private static partial Regex DutyRx();
}
