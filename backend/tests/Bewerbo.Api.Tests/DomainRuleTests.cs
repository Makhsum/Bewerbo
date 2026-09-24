using System.Text.RegularExpressions;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Bewerbo.Api.Text;
using QuestPDF.Fluent;
using UglyToad.PdfPig;
using Xunit;

namespace Bewerbo.Api.Tests;

/// <summary>
/// One test per rule the README states, because these are the rules the product is. A regression
/// here is not a broken feature — it is an application that reads as foreign, which is the only
/// failure this product has.
/// </summary>
public class DomainRuleTests
{
    // -- the one architectural rule --------------------------------------------------------------

    [Fact]
    public void No_schema_handed_to_the_model_contains_a_layout_field()
    {
        // This is the mechanism, not a restatement of the intention: if somebody adds "fontSize" to
        // a content schema in good faith, this fails before the PDF drifts.
        OutputSchemas.AssertNoLayoutFields();
    }

    // -- Floskeln ---------------------------------------------------------------------------------

    [Theory]
    [InlineData("Hiermit bewerbe ich mich auf die Stelle.", "Hiermit bewerbe ich mich")]
    [InlineData("Ich bin ein Teamplayer und belastbar.", "Teamplayer")]
    [InlineData("Ich bin kommunikationsstark.", "kommunikationsstark")]
    public void Banned_phrases_are_named_where_they_occur(string text, string expected)
    {
        var hits = FloskelRules.Find(text);
        Assert.Contains(expected, hits, StringComparer.OrdinalIgnoreCase);
    }

    [Fact]
    public void A_clean_letter_reports_no_Floskeln()
    {
        Assert.Empty(FloskelRules.Find(
            "in Ihrer Anzeige suchen Sie jemanden für die Finanzbuchhaltung. " +
            "Genau das war vier Jahre lang meine tägliche Arbeit."));
    }

    // -- Anschreiben, not Motivationsschreiben ----------------------------------------------------

    [Fact]
    public void A_letter_that_reads_as_a_Motivationsschreiben_fails_the_review()
    {
        var letter = new LetterContent
        {
            Salutation = "Sehr geehrte Frau Weber",
            Subject = "Motivationsschreiben für den Studienplatz",
            Paragraphs = ["Ich bewerbe mich um ein Stipendium."],
        };

        var result = TextReview.Run(letter, "Frau Dr. Weber", null);
        var check = result.Checks.Single(c => c.Key == "anschreiben");
        Assert.Equal("fehler", check.Verdict);
        Assert.False(result.Passed);
    }

    // -- a proportion needs a text long enough to have one -----------------------------------------

    [Fact]
    public void The_Ich_proportion_is_not_reported_on_a_letter_of_three_sentences()
    {
        var letter = ShortLetter(
            "Ich bewerbe mich bei Ihnen. Die Stelle passt zu meiner Arbeit. Ich rufe Sie gern an.");

        var result = TextReview.Run(letter, "Frau Dr. Weber", null);

        // Two of three sentences open with "Ich", so the old check would have raised a hint on a
        // text that is far too short for the share to mean anything.
        Assert.DoesNotContain(result.Checks, c => c.Key == "perspektive");
        Assert.Contains("perspektive", result.NotChecked);
    }

    [Fact]
    public void A_check_that_was_left_out_is_not_counted_as_a_hint_and_not_reported_as_passed()
    {
        var result = TextReview.Run(ShortLetter("Ich bewerbe mich. Ich kann anfangen."), null, null);

        var perspektive = result.Checks.FirstOrDefault(c => c.Key == "perspektive");
        Assert.Null(perspektive);
        Assert.NotEmpty(result.NotChecked);
    }

    [Fact]
    public void The_Ich_proportion_is_reported_once_the_letter_is_long_enough()
    {
        var letter = ShortLetter(
            "Ich bewerbe mich bei Ihnen. Ich habe vier Jahre in der Finanzbuchhaltung gearbeitet. " +
            "Ich habe dort Monatsabschlüsse erstellt. Ich kenne DATEV und SAP. " +
            "Ich kann im Frühjahr anfangen. Über ein Gespräch freue ich mich.");

        var result = TextReview.Run(letter, "Frau Dr. Weber", null);

        var check = result.Checks.Single(c => c.Key == "perspektive");
        Assert.Equal("hinweis", check.Verdict);
        Assert.Equal(["5", "6"], check.DetailArgs);
        Assert.Empty(result.NotChecked);
    }

    private static LetterContent ShortLetter(string body) => new()
    {
        Salutation = "Sehr geehrte Frau Weber",
        Subject = "Bewerbung als Bilanzbuchhalterin",
        Paragraphs = [body],
    };

    // -- DIN 5008 Betreffzeile ---------------------------------------------------------------------

    [Fact]
    public void The_Betreffzeile_carries_the_Referenznummer_and_not_the_word_Betreff()
    {
        var posting = new Posting { JobTitle = "Bilanzbuchhalter (m/w/d)", Reference = "SBT-2026-0417" };
        var subject = ApplicationWriter.Subject(posting);

        Assert.Contains("SBT-2026-0417", subject);
        Assert.DoesNotContain("Betreff", subject, StringComparison.OrdinalIgnoreCase);
    }

    // -- address a person --------------------------------------------------------------------------

    [Theory]
    [InlineData("Frau Dr. Annika Weber", "Sehr geehrte Frau Dr. Weber")]
    [InlineData("Herr Klaus Meier", "Sehr geehrter Herr Meier")]
    [InlineData("", "Sehr geehrte Damen und Herren")]
    public void The_salutation_names_the_contact_where_the_posting_named_one(string contact, string expected)
    {
        Assert.Equal(expected, ApplicationWriter.Salutation(contact));
    }

    // -- the Eintritt in a sentence ----------------------------------------------------------------

    [Theory]
    [InlineData("01.03.2026", "am 01.03.2026")]
    [InlineData("ab sofort", "ab sofort")]
    [InlineData("zum nächstmöglichen Zeitpunkt", "zum nächstmöglichen Zeitpunkt")]
    [InlineData("zum 01.03.2026", "zum 01.03.2026")]
    public void The_Eintritt_gets_the_preposition_a_bare_date_is_missing(string start, string expected)
    {
        // The close of the letter reads "Anfangen kann ich {…}.". A posting that writes its date
        // as a phrase brings the preposition with it; "Eintritt: 01.03.2026" does not, and neither
        // does the correction dialog, so the letter said "Anfangen kann ich 01.03.2026."
        Assert.Equal(expected, ApplicationWriter.EntryDate(start));
    }

    // -- the posting parser --------------------------------------------------------------------------

    private const string Posting = """
        Bilanzbuchhalter (m/w/d) — Vollzeit, Stuttgart-Vaihingen

        Die Schwarzwald Technik GmbH sucht Verstärkung für das Team Finanzbuchhaltung. Sie wirken an
        Monats- und Jahresabschlüssen nach HGB mit und sind Ansprechpartner für Steuerberater.

        Wir erwarten sichere DATEV-Kenntnisse, Deutsch mindestens B2 und ein sicheres Auftreten.

        Bitte richten Sie Ihre Unterlagen an Frau Dr. Annika Weber, Leitung Personal, unter Angabe
        der Referenznummer SBT-2026-0417. Eintritt zum nächstmöglichen Zeitpunkt.

        Schwarzwald Technik GmbH, Industriestraße 8, 70563 Stuttgart
        """;

    [Theory]
    [InlineData("contact", "Frau Dr. Annika Weber")]
    [InlineData("company", "Schwarzwald Technik GmbH")]
    [InlineData("reference", "SBT-2026-0417")]
    [InlineData("title", "Bilanzbuchhalter (m/w/d)")]
    [InlineData("start", "zum nächstmöglichen Zeitpunkt")]
    public void The_parser_reads_the_fields_the_letter_needs(string key, string expected)
    {
        var extract = PostingParser.Parse(Posting);
        var field = extract.Fields.SingleOrDefault(f => f.Key == key);

        Assert.NotNull(field);
        Assert.Equal(expected, field!.Value);
    }

    [Theory]
    [InlineData("Ihre Ansprechpartnerin: Frau Dr. Katrin Sommer", "Frau Dr. Katrin Sommer")]
    [InlineData("Ansprechpartner: Herr Klaus Meier", "Herr Klaus Meier")]
    [InlineData("Ihre Ansprechpartnerin ist Frau Dr. Katrin Sommer", "Frau Dr. Katrin Sommer")]
    [InlineData("Bitte wenden Sie sich an Frau Dr. Katrin Sommer", "Frau Dr. Katrin Sommer")]
    public void The_contact_is_read_whether_the_label_uses_a_colon_or_the_word_ist(
        string line, string expected)
    {
        // The colon form is at least as common as "ist" in a German advert, and it read as nothing:
        // the CONTACT row said the posting had named nobody and asked the user to add what was
        // written in front of them. Kontakt was already allowed its colon, so only this label broke.
        var extract = PostingParser.Parse(line);
        var field = extract.Fields.SingleOrDefault(f => f.Key == "contact");

        Assert.NotNull(field);
        Assert.Equal(expected, field!.Value);
    }

    [Fact]
    public void The_contact_name_stops_at_the_end_of_its_line()
    {
        // The pattern allows two trailing words for a double surname, and \s crosses a line break,
        // so the next line of the advert was read as part of the name: "Frau Dr. Katrin Sommer
        // Anforderungen". That string is what the Anschreiben opens with.
        var extract = PostingParser.Parse(
            "Ihre Ansprechpartnerin: Frau Dr. Katrin Sommer\nAnforderungen:\n- Deutsch auf Niveau C1");

        Assert.Equal("Frau Dr. Katrin Sommer", extract.Fields.Single(f => f.Key == "contact").Value);
    }

