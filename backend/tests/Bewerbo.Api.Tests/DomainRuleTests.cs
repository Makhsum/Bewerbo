using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Rendering;
using Bewerbo.Api.Services;
using Bewerbo.Api.Text;
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
    [InlineData("Klinikum München Süd sucht eine Pflegefachkraft (m/w/d).", "Klinikum München Süd")]
    [InlineData("Das Universitätsklinikum Heidelberg sucht Verstärkung.", "Universitätsklinikum Heidelberg")]
    [InlineData("Die Stadt Augsburg sucht eine Sachbearbeiterin.", "Stadt Augsburg")]
    [InlineData("Seniorenzentrum Nord sucht Pflegekräfte.", "Seniorenzentrum Nord")]
    [InlineData("Die Schwarzwald Technik GmbH sucht Verstärkung.", "Schwarzwald Technik GmbH")]
    public void An_employer_without_a_legal_form_in_its_name_is_still_found(string line, string expected)
    {
        // Hospitals, care homes, universities and town halls carry no GmbH — and they are exactly
        // the employers this product's users apply to. The company is the addressee of the
        // Anschriftenfeld, so not finding it leaves the letter addressed to nobody.
        var extract = PostingParser.Parse(line);

        Assert.Equal(expected, extract.Fields.Single(f => f.Key == "company").Value);
    }

    [Theory]
    [InlineData("Klinikum München sucht eine Pflegefachkraft (m/w/d).", "Pflegefachkraft (m/w/d)")]
    [InlineData("Wir suchen zum 01.01.2027 eine Leitende Buchhalterin (m/w/d).", "Leitende Buchhalterin (m/w/d)")]
    public void The_job_title_does_not_swallow_the_sentence_in_front_of_it(string line, string expected)
    {
        var extract = PostingParser.Parse(line);

        Assert.Equal(expected, extract.Fields.Single(f => f.Key == "title").Value);
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

    [Fact]
    public void An_unrecognised_reason_produces_no_wording_rather_than_an_invented_one()
    {
        Assert.Equal("", GapWording.Suggest("qwertz"));
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
        Assert.Contains(bullets, b => b.Contains("Bachelorabschluss") && b.Contains("H+"));
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

        var pdf = MergedApplicationDocument.Render(profile, posting, letter, cv, [],
            new DateOnly(2026, 9, 22));

        var result = AtsTextCheck.Run(pdf, profile);
        Assert.True(result.Passed,
            "The rendered PDF did not read back: " +
            string.Join("; ", result.Findings.Where(f => !f.Found).Select(f => f.Detail)));
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