    [Theory]
    [InlineData("Bitte richten Sie Ihre Unterlagen an Frau Dr. Annika Weber unter Angabe der Referenznummer.")]
    [InlineData("Wenden Sie sich an Frau Katrin Sommer oder an ihre Vertretung.")]
    [InlineData("Ansprechpartner: Herr Klaus Meier steht Ihnen gerne zur Verfuegung.")]
    public void The_contact_name_stops_before_the_first_word_that_is_not_capitalised(string line)
    {
        // The two trailing words the pattern allows for a double surname know where the name ends
        // only by the capital letter they have to start with, and RegexOptions.IgnoreCase over the
        // whole pattern voided it. On one line "an Frau Dr. Annika Weber unter Angabe der
        // Referenznummer" read as "Frau Dr. Annika Weber unter", and Salutation() takes the last
        // word for the surname: the letter opened "Sehr geehrte Frau Dr. unter".
        var extract = PostingParser.Parse(line);
        var field = extract.Fields.Single(f => f.Key == "contact");

        Assert.DoesNotContain(field.Value.Split(' '), w => char.IsLower(w[0]));
    }

    [Theory]
    [InlineData("Eintritt: 01.03.2026", "01.03.2026")]
    [InlineData("Eintrittstermin: 01.03.2026", "01.03.2026")]
    [InlineData("Beginn: ab sofort", "ab sofort")]
    [InlineData("Eintritt zum nächstmöglichen Zeitpunkt.", "zum nächstmöglichen Zeitpunkt")]
    [InlineData("Wir besetzen die Stelle ab sofort.", "ab sofort")]
    public void The_starting_date_is_read_from_the_label_as_well_as_from_the_phrase(
        string line, string expected)
    {
        // "Eintritt: 01.03.2026" is how most adverts write it, and only the phrases were read, so
        // the STARTING DATE row said the posting had named none. The value is the date alone — the
        // label was never part of it, and this field goes into the letter.
        var extract = PostingParser.Parse(line);
        var field = extract.Fields.SingleOrDefault(f => f.Key == "start");

        Assert.NotNull(field);
        Assert.Equal(expected, field!.Value);
    }

    [Fact]
    public void A_date_without_a_label_is_not_read_as_the_starting_date()
    {
        // The other dates an advert prints are deadlines. A Bewerbungsfrist offered as the day the
        // applicant would start is worse than the empty field the user can fill in themselves,
        // because Confidence is "sicher" here and no pill would ask them to look at it.
        var extract = PostingParser.Parse("Bewerbungsfrist: 15.02.2026. Wir freuen uns auf Sie.");

        Assert.DoesNotContain(extract.Fields, f => f.Key == "start");
    }

    [Theory]
    [InlineData("Ihre Ansprechpartnerin ist Frau Lena Sommer, Personalreferentin.")]
    [InlineData("Wir suchen für unser Referat Finanzen eine Sachbearbeiterin.")]
    [InlineData("Eine Reform der Abläufe begleiten Sie mit.")]
    public void A_ref_inside_an_ordinary_German_word_is_not_a_Referenznummer(string line)
    {
        // "Personalreferentin" produced the Referenz "erentin", and Referat und Reform would have
        // produced "erat" and "orm". Reference() reports "sicher" — rightly, the code is read off a
        // label the posting wrote itself — so no pill fired and the invented code went into the
        // Betreffzeile with nothing asking the user to look at it.
        var extract = PostingParser.Parse(line);

        Assert.DoesNotContain(extract.Fields, f => f.Key == "reference");
    }

    [Theory]
    [InlineData("Die Referenznummer bitte unbedingt angeben.")]
    [InlineData("Bitte richten Sie Ihre Unterlagen an uns unter Angabe der Referenznummer dieser Anzeige.")]
    [InlineData("Nennen Sie die Kennziffer dieser Stelle.")]
    public void An_ordinary_word_after_the_label_is_not_a_Referenznummer(string line)
    {
        // The code is capitals and digits, and that is all that tells it apart from the next word of
        // the sentence — RegexOptions.IgnoreCase over the pattern made [A-Z0-9] match any letter, so
        // these three read the Referenz "bitte", "dieser" and "dieser". Same silent path as the ref
        // inside a word above: reported "sicher", no pill, straight into the Betreffzeile.
        var extract = PostingParser.Parse(line);

        Assert.DoesNotContain(extract.Fields, f => f.Key == "reference");
    }

    [Fact]
    public void The_Kennziffer_is_read_even_when_a_word_containing_ref_comes_first()
    {
        // The two live in one advert, and the false match came first, so it won and the real code
        // was never reached at all.
        var extract = PostingParser.Parse(
            "Ihre Ansprechpartnerin ist Frau Lena Sommer, Personalreferentin. Wir freuen uns auf "
            + "Ihre Unterlagen unter der Kennziffer NWH-2026-07.");

        Assert.Equal("NWH-2026-07", extract.Fields.Single(f => f.Key == "reference").Value);
    }

    [Theory]
    [InlineData("Bitte senden Sie Ihre Bewerbung an bewerbung@vogt-schneider.de", "bewerbung@vogt-schneider.de")]
    [InlineData("Ihre Bewerbungsunterlagen senden Sie bitte per E-Mail an jobs@klinikum-nord.de.", "jobs@klinikum-nord.de")]
    [InlineData("Bewerben Sie sich unter karriere@schwarzwald-technik.de", "karriere@schwarzwald-technik.de")]
    [InlineData("Ihre Unterlagen an frau.weber@stadt-stuttgart.de", "frau.weber@stadt-stuttgart.de")]
    public void The_address_the_posting_hands_the_application_to_is_read(string line, string expected)
    {
        // This one ends up in the To: line of a real e-mail, so it is read from the sentence that
        // ASKS for the application rather than from the first address in the advert.
        var extract = PostingParser.Parse(line);
        var field = extract.Fields.Single(f => f.Key == "contactEmail");

        Assert.Equal(expected, field.Value);
        Assert.Equal("sicher", field.Confidence);
    }

    [Fact]
    public void An_address_no_sentence_asked_for_is_offered_but_marked_to_be_checked()
    {
        // A German advert prints more than one address. Picking the Datenschutz one and presenting
        // it as fact would put the application in front of the wrong reader; the posting screen
        // shows a "pruefen" field with a pill, which is the user's cue to correct it.
        var extract = PostingParser.Parse(
            "Fragen zum Datenschutz beantwortet datenschutz@schwarzwald-technik.de.");

        var field = extract.Fields.Single(f => f.Key == "contactEmail");
        Assert.Equal("datenschutz@schwarzwald-technik.de", field.Value);
        Assert.Equal("pruefen", field.Confidence);
    }

    [Fact]
    public void A_posting_that_names_no_address_yields_no_field_rather_than_an_empty_one()
    {
        var extract = PostingParser.Parse("Wir freuen uns auf Ihre Bewerbung ueber unser Portal.");

        Assert.DoesNotContain(extract.Fields, f => f.Key == "contactEmail");
    }

    [Theory]
    [InlineData("Bitte geben Sie die Referenznummer SBT-2026-0417 an.", "SBT-2026-0417")]
    [InlineData("Referenz LOG-2026-11", "LOG-2026-11")]
    [InlineData("Kennziffer: NWH-2026-07", "NWH-2026-07")]
    [InlineData("Stellen-ID 4711-AB", "4711-AB")]
    [InlineData("Ref. SBT-2026-0417", "SBT-2026-0417")]
    [InlineData("Ref 4711-AB", "4711-AB")]
    public void Every_label_a_posting_writes_its_reference_under_is_still_read(string line, string expected)
    {
        // The word boundaries must not cost the abbreviated forms: "Ref." and a bare "Ref" before
        // the code are both how an advert writes it.
        var extract = PostingParser.Parse(line);

        Assert.Equal(expected, extract.Fields.Single(f => f.Key == "reference").Value);
    }

    [Theory]
    [InlineData("Klinikum München Süd sucht eine Pflegefachkraft (m/w/d).", "Klinikum München Süd")]
    [InlineData("Das Universitätsklinikum Heidelberg sucht Verstärkung.", "Universitätsklinikum Heidelberg")]
    [InlineData("Die Stadt Augsburg sucht eine Sachbearbeiterin.", "Stadt Augsburg")]
    [InlineData("Seniorenzentrum Nord sucht Pflegekräfte.", "Seniorenzentrum Nord")]
    [InlineData("Die Schwarzwald Technik GmbH sucht Verstärkung.", "Schwarzwald Technik GmbH")]
    // The institution word is the head of a COMPOUND. Reading it as a word standing on its own
    // returned "Stadt" for the first of these, out of the middle of "Stadtklinik".
    [InlineData("Die Stadtklinik Augsburg sucht eine Pflegefachkraft (m/w/d).", "Stadtklinik Augsburg")]
    [InlineData("Das Kreiskrankenhaus Erding sucht Verstärkung.", "Kreiskrankenhaus Erding")]
    [InlineData("Die Fachhochschule Kiel sucht eine Dozentin.", "Fachhochschule Kiel")]
    [InlineData("Die Stadtverwaltung Bonn sucht eine Sachbearbeiterin.", "Stadtverwaltung Bonn")]
    [InlineData("Der Waldkindergarten Sonnenschein sucht eine Erzieherin.", "Waldkindergarten Sonnenschein")]
    public void An_employer_without_a_legal_form_in_its_name_is_still_found(string line, string expected)
    {
        // Hospitals, care homes, universities and town halls carry no GmbH — and they are exactly
        // the employers this product's users apply to. The company is the addressee of the
        // Anschriftenfeld, so not finding it leaves the letter addressed to nobody.
        var extract = PostingParser.Parse(line);

        Assert.Equal(expected, extract.Fields.Single(f => f.Key == "company").Value);
    }

    [Theory]
    [InlineData("Die Firma in Darmstadt sucht eine Lageristin.")]
    [InlineData("Unser Standort in Ingolstadt sucht Verstärkung.")]
    [InlineData("Wir suchen für unser Team in Neustadt eine Erzieherin.")]
    public void A_town_whose_name_ends_in_stadt_is_not_read_as_the_employer(string line)
    {
        // The mirror image of the "Stadtklinik" bug, and the reason "Stadt" is matched as a whole
        // word while "-klinik" is allowed to be the tail of a compound: half the towns in Germany
        // end in -stadt, and naming one as the addressee of the Anschriftenfeld would be just as
        // wrong as naming "Stadt". No company read at all is the honest answer here — the field
        // then shows as missing and asks the user to add it.
        var extract = PostingParser.Parse(line);

        Assert.DoesNotContain(extract.Fields, f => f.Key == "company");
    }

    [Theory]
    [InlineData("Klinikum München sucht eine Pflegefachkraft (m/w/d).", "Pflegefachkraft (m/w/d)")]
    [InlineData("Wir suchen zum 01.01.2027 eine Leitende Buchhalterin (m/w/d).", "Leitende Buchhalterin (m/w/d)")]
    public void The_job_title_does_not_swallow_the_sentence_in_front_of_it(string line, string expected)
    {
        var extract = PostingParser.Parse(line);

        Assert.Equal(expected, extract.Fields.Single(f => f.Key == "title").Value);
    }

    [Theory]
    [InlineData("Senior Softwareentwickler C#/.NET (m/w/d)", "Senior Softwareentwickler C#/.NET (m/w/d)")]
    [InlineData("C++ Entwickler (m/w/d)", "C++ Entwickler (m/w/d)")]
    [InlineData("Fachkraft für Lagerlogistik (m/w/d)", "Fachkraft für Lagerlogistik (m/w/d)")]
    [InlineData("Kaufmann im Einzelhandel (m/w/d)", "Kaufmann im Einzelhandel (m/w/d)")]
    [InlineData("Leiter der Buchhaltung (m/w/d)", "Leiter der Buchhaltung (m/w/d)")]
    [InlineData("Pflegefachkraft (m/w/d)", "Pflegefachkraft (m/w/d)")]
    public void The_job_title_keeps_the_punctuation_and_the_small_words_it_was_written_with(
        string line, string expected)
    {
        // Reading the title backwards from the marker over capitalised words only kept the tail:
        // "NET (m/w/d)" for a C#/.NET role, "Lagerlogistik (m/w/d)" for "Fachkraft für
        // Lagerlogistik". Both shapes are ordinary, and the wrong Bezeichnung is carried into the
        // Betreffzeile, the first line of the Anschreiben and the name of the exported PDF.
        var extract = PostingParser.Parse(line);

        Assert.Equal(expected, extract.Fields.Single(f => f.Key == "title").Value);
    }

    [Fact]
    public void A_title_read_out_of_a_sentence_is_offered_for_checking_rather_than_called_certain()
    {
        var headline = PostingParser.Parse("Fachkraft für Lagerlogistik (m/w/d)");
        var sentence = PostingParser.Parse("Klinikum München sucht eine Pflegefachkraft (m/w/d).");

        Assert.Equal("sicher", headline.Fields.Single(f => f.Key == "title").Confidence);
        Assert.Equal("pruefen", sentence.Fields.Single(f => f.Key == "title").Confidence);
    }

    [Fact]
    public void A_requirements_heading_does_not_swallow_the_list_under_it_or_the_sentence_after_it()
    {
        // "Ihr Profil:" followed by bullets used to be read as prose running to the next full
        // stop — which was the one in "Frau Dr.". The Abgleich then listed the whole bullet block
        // as one requirement, dashes included, and a second one cut off mid-abbreviation.
        var extract = PostingParser.Parse("""
            Fachkraft für Lagerlogistik (m/w/d)

            Ihr Profil:
            - Abgeschlossene Berufsausbildung im Lager
            - Sehr gute Deutschkenntnisse (mindestens B2) und gute Englischkenntnisse

            Ihre Bewerbung richten Sie bitte an Frau Dr. Annette Kleinschmidt.
            """);

        Assert.Equal(
        [
            "Abgeschlossene Berufsausbildung im Lager",
            "Sehr gute Deutschkenntnisse (mindestens B2) und gute Englischkenntnisse",
        ], extract.Requirements.Select(r => r.Text));
    }

    [Fact]
    public void A_requirements_sentence_without_bullets_is_still_split_into_its_parts()
    {
        // The prose form is why ExpectationRx exists at all; narrowing it to its own line must not
        // cost the posting that states the same expectations in one sentence.
        var extract = PostingParser.Parse(Posting);

        Assert.Contains("sichere DATEV-Kenntnisse", extract.Requirements.Select(r => r.Text));
        Assert.Contains("Deutsch mindestens B2", extract.Requirements.Select(r => r.Text));
    }

    [Fact]
    public void A_one_paragraph_posting_that_names_its_Anforderungen_still_yields_them()
    {
        // The whole advert on one line, the list introduced by the word the Abgleich is named
        // after and separated by semicolons. Neither the heading nor the semicolon was known, so
        // this posting produced NO requirements and the Abgleich showed "0 von 0" — which reads
        // to the applicant like "nothing you have counts".
        var extract = PostingParser.Parse(
            "Stellenausschreibung: Fachkraft Lagerlogistik (m/w/d), Referenz LOG-2026-11. " +
            "Die Muster Logistik GmbH, Hauptstrasse 12, 70173 Stuttgart, sucht zum " +
            "naechstmoeglichen Zeitpunkt eine Fachkraft fuer Lagerlogistik. Anforderungen: " +
            "abgeschlossene Ausbildung im Bereich Lagerlogistik; Erfahrung mit Gabelstaplern; " +
            "Deutschkenntnisse ab Niveau B2; Bereitschaft zur Schichtarbeit. " +
            "Wir bieten eine unbefristete Anstellung.");

        Assert.Equal(
        [
            "abgeschlossene Ausbildung im Bereich Lagerlogistik",
            "Erfahrung mit Gabelstaplern",
            "Deutschkenntnisse ab Niveau B2",
            "Bereitschaft zur Schichtarbeit",
        ], extract.Requirements.Select(r => r.Text));
    }

    [Fact]
    public void A_Voraussetzungen_heading_over_bullets_still_leaves_the_bullets_to_their_own_rule()
    {
        // The heading forms were added for the prose case only. A heading on its own line must
        // still not swallow the list under it — the same guarantee the Ihr-Profil case already has.
        var extract = PostingParser.Parse("""
            Fachkraft für Lagerlogistik (m/w/d)

            Voraussetzungen:
            - Abgeschlossene Berufsausbildung im Lager
            - Bereitschaft zur Schichtarbeit
            """);

        Assert.Equal(
        [
            "Abgeschlossene Berufsausbildung im Lager",
            "Bereitschaft zur Schichtarbeit",
        ], extract.Requirements.Select(r => r.Text));
    }

    [Fact]
    public void The_company_does_not_swallow_the_article_or_the_line_above_it()
    {
        var extract = PostingParser.Parse(Posting);
        var company = extract.Fields.Single(f => f.Key == "company").Value;

        Assert.DoesNotContain("Die ", company);
        Assert.DoesNotContain("Vaihingen", company);
    }

    [Fact]
    public void Every_extracted_field_can_be_found_in_the_posting_it_came_from()
    {
        var extract = PostingParser.Parse(Posting);

        foreach (var field in extract.Fields)
        {
            var span = EvidenceLocator.Locate(Posting, field.Quote);
            Assert.True(span is not null, $"The quote for '{field.Key}' was not found in the posting.");
        }
    }

    [Fact]
    public void A_quote_that_is_not_in_the_posting_yields_no_span_rather_than_a_wrong_one()
    {
        // A wrong highlight is worse than none: it shows the user words the value did not come from.
        Assert.Null(EvidenceLocator.Locate(Posting, "Geschäftsführer Hans Beispiel"));
    }

    [Fact]
    public void A_quote_that_crossed_a_line_break_is_still_located()
    {
        var span = EvidenceLocator.Locate(Posting, "Sie wirken an Monats- und Jahresabschlüssen");
        Assert.NotNull(span);
    }

    // -- the Anforderungsabgleich ---------------------------------------------------------------------

    private static Domain.Profile SampleProfile() => new()
    {
        FirstName = "Olena", LastName = "Kovalchuk", City = "Stuttgart",
        Experience =
        [
            new ExperienceEntry
            {
                Position = "Leitende Buchhalterin", Employer = "Agrosvit GmbH", Location = "Kyjiw",
                From = new DateOnly(2019, 4, 1), To = new DateOnly(2023, 7, 31),
                Duties = "Jahresabschlüsse nach HGB\nAnsprechpartnerin für Steuerberater",
            },
            new ExperienceEntry
            {
                Position = "Buchhalterin", Employer = "Mayer & Partner GmbH", Location = "Stuttgart",
                From = new DateOnly(2023, 9, 1), To = null,
                Duties = "DATEV für 14 Mandanten",
            },
        ],
        Languages =
        [
            new LanguageSkill { Language = "Deutsch", Level = "B2", CertificateOnFile = false },
        ],
    };

    [Fact]
    public void A_requirement_the_profile_proves_is_belegt_and_names_its_evidence()
    {
        var match = RequirementMatcher.Match(SampleProfile(),
            [new ExtractedRequirement { Text = "sichere DATEV-Kenntnisse" }]);

        var requirement = match.Requirements.Single();
        Assert.Equal(RequirementState.Belegt, requirement.State);
        Assert.Contains("Mayer & Partner GmbH", requirement.Evidence);
    }

    [Fact]
    public void A_language_level_stated_but_not_certified_is_offen_with_the_action_that_closes_it()
    {
        var match = RequirementMatcher.Match(SampleProfile(),
            [new ExtractedRequirement { Text = "Deutsch mindestens B2" }]);

        var requirement = match.Requirements.Single();
        Assert.Equal(RequirementState.Offen, requirement.State);
        Assert.Equal("Nachweis hochladen", requirement.Action);
    }

    [Fact]
    public void What_holds_for_every_offen_requirement_alike_is_left_to_the_group_not_repeated_per_row()
    {
        // Two unproven languages used to print "Im Profil angegeben, Zertifikat fehlt" twice, once
        // under each row. The sentence is true of every offen row by definition, so the screen says
        // it once for the group. The action stays on the row: it files a different Nachweis each
        // time, and a button is not a sentence.
        var profile = SampleProfile();
        profile.Languages.Add(new LanguageSkill
        {
            Language = "Englisch", Level = "B2", CertificateOnFile = false,
        });

        var match = RequirementMatcher.Match(profile,
        [
            new ExtractedRequirement { Text = "Deutschkenntnisse auf Niveau B2" },
            new ExtractedRequirement { Text = "Englischkenntnisse auf Niveau B1" },
        ]);

        var offen = match.Requirements.Where(r => r.State == RequirementState.Offen).ToList();
        Assert.Equal(2, offen.Count);
        Assert.All(offen, r => Assert.Equal("", r.Evidence));
        Assert.All(offen, r => Assert.Equal("", r.EvidenceKind));
        Assert.All(offen, r => Assert.Equal("nachweis_ablegen", r.ActionKind));

        // The language is what makes the action row-specific, and it must survive.
        Assert.Equal(["Deutsch", "Englisch"], offen.Select(r => r.Language));
    }

    [Theory]
    [InlineData("Deutsch B2")]
    [InlineData("Deutsch mindestens B2")]
    [InlineData("Deutschkenntnisse auf Niveau B2")]
    [InlineData("Deutschkenntnisse mindestens auf dem Niveau B2")]
    public void A_language_requirement_is_recognised_however_the_posting_phrases_it(string text)
    {
        // "Deutschkenntnisse auf Niveau B2" puts 22 characters between the language and the level.
        // A tighter window read this as no language requirement at all and told an applicant with
        // B2 in her profile that her German was "nicht vorhanden".
        var match = RequirementMatcher.Match(SampleProfile(), [new ExtractedRequirement { Text = text }]);

        Assert.Equal(RequirementState.Offen, match.Requirements.Single().State);
    }

    [Fact]
    public void A_qualification_the_profile_does_not_have_is_nicht_belegt_and_stays_out_of_the_letter()
    {
        var requirements = new List<ExtractedRequirement>
        {
            new() { Text = "Geprüfte Bilanzbuchhalterin (IHK)" },
            new() { Text = "sichere DATEV-Kenntnisse" },
        };
        var match = RequirementMatcher.Match(SampleProfile(), requirements);

        Assert.Equal(RequirementState.NichtBelegt,
            match.Requirements.Single(r => r.Text.Contains("IHK")).State);

        // The guard that matters: the letter writer is only ever handed the belegt ones.
        Assert.DoesNotContain(match.Claimable, r => r.Text.Contains("IHK"));
    }

    // -- what the posting demands of the Mappe ----------------------------------------------------

    [Theory]
    [InlineData("Bitte senden Sie uns Ihre Zeugnisse.")]
    [InlineData("Wir freuen uns auf Ihre vollständigen Bewerbungsunterlagen.")]
    [InlineData("Arbeitszeugnisse der letzten drei Jahre legen Sie bitte bei.")]
    public void A_posting_that_asks_for_Zeugnisse_demands_an_Arbeitszeugnis_and_it_is_outstanding(string text)
    {
        // The demand is read off the whole advert: none of these three sentences survives into the
        // requirements list, and all three are how a German advert asks for papers.
        var demands = DocumentDemandService.Demands(SampleProfile(), text, []);

        var zeugnis = Assert.Single(demands, d => d.Kind == DocumentKind.Arbeitszeugnis);
        Assert.False(zeugnis.OnFile);

        // The quote has to be the advert's own words, verbatim — it is shown to the user as the
        // reason this row is there, and a paraphrase would be unverifiable.
        Assert.NotEmpty(zeugnis.Quote);
        Assert.Contains(zeugnis.Quote, text, StringComparison.Ordinal);
    }

    [Fact]
    public void The_same_demand_reads_as_on_file_once_the_Mappe_holds_that_kind()
    {
        var profile = SampleProfile();
        profile.Documents.Add(new StoredDocument
        {
            Title = "Arbeitszeugnis Agrosvit GmbH", Kind = DocumentKind.Arbeitszeugnis, PageCount = 2,
        });

        var demands = DocumentDemandService.Demands(profile, "Bitte senden Sie uns Ihre Zeugnisse.", []);

        Assert.True(Assert.Single(demands, d => d.Kind == DocumentKind.Arbeitszeugnis).OnFile);
    }

    [Fact]
    public void Anerkennung_as_a_benefit_is_not_a_demand_for_an_anabin_extract()
    {
        // "Wertschätzung und Anerkennung" is stock wording in the benefits half of a German advert.
        // Reading it as a demand put an outstanding anabin extract on every applicant's Mappe.
        var demands = DocumentDemandService.Demands(SampleProfile(),
            "Wir bieten ein familiäres Team, Wertschätzung und Anerkennung Ihrer Leistung.", []);

        Assert.DoesNotContain(demands, d => d.Kind == DocumentKind.AnabinAuszug);
    }

    [Fact]
    public void An_anerkennung_that_names_the_Abschluss_is_a_demand()
    {
        var demands = DocumentDemandService.Demands(SampleProfile(),
            "Voraussetzung ist die Anerkennung Ihres auslaendischen Abschlusses.", []);

        Assert.Contains(demands, d => d.Kind == DocumentKind.AnabinAuszug);
    }

    [Fact]
    public void A_quote_taken_off_a_bullet_does_not_keep_the_bullet_marker()
    {
        var demands = DocumentDemandService.Demands(SampleProfile(),
            "Anforderungen:\n- Anerkennung Ihres auslaendischen Abschlusses\n", []);

        Assert.StartsWith("Anerkennung",
            Assert.Single(demands, d => d.Kind == DocumentKind.AnabinAuszug).Quote);
    }

    [Fact]
    public void A_demanded_Sprachnachweis_is_on_file_only_when_the_language_says_so()
    {
        // The rule that matters: a document of kind Sprachnachweis lying in the Mappe does NOT make
        // the Nachweis count. CertificateOnFile is what the Abgleich, the readiness score and the
        // letter writer read, so anything else would say "on file" here while the requirement it
        // belongs to stayed offen.
        var profile = SampleProfile();
        profile.Documents.Add(new StoredDocument
        {
            Title = "Goethe-Zertifikat B2", Kind = DocumentKind.Sprachnachweis, PageCount = 1,
        });

        var demands = DocumentDemandService.Demands(profile, "",
            [new ExtractedRequirement { Text = "Deutsch mindestens B2" }]);

        var nachweis = Assert.Single(demands, d => d.Kind == DocumentKind.Sprachnachweis);
        Assert.False(nachweis.OnFile);
        Assert.Equal("Sprachnachweis Deutsch B2", nachweis.Title);

        profile.Languages[0].CertificateOnFile = true;
        Assert.True(Assert.Single(DocumentDemandService.Demands(profile, "",
            [new ExtractedRequirement { Text = "Deutsch mindestens B2" }])).OnFile);
    }

    [Fact]
    public void A_posting_that_asks_for_no_document_demands_nothing()
    {
        // An empty section is the honest answer. Listing all four kinds "just in case" would tell
        // the user this advert wants papers it never mentioned.
        var demands = DocumentDemandService.Demands(SampleProfile(),
            "Wir suchen eine Buchhalterin fuer unser Team in Stuttgart.", []);

        Assert.Empty(demands);
    }

    [Fact]
    public void The_generated_letter_claims_nothing_that_is_not_belegt()
    {
        var profile = SampleProfile();
        var posting = new Posting
        {
            JobTitle = "Bilanzbuchhalter (m/w/d)", Reference = "SBT-2026-0417",
            ContactName = "Frau Dr. Annika Weber", Company = "Schwarzwald Technik GmbH",
            StartDate = "zum nächstmöglichen Zeitpunkt",
        };
        var match = RequirementMatcher.Match(profile,
        [
            new ExtractedRequirement { Text = "sichere DATEV-Kenntnisse" },
            new ExtractedRequirement { Text = "Geprüfte Bilanzbuchhalterin (IHK)" },
        ]);

        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var letter = writer.WriteLetterAsync(profile, posting, match, LetterTone.Sachlich).Result;
        var body = string.Join(" ", letter.Paragraphs);

        Assert.DoesNotContain("IHK", body);
        Assert.Contains("DATEV", body);
    }

    [Fact]
    public void A_job_that_has_ended_is_not_written_up_as_present_employment()
    {
        // Every post in this profile is over. Saying "Seit März 2019 arbeite ich bei X" would tell
        // the employer the applicant is still employed there — the one fact they can check first.
        var profile = SampleProfile();
        profile.Experience[1].To = new DateOnly(2024, 5, 31);

        var posting = new Posting { JobTitle = "Buchhalter (m/w/d)", Reference = "X-1" };
        var match = RequirementMatcher.Match(profile, []);

        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var letter = writer.WriteLetterAsync(profile, posting, match, LetterTone.Sachlich).Result;
        var body = string.Join(" ", letter.Paragraphs);

        Assert.DoesNotContain("arbeite ich", body);
        Assert.Contains("gearbeitet", body);
        Assert.Contains("bis Mai 2024", body);
    }

    [Fact]
    public void An_ongoing_job_is_still_written_in_the_present()
    {
        var posting = new Posting { JobTitle = "Buchhalter (m/w/d)", Reference = "X-1" };
        var match = RequirementMatcher.Match(SampleProfile(), []);

        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var letter = writer.WriteLetterAsync(SampleProfile(), posting, match, LetterTone.Sachlich).Result;

        Assert.Contains("arbeite ich", string.Join(" ", letter.Paragraphs));
    }

    [Fact]
    public void An_empty_profile_is_nought_per_cent_complete()
    {
        // All() over an empty list is true, which used to award the "every entry has duty lines"
        // point to a profile with no entries and open the app at "8 % complete".
        Assert.Equal(0, ReadinessService.Completeness(new Domain.Profile()));
    }

    [Fact]
    public void An_empty_profile_is_told_what_is_outstanding_and_is_not_sent_to_start_an_application()
    {
        // The first screen a new user sees used to offer a score of 0 out of 100 and, as its one
        // action, an application the empty profile cannot produce a Lebenslauf for.
        var profile = new Domain.Profile();
        var overview = ReadinessService.Build(
            profile, TimelineService.Build(profile, new DateOnly(2026, 9, 23)), []);

        Assert.False(overview.CanStartApplication);
        Assert.All(overview.NextSteps, s => Assert.Equal("profil", s.Target));
        Assert.Equal(["person", "beruf"], overview.NextSteps.Select(s => s.Key));
    }

    [Fact]
    public void A_profile_with_no_name_and_no_Anschrift_has_the_Briefkopf_outstanding()
    {
        // Half of Completeness is the person's own details, and none of it produced a step — so the
        // Übersicht read "nothing outstanding" over a profile that cannot address a letter.
        var profile = SampleProfile();
        profile.FirstName = "";
        profile.Street = "";
        profile.Email = "olena.kovalchuk@example.de";

        var steps = ReadinessService.Build(
            profile, TimelineService.Build(profile, new DateOnly(2026, 9, 23)), []).NextSteps;

        var step = Assert.Single(steps, s => s.Key == "person");
        Assert.Contains("Name", step.Detail);
        Assert.Contains("Anschrift", step.Detail);
        // An Email is enough to be reachable, so the Kontakt is not outstanding.
        Assert.DoesNotContain("Kontakt", step.Detail);
    }

    [Fact]
    public void One_Berufserfahrung_is_what_makes_an_application_worth_beginning()
    {
        var profile = SampleProfile();
        var overview = ReadinessService.Build(
            profile, TimelineService.Build(profile, new DateOnly(2026, 9, 23)), []);

        Assert.True(overview.CanStartApplication);
        Assert.DoesNotContain(overview.NextSteps, s => s.Key == "beruf");
    }

    [Fact]
    public void An_application_waiting_for_an_answer_is_listed_as_such_and_is_not_told_to_send_itself()
    {
        // Versendet is the act; waiting is the state the applicant is in afterwards and sits in
        // longest. Until Wartend existed the two could not be told apart, so a user with several
        // employers could not see which ones they were still owed an answer by. Only a draft is
        // still to be sent, so this one carries no versand step.
        var profile = SampleProfile();
        var posting = new Posting
        {
            ProfileId = profile.Id, Company = "Agrosvit GmbH",
            JobTitle = "Buchhalterin (m/w/d)", Reference = "2026-4711",
        };
        profile.Applications =
        [
            new Application
            {
                ProfileId = profile.Id, PostingId = posting.Id,
                Status = ApplicationStatus.Wartend,
            },
        ];

        var listed = Assert.Single(ReadinessService.Build(
            profile, TimelineService.Build(profile, new DateOnly(2026, 9, 23)), [posting]).Applications);

        Assert.Equal("Wartend", listed.Status);
        Assert.Equal("Agrosvit GmbH", listed.Company);
        Assert.Equal("2026-4711", listed.Reference);
        Assert.DoesNotContain(listed.OpenSteps, s => s.Kind == "versand");
    }

    // -- what has to be there before an Anschreiben may be written ---------------------------------

    [Fact]
    public void An_empty_profile_has_no_Anschreiben_written_for_it_at_all()
    {
        // It used to write one from whatever was there, which on an empty profile is the advert:
        // no sender address, no name under the closing, and a Lebenslauf listed as an Anlage that
        // the profile cannot produce. What is missing is named field by field, as keys.
        Assert.Equal(["name", "anschrift", "kontakt", "beruf"],
            ReadinessService.LetterBlockers(new Domain.Profile()).Select(b => b.Key));
    }

    [Fact]
    public void A_profile_with_the_Briefkopf_and_one_position_blocks_nothing()
    {
        var profile = SampleProfile();
        profile.Street = "Hauptstraße 12";
        profile.PostalCode = "70173";
        profile.Email = "olena.kovalchuk@example.de";

        Assert.Empty(ReadinessService.LetterBlockers(profile));
    }

    [Fact]
    public void A_Briefkopf_that_is_complete_does_not_make_up_for_a_missing_position()
    {
        // The two halves are asked for separately: the letter has an address to come from, and
        // nothing to say about the applicant. The Übersicht may still offer the flow here — that
        // is the looser CanStartApplication question — but the letter waits.
        var profile = SampleProfile();
        profile.Street = "Hauptstraße 12";
        profile.PostalCode = "70173";
        profile.Email = "olena.kovalchuk@example.de";
        profile.Experience.Clear();

        Assert.Equal(["beruf"], ReadinessService.LetterBlockers(profile).Select(b => b.Key));
    }

    // -- what the Lebenslauf still lacks, which is a different question -----------------------------

    [Fact]
    public void What_the_Lebenslauf_lacks_is_named_and_none_of_it_holds_the_document_back()
    {
        // The three sections the document is made of, beside the three fields of its header — and
        // these are keys, because the screen writes the sentence. All three sections are also what
        // an assistant proposal can fill, which is what makes every one of them answerable in the
        // conversation the document is produced from.
        Assert.Equal(["name", "anschrift", "kontakt", "beruf", "ausbildung", "sprachen"],
            ReadinessService.CvMissing(new Domain.Profile()).Select(m => m.Key));

        // And a Lebenslauf comes out of that same empty profile all the same. Naming what is
        // missing is the opposite of withholding the document: this is the whole difference from
        // LetterBlockers, which really does hold the Anschreiben back.
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var empty = new Domain.Profile();
        var timeline = TimelineService.Build(empty, new DateOnly(2026, 9, 24));
        var cv = writer.WriteCvAsync(empty, timeline).Result;

        var pdf = new LebenslaufDocument(cv, empty, CvTemplate.Klassisch, writer.LastSource)
            .GeneratePdf();

        Assert.NotEmpty(pdf);
    }

    [Fact]
    public void A_section_the_profile_carries_is_not_named_as_missing()
    {
        // SampleProfile has two positions and one language, and no Ausbildung. The header fields it
        // does not carry are still named — they are the same three PersonMissing produces for the
        // letter, which is why the two lists cannot drift apart.
        Assert.Equal(["anschrift", "kontakt", "ausbildung"],
            ReadinessService.CvMissing(SampleProfile()).Select(m => m.Key));
    }

    [Theory]
    [InlineData("model", "einem KI-Sprachmodell")]
    [InlineData("regeln", "den Textregeln von Bewerbo")]
    public void The_Lebenslauf_says_on_the_page_which_writer_produced_it(string writer, string expected)
    {
        // Read back out of the rendered file rather than asserted on what was handed to the
        // renderer, for the reason the sender address is: the page is what leaves the app, and it
        // leaves without a screen beside it to carry the disclosure.
        var profile = SampleProfile();
        var content = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>())
            .WriteCvAsync(profile, TimelineService.Build(profile, new DateOnly(2026, 9, 24))).Result;

        var pdf = new LebenslaufDocument(content, profile, profile.Template, writer).GeneratePdf();

        using var document = PdfDocument.Open(pdf);
        var compact = Regex.Replace(
            string.Join("\n", document.GetPages().Select(p => p.Text)), @"\s+", "");

        Assert.Contains("ErstelltmitBewerbo", compact);
        Assert.Contains(Regex.Replace(expected, @"\s+", ""), compact);
    }

    // -- gaps ------------------------------------------------------------------------------------------

    [Fact]
    public void A_gap_between_two_entries_is_found()
    {
        var profile = SampleProfile();
        var view = TimelineService.Build(profile, new DateOnly(2026, 9, 22));

        var gap = Assert.Single(view.Gaps);
        Assert.Equal(new DateOnly(2023, 7, 31), gap.From);
        Assert.False(gap.Explained);
    }

    [Fact]
    public void Overlapping_entries_do_not_produce_a_phantom_gap()
    {
        // A job held while still studying overlaps the education period. Comparing entries pairwise
        // would report the space between them as a gap; merging the intervals first does not.
        var profile = new Domain.Profile
        {
            Education =
            [
                new EducationEntry
                {
                    Degree = "Diplom", Institution = "KHEU",
                    From = new DateOnly(2014, 9, 1), To = new DateOnly(2019, 6, 30),
                },
            ],
            Experience =
            [
                new ExperienceEntry
                {
                    Position = "Werkstudentin", Employer = "Agrosvit GmbH",
                    From = new DateOnly(2017, 1, 1), To = new DateOnly(2020, 12, 31),
                },
            ],
        };

        var view = TimelineService.Build(profile, new DateOnly(2026, 9, 22));

        // Nothing between the entries. The stretch AFTER the last one is a real gap and has its
        // own test below — this one is only about the overlap not inventing a hole.
        Assert.DoesNotContain(view.Gaps, g => g.From < new DateOnly(2020, 12, 31));
    }

    [Fact]
    public void The_stretch_between_the_last_entry_and_today_is_a_gap()
    {
        // A Lebenslauf that simply stops three years ago is the loudest question on the page, and
        // walking only the spaces BETWEEN entries never reaches it.
        var profile = new Domain.Profile
        {
            Experience =
            [
                new ExperienceEntry
                {
                    Position = "Pflegefachkraft", Employer = "Klinikum",
                    From = new DateOnly(2019, 3, 1), To = new DateOnly(2023, 8, 1),
                },
            ],
        };

        var view = TimelineService.Build(profile, new DateOnly(2026, 9, 22));

        var gap = Assert.Single(view.Gaps);
        Assert.Equal(new DateOnly(2023, 8, 1), gap.From);
        Assert.Equal(37, gap.Months);
        Assert.False(gap.Explained);
    }

    [Fact]
    public void The_last_year_reaches_the_end_of_the_gap_that_runs_up_to_today()
    {
        // FirstYear and LastYear are the axis the Zeitstrahl is drawn on. Taking LastYear from the
        // periods alone headed the card "2019 - 2023" while the gap card underneath said the hole
        // reaches 2026-09, and the hatched block itself fell outside the chart.
        var profile = new Domain.Profile
        {
            Experience =
            [
                new ExperienceEntry
                {
                    Position = "Pflegefachkraft", Employer = "Klinikum",
                    From = new DateOnly(2019, 3, 1), To = new DateOnly(2023, 8, 1),
                },
            ],
        };

        var view = TimelineService.Build(profile, new DateOnly(2026, 9, 22));

        Assert.Equal(2019, view.FirstYear);
        Assert.Equal(2026, view.LastYear);
    }

    [Fact]
    public void An_ongoing_job_leaves_no_gap_at_the_end()
    {
        var profile = new Domain.Profile
        {
            Experience =
            [
                new ExperienceEntry
                {
                    Position = "Pflegefachkraft", Employer = "Klinikum",
                    From = new DateOnly(2019, 3, 1), To = null,
                },
            ],
        };

        Assert.Empty(TimelineService.Build(profile, new DateOnly(2026, 9, 22)).Gaps);
    }

    [Theory]
    [InlineData("Переїзд до Німеччини + курс німецької B2", "Umzug nach Deutschland, Sprachkurs Deutsch B2")]
    [InlineData("relocation to Germany", "Umzug nach Deutschland")]
    [InlineData("Анеркеннунг", "")]
    public void A_gap_reason_becomes_the_German_wording_a_Lebenslauf_uses(string reason, string expected)
    {
        Assert.Equal(expected, GapWording.Suggest(reason));
    }

    [Theory]
    // Every one of these is the wording that locale's OWN profile_gap_explainer tells the user to
    // write. Three of the four were not on the trigger list: the Ukrainian "language courses" and
    // the Russian plural both fell through to the generic Weiterbildung - taking the CEFR level
    // with them - and the English "a move" matched nothing at all, because only "moved"/"moving"
    // were listed. A product that asks for a phrase has to be able to read it back.
    [InlineData("переїзд, мовні курси, визнання диплома")]
    [InlineData("переезд, языковые курсы, признание диплома")]
    [InlineData("a move, a language course, a recognition procedure")]
    [InlineData("Umzug, Sprachkurs, Anerkennungsverfahren")]
    public void The_phrase_each_explainer_suggests_is_one_the_wording_table_reads(string suggested)
    {
        var german = GapWording.Suggest(suggested);

        Assert.Contains("Umzug nach Deutschland", german);
        Assert.Contains("Sprachkurs Deutsch", german);
        Assert.Contains("Anerkennungsverfahren", german);
        // The specific wording is what a German reader learns something from, so the catch-all
        // must not be left standing next to it.
        Assert.DoesNotContain("Weiterbildung", german);
    }

    [Theory]
    [InlineData("мовні курси B2", "Sprachkurs Deutsch B2")]
    [InlineData("мовний курс B1", "Sprachkurs Deutsch B1")]
    [InlineData("курсы немецкого B2", "Sprachkurs Deutsch B2")]
    [InlineData("a move to Germany", "Umzug nach Deutschland")]
    public void The_level_survives_the_wording_the_user_actually_types(string reason, string expected)
    {
        Assert.Equal(expected, GapWording.Suggest(reason));
    }

    [Fact]
    public void An_unrecognised_reason_produces_no_wording_rather_than_an_invented_one()
    {
        Assert.Equal("", GapWording.Suggest("qwertz"));
    }

    // -- duties written as results ----------------------------------------------------------------

    [Theory]
    [InlineData("Verantwortlich für die Durchführung von Schulungen", "Schulungen durchgeführt")]
    [InlineData("Zuständig für die Betreuung von 40 Bestandskunden", "40 Bestandskunden betreut")]
    [InlineData("Pflege der Datenbank", "Datenbank gepflegt")]
    [InlineData("Kundenbetreuung im Innendienst", "Kunden im Innendienst betreut")]
    [InlineData("Qualitätskontrolle der Lieferungen", "Qualität der Lieferungen kontrolliert")]
    [InlineData("- Erstellung der Monatsberichte", "Monatsberichte erstellt")]
    [InlineData("Wartung des Fahrzeugs", "Fahrzeug gewartet")]
    [InlineData("Aufgaben: Montage der Baugruppen", "Baugruppen montiert")]
    // The user's own framing said what they did with it, so the active form is theirs even where
    // the head noun is not one this table knows.
    [InlineData("Verantwortlich für den Empfang", "Empfang verantwortet")]
    // A label in front of the claim does not hide it.
    [InlineData("Aufgaben: Verantwortlich für den Empfang", "Empfang verantwortet")]
    public void A_duty_is_rewritten_as_the_result_a_German_reader_weighs(string duty, string expected)
    {
        Assert.Equal(expected, DutyOutcomes.Rewrite(duty));
    }

    [Theory]
    // Nothing to move: no framing and no head noun the table knows.
    [InlineData("Schweißen nach WIG-Verfahren")]
    // A noun that merely ends in the letters of one in the table. "Überprüfung" is not a "Prüfung"
    // of something called "Über", and "Anleitung" is not a "Leitung" - a rewrite here would read
    // as nonsense the user has to catch.
    [InlineData("Überprüfung der Maschinen")]
    [InlineData("Anleitung der Auszubildenden")]
    // A dative plural whose nominative is a different word: "von Monatsberichten" would have to
    // become "Monatsberichte", and no letter of the word says whether it does - "von Kunden" stays
    // "Kunden". The rewrite is offered for the second and not for the first.
    [InlineData("Erstellung von Monatsberichten")]
    // Not German. This writer does not translate, for the same reason the rule-based letter writer
    // does not - see ScriptCheck.
    [InlineData("Обслуживание клиентов")]
    // A label with nothing behind it.
    [InlineData("Tätigkeiten:")]
    // A label is a heading somebody put in front of their own list, NOT a claim of responsibility.
    // Reading it as one produced "Stapler fahren verantwortet" - neither German, nor anything the
    // user said, and it reached the stored duties and the Lebenslauf from there.
    [InlineData("Tätigkeiten: Stapler fahren")]
    [InlineData("Aufgaben: Empfang und Telefon")]
    public void A_duty_the_move_does_not_fit_gets_no_rewrite_rather_than_a_guess(string duty)
    {
        Assert.Equal("", DutyOutcomes.Rewrite(duty));
    }

    [Fact]
    public void A_line_with_no_rewrite_keeps_its_place_beside_the_ones_that_have_one()
    {
        var lines = DutyOutcomes.RewriteAll(
            ["Pflege der Datenbank", "Schweißen nach WIG-Verfahren", "Wartung der Anlagen"]);

        Assert.Equal(3, lines.Count);
        Assert.Equal("Datenbank gepflegt", lines[0].Outcome);
        Assert.Equal("", lines[1].Outcome);
        Assert.Equal("Schweißen nach WIG-Verfahren", lines[1].Original);
        Assert.Equal("Anlagen gewartet", lines[2].Outcome);
    }

    [Fact]
    public void The_named_gap_appears_in_the_Lebenslauf_as_German_wording()
    {
        var profile = SampleProfile();
        profile.Gaps.Add(new GapExplanation
        {
            From = new DateOnly(2023, 7, 31), To = new DateOnly(2023, 9, 1),
            Reason = "Переїзд до Німеччини",
            GermanWording = "Umzug nach Deutschland, Sprachkurs Deutsch B2",
        });

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        var items = cv.Sections.SelectMany(s => s.Items).ToList();
        var gapItem = Assert.Single(items, i => i.IsGap);
        Assert.Equal("Umzug nach Deutschland, Sprachkurs Deutsch B2", gapItem.Title);
    }

    [Fact]
    public void A_gap_that_ends_where_a_study_period_begins_is_named_in_the_Ausbildung_section()
    {
        // Gaps are found across BOTH lanes, but only the Berufserfahrung loop placed them, so the
        // year before a course began was named on the Profil screen, counted as explained, and then
        // missing from the document.
        var profile = new Domain.Profile
        {
            Experience =
            [
                new ExperienceEntry
                {
                    Position = "Buchhalterin", Employer = "Agro Invest",
                    From = new DateOnly(2016, 1, 1), To = new DateOnly(2018, 6, 30),
                },
            ],
            Education =
            [
                new EducationEntry
                {
                    Degree = "Magister Betriebswirtschaft", Institution = "KHEU",
                    From = new DateOnly(2019, 9, 1), To = new DateOnly(2021, 6, 30),
                },
            ],
            Gaps =
            [
                new GapExplanation
                {
                    From = new DateOnly(2018, 6, 30), To = new DateOnly(2019, 9, 1),
                    Reason = "переїзд до Німеччини", GermanWording = "Umzug nach Deutschland",
                },
            ],
        };

        var timeline = TimelineService.Build(profile, new DateOnly(2021, 6, 30));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        var ausbildung = Assert.Single(cv.Sections, s => s.Title == "Ausbildung");
        var gapItem = Assert.Single(ausbildung.Items, i => i.IsGap);
        Assert.Equal("Umzug nach Deutschland", gapItem.Title);
        // Directly under the entry it runs into, because the list is newest first.
        Assert.Equal(1, ausbildung.Items.ToList().FindIndex(i => i.IsGap));
    }

    [Fact]
    public void The_gap_from_the_last_entry_up_to_today_is_named_at_the_top_of_the_section()
    {
        // It runs into no entry at all, so neither loop places it — and it is the loudest question
        // on the page, a Lebenslauf that simply stops. It used to reach the document nowhere.
        var profile = new Domain.Profile
        {
            Experience =
            [
                new ExperienceEntry
                {
                    Position = "Buchhalterin", Employer = "Agro Invest",
                    From = new DateOnly(2019, 4, 1), To = new DateOnly(2023, 5, 31),
                },
            ],
            Gaps =
            [
                new GapExplanation
                {
                    From = new DateOnly(2023, 5, 31), To = new DateOnly(2026, 9, 23),
                    Reason = "визнання диплома", GermanWording = "Anerkennungsverfahren",
                },
            ],
        };

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 23));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        var work = Assert.Single(cv.Sections, s => s.Title == "Berufserfahrung");
        Assert.True(work.Items[0].IsGap);
        Assert.Equal("Anerkennungsverfahren", work.Items[0].Title);
    }

    [Fact]
    public void Every_explained_gap_with_a_German_name_reaches_the_document_exactly_once()
    {
        // The invariant behind the two tests above: a gap the Profil screen counts as answered and
        // shows a German sentence for has to appear on the page, wherever in the chronology it
        // sits. All three positions at once — into a study period, into a job, and into nothing.
        var profile = new Domain.Profile
        {
            Experience =
            [
                new ExperienceEntry
                {
                    Position = "Buchhalterin", Employer = "Agro Invest",
                    From = new DateOnly(2016, 1, 1), To = new DateOnly(2018, 6, 30),
                },
                new ExperienceEntry
                {
                    Position = "Sachbearbeiterin", Employer = "Nordhaus GmbH",
                    From = new DateOnly(2022, 1, 1), To = new DateOnly(2023, 5, 31),
                },
            ],
            Education =
            [
                new EducationEntry
                {
                    Degree = "Magister Betriebswirtschaft", Institution = "KHEU",
                    From = new DateOnly(2019, 9, 1), To = new DateOnly(2021, 6, 30),
                },
            ],
            Gaps =
            [
                new GapExplanation
                {
                    From = new DateOnly(2018, 6, 30), To = new DateOnly(2019, 9, 1),
                    Reason = "переїзд", GermanWording = "Umzug nach Deutschland",
                },
                new GapExplanation
                {
                    From = new DateOnly(2021, 6, 30), To = new DateOnly(2022, 1, 1),
                    Reason = "мовні курси B2", GermanWording = "Sprachkurs Deutsch B2",
                },
                new GapExplanation
                {
                    From = new DateOnly(2023, 5, 31), To = new DateOnly(2026, 9, 23),
                    Reason = "визнання диплома", GermanWording = "Anerkennungsverfahren",
                },
            ],
        };

        var today = new DateOnly(2026, 9, 23);
        var timeline = TimelineService.Build(profile, today);
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        var written = cv.Sections.SelectMany(s => s.Items).Where(i => i.IsGap)
            .Select(i => i.Title).OrderBy(t => t).ToArray();

        Assert.Equal(3, timeline.Gaps.Count(g => g.Explained));
        Assert.Equal(
            new[] { "Anerkennungsverfahren", "Sprachkurs Deutsch B2", "Umzug nach Deutschland" },
            written);
    }

    [Fact]
    public void A_gap_with_no_German_wording_stays_out_of_the_Lebenslauf()
    {
        // The reason is the user's and is kept; what has no German name simply cannot be written.
        // It used to be written anyway, as a date range with an empty title beside it - a blank
        // line exactly where the reader was looking for the answer.
        var profile = SampleProfile();
        profile.Gaps.Add(new GapExplanation
        {
            From = new DateOnly(2023, 7, 31), To = new DateOnly(2023, 9, 1),
            Reason = "qwertz", GermanWording = "",
        });

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        Assert.DoesNotContain(cv.Sections.SelectMany(s => s.Items), i => i.IsGap);
    }

    // -- recognition ----------------------------------------------------------------------------------

    [Fact]
    public void An_unconfirmed_equivalence_never_reaches_the_Lebenslauf()
    {
        var profile = SampleProfile();
        profile.Education.Add(new EducationEntry
        {
            Degree = "Diplom", Institution = "KHEU", Country = "UA",
            From = new DateOnly(2014, 9, 1), To = new DateOnly(2019, 6, 30),
            AnabinAssessment = "H+", GermanEquivalent = "Bachelorabschluss",
            EquivalenceConfirmed = false,
        });

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        var bullets = cv.Sections.SelectMany(s => s.Items).SelectMany(i => i.Bullets);
        Assert.DoesNotContain(bullets, b => b.Contains("gleichwertig"));
    }

    [Fact]
    public void A_confirmed_equivalence_is_stated_with_its_anabin_rating()
    {
        var profile = SampleProfile();
        profile.Education.Add(new EducationEntry
        {
            Degree = "Diplom", Institution = "KHEU", Country = "UA",
            From = new DateOnly(2014, 9, 1), To = new DateOnly(2019, 6, 30),
            AnabinAssessment = "H+", GermanEquivalent = "Bachelorabschluss",
            EquivalenceConfirmed = true,
        });

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        var bullets = cv.Sections.SelectMany(s => s.Items).SelectMany(i => i.Bullets).ToList();
        Assert.Contains(bullets, b => b.Contains("gleichwertig mit: Bachelorabschluss"));
        Assert.Contains(bullets, b => b.Contains("H+"));
    }

    [Fact]
    public void The_anabin_rating_is_named_as_the_institutions_listing_and_not_the_degrees()
    {
        var profile = SampleProfile();
        profile.Education.Add(new EducationEntry
        {
            Degree = "Diplom", Institution = "KHEU", Country = "UA",
            From = new DateOnly(2014, 9, 1), To = new DateOnly(2019, 6, 30),
            AnabinAssessment = "H+", GermanEquivalent = "Bachelorabschluss",
            EquivalenceConfirmed = true,
        });

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        // anabin lists institutions and qualifications separately, and H+ is the status of the
        // Hochschule. Written as "gleichwertig mit: Bachelorabschluss (anabin: H+)" it read as a
        // mark awarded to the degree — a claim the entry does not make and the user never confirmed.
        var bullets = cv.Sections.SelectMany(s => s.Items).SelectMany(i => i.Bullets).ToList();
        Assert.Contains(bullets, b => b.StartsWith("Hochschule in anabin:") && b.Contains("H+"));
        Assert.DoesNotContain(bullets, b => b.Contains("gleichwertig") && b.Contains("H+"));
    }

    [Fact]
    public void An_assessment_that_is_still_open_is_named_beside_the_degree()
    {
        var profile = SampleProfile();
        profile.Education.Add(new EducationEntry
        {
            Degree = "Diplom", Institution = "KHEU", Country = "UA",
            From = new DateOnly(2014, 9, 1), To = new DateOnly(2019, 6, 30),
            ZabAssessmentPending = true,
        });

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        // Beside the degree, not somewhere else on the page: the reader weighs the qualification
        // where they read it.
        var item = cv.Sections.SelectMany(s => s.Items).Single(i => i.Title == "Diplom");
        Assert.Contains(item.Bullets, b => b.Contains("Zeugnisbewertung") && b.Contains("steht noch aus"));
    }

    [Fact]
    public void An_open_assessment_is_not_written_as_a_settled_equivalence()
    {
        var profile = SampleProfile();
        profile.Education.Add(new EducationEntry
        {
            Degree = "Diplom", Institution = "KHEU", Country = "UA",
            From = new DateOnly(2014, 9, 1), To = new DateOnly(2019, 6, 30),
            AnabinAssessment = "H+", GermanEquivalent = "Bachelorabschluss",
            EquivalenceConfirmed = false, ZabAssessmentPending = true,
        });

        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var cv = writer.WriteCvAsync(profile, timeline).Result;

        // The outstanding assessment is the one thing that may be said while the user has not
        // confirmed anything. It must not carry the equivalence in with it.
        var bullets = cv.Sections.SelectMany(s => s.Items).SelectMany(i => i.Bullets).ToList();
        Assert.Contains(bullets, b => b.Contains("Zeugnisbewertung"));
        Assert.DoesNotContain(bullets, b => b.Contains("gleichwertig"));
    }

    [Fact]
    public void A_degree_whose_assessment_is_running_is_not_told_to_go_and_look_it_up()
    {
        var profile = SampleProfile();
        profile.Education.Add(new EducationEntry
        {
            Degree = "Diplom", Institution = "KHEU", Country = "UA",
            From = new DateOnly(2014, 9, 1), To = new DateOnly(2019, 6, 30),
            ZabAssessmentPending = true,
        });

        var steps = ReadinessService.Build(
            profile, TimelineService.Build(profile, new DateOnly(2026, 9, 23)), []).NextSteps;

        // Still outstanding — an assessment nobody is waiting for would read as settled. But the
        // step the user cannot act on is a different step from the one they can: they have applied
        // and are waiting, and "anabin-Eintrag prüfen und zitieren" says the app did not notice.
        var step = Assert.Single(steps, s => s.Kind.StartsWith("anerkennung"));
        Assert.Equal("anerkennung_offen", step.Kind);
        Assert.Contains("ZAB", step.Detail);
    }

    // -- the output the card asks for --------------------------------------------------------------------

    [Fact]
    public void The_finished_application_is_one_PDF_whose_text_extracts_again()
    {
        var profile = SampleProfile();
        var posting = new Posting
        {
            JobTitle = "Bilanzbuchhalter (m/w/d)", Reference = "SBT-2026-0417",
            ContactName = "Frau Dr. Annika Weber", Company = "Schwarzwald Technik GmbH",
            CompanyAddress = "Industriestraße 8, 70563 Stuttgart",
        };
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var cv = writer.WriteCvAsync(profile, timeline).Result;
        var match = RequirementMatcher.Match(profile, []);
        var letter = writer.WriteLetterAsync(profile, posting, match, LetterTone.Sachlich).Result;

        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv, writer.LastSource, [],
            new DateOnly(2026, 9, 22));

        var result = AtsTextCheck.Run(pdf, profile);
        Assert.True(result.Passed,
            "The rendered PDF did not read back: " +
            string.Join("; ", result.Findings.Where(f => f.Verdict != "ok").Select(f => f.Detail)));
        Assert.All(result.Findings, f => Assert.Equal("ok", f.Verdict));
    }

    [Fact]
    public void The_letter_carries_the_sender_address_and_the_name_under_the_closing()
    {
        // The two things a letter that is sent must have and that an empty profile cannot give it.
        // Read back out of the rendered Anschreiben rather than asserted on the content handed to
        // the renderer: it is the page that is posted, not the LetterContent.
        var profile = SampleProfile();
        profile.Street = "Hauptstraße 12";
        profile.PostalCode = "70173";
        profile.Email = "olena.kovalchuk@example.de";

        var posting = new Posting
        {
            JobTitle = "Bilanzbuchhalter (m/w/d)", Company = "Schwarzwald Technik GmbH",
            CompanyAddress = "Industriestraße 8, 70563 Stuttgart",
        };
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var cv = writer.WriteCvAsync(profile, timeline).Result;
        var match = RequirementMatcher.Match(profile, []);
        var letter = writer.WriteLetterAsync(profile, posting, match, LetterTone.Sachlich).Result;

        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv, writer.LastSource, [],
            new DateOnly(2026, 9, 22), ApplicationParts.Anschreiben);

        using var document = PdfDocument.Open(pdf);
        // PdfPig hands back the glyph run without the spaces the eye supplies, the way
        // AtsTextCheck reads it.
        var compact = Regex.Replace(
            string.Join("\n", document.GetPages().Select(p => p.Text)), @"\s+", "");

        Assert.Contains("Hauptstraße12", compact);
        Assert.Contains("70173Stuttgart", compact);
        // Twice: once in the sender line over the Anschriftenfeld, once typed under the signature
        // rule. One occurrence would be satisfied by the Briefkopf alone.
        Assert.Equal(2, Regex.Matches(compact, "OlenaKovalchuk").Count);
    }

    [Fact]
    public void An_empty_profile_is_not_reported_as_a_fault_of_the_document()
    {
        // Nothing is wrong with the file here — there is simply nothing to look for in it. A
        // "fehler" on these three is the app blaming its own output for the missing input.
        var profile = new Domain.Profile { FirstName = "", LastName = "" };
        var posting = new Posting { JobTitle = "Bilanzbuchhalter (m/w/d)", Company = "Schwarzwald Technik GmbH" };
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var cv = writer.WriteCvAsync(profile, timeline).Result;
        var match = RequirementMatcher.Match(profile, []);
        var letter = writer.WriteLetterAsync(profile, posting, match, LetterTone.Sachlich).Result;

        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv, writer.LastSource, [],
            new DateOnly(2026, 9, 22));

        var result = AtsTextCheck.Run(pdf, profile);

        Assert.DoesNotContain(result.Findings, f => f.Verdict == "fehler");
        Assert.True(result.Passed);

        // Each of the three says WHICH field is empty, and leads to the screen it is filled in on.
        foreach (var key in new[] { "name", "arbeitgeber", "zeitraeume" })
        {
            var finding = result.Findings.Single(f => f.Key == key);
            Assert.Equal("ungeprueft", finding.Verdict);
            Assert.Equal($"{key}_keine", finding.DetailKind);
            Assert.Equal("profil", finding.Target);
        }

        // With nothing entered at all, both halves of the name are named.
        Assert.Equal(["vorname", "nachname"], result.Findings.Single(f => f.Key == "name").DetailArgs);
    }

    [Fact]
    public void A_half_entered_name_names_the_half_that_is_missing()
    {
        // The export is already called Bewerbung_Olena_… here, so "Kein Name im Profil" is not
        // merely unhelpful, it is false — and it still leaves the user looking for what to fill in.
        var profile = new Domain.Profile { FirstName = "Olena", LastName = "" };
        var posting = new Posting { JobTitle = "Bilanzbuchhalter (m/w/d)", Company = "Schwarzwald Technik GmbH" };
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var timeline = TimelineService.Build(profile, new DateOnly(2026, 9, 22));
        var cv = writer.WriteCvAsync(profile, timeline).Result;
        var match = RequirementMatcher.Match(profile, []);
        var letter = writer.WriteLetterAsync(profile, posting, match, LetterTone.Sachlich).Result;

        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv, writer.LastSource, [],
            new DateOnly(2026, 9, 22));

        var finding = AtsTextCheck.Run(pdf, profile).Findings.Single(f => f.Key == "name");

        Assert.Equal("ungeprueft", finding.Verdict);
        Assert.Equal("name_keine", finding.DetailKind);
        Assert.Equal(["nachname"], finding.DetailArgs);
        Assert.Equal("profil", finding.Target);
    }

    [Theory]
    [InlineData("Olena", "Kovalchuk", "Bilanzbuchhalter (m/w/d)", "Bewerbung_Olena_Kovalchuk_Bilanzbuchhalter.pdf")]
    [InlineData("Jörg", "Weiß", "Sachbearbeiter", "Bewerbung_Joerg_Weiss_Sachbearbeiter.pdf")]
    public void The_export_is_named_as_the_card_requires(string first, string last, string title, string expected)
    {
        var profile = new Domain.Profile { FirstName = first, LastName = last };
        var posting = new Posting { JobTitle = title };
        Assert.Equal(expected, MergedApplicationDocument.FileName(profile, posting));
    }

    [Fact]
    public void A_Cyrillic_name_is_transliterated_rather_than_dropped_from_the_file_name()
    {
        // The audience is people who moved to Germany, so this is the normal case.
        var profile = new Domain.Profile
        {
            FirstName = "Олена",
            LastName = "Ковальчук",
        };
        var name = MergedApplicationDocument.FileName(profile, new Posting { JobTitle = "Buchhalter" });

        Assert.Equal("Bewerbung_Olena_Kowaltschuk_Buchhalter.pdf", name);
    }

    // -- no untranslated text inside a German sentence ------------------------------------------------------

    [Fact]
    public void The_rule_based_letter_never_splices_the_users_own_script_into_German_prose()
    {
        var profile = SampleProfile();
        profile.Experience[1].Duties =
            "Ведення фінансової бухгалтерії в DATEV";

        var posting = new Posting { JobTitle = "Buchhalter", ContactName = "Frau Weber" };
        var writer = new ApplicationWriter(new NoModel(), new NullLogger<ApplicationWriter>());
        var match = RequirementMatcher.Match(profile, []);
        var letter = writer.WriteLetterAsync(profile, posting, match, LetterTone.Sachlich).Result;

        foreach (var paragraph in letter.Paragraphs)
        {
            Assert.True(ScriptCheck.IsLatin(paragraph),
                $"A German paragraph contains untranslated text: {paragraph}");
        }
    }

    [Theory]
    [InlineData("Jahresabschlüsse nach HGB", true)]
    [InlineData("Ведення бухгалтерії", false)]
    [InlineData("DATEV 14", true)]
    public void Latin_text_is_told_apart_from_the_users_own_script(string text, bool expected)
    {
        Assert.Equal(expected, ScriptCheck.IsLatin(text));
    }
}

/// <summary>A model that is not configured — the path every test above exercises.</summary>
internal class NoModel : ILanguageModel
{
    public bool IsConfigured => false;

    public Task<T?> CompleteAsync<T>(string system, string user, string schemaName, string schemaJson,
        CancellationToken ct = default) => Task.FromResult<T?>(default);
}

internal class NullLogger<T> : ILogger<T>
{
    public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
    public bool IsEnabled(LogLevel logLevel) => false;
    public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception,
        Func<TState, Exception?, string> formatter) { }
}
