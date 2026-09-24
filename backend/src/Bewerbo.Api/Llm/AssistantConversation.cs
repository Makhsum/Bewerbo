using Bewerbo.Api.Domain;

namespace Bewerbo.Api.Llm;

/// <summary>One thing that has been said so far. <paramref name="FromUser"/> false is the assistant's
/// own earlier turn — the conversation is not stored anywhere, so the client sends back what it has
/// on screen and this is the whole of the memory a turn gets.</summary>
public record AssistantMessage(bool FromUser, string Text);

public interface IAssistantConversation
{
    /// <summary>
    /// Answers the conversation so far, or null when the model had nothing usable to say.
    ///
    /// Null and not a fallback, unlike <see cref="IApplicationWriter"/>: there is no rule-based
    /// assistant and there must not be one. See <see cref="AssistantConversation"/>.
    /// </summary>
    Task<AssistantReply?> TurnAsync(Profile profile, IReadOnlyList<AssistantMessage> said,
        string uiLanguage, CancellationToken ct = default);
}

/// <summary>
/// The conversation the first minutes of Bewerbo are.
///
/// It is the ONE thing in this API with no deterministic second implementation behind it, and that
/// is the decision rather than an omission: a chat backed by regular expressions would be the exact
/// dishonesty the rest of the product avoids. Where no key is configured there is no assistant, the
/// route says so, and the screen points at the form — which is still the complete way in.
///
/// The reply travels as prose, which is the one exception to the rule that the server never sends a
/// finished sentence (see <see cref="Contracts.NextStepDto"/>). That rule exists because the server
/// does not know the interface language; here the CLIENT names it, and prose written for this user
/// is the whole of what the feature produces. Nothing about the rule changes for anything the
/// server itself composes.
///
/// Nothing is stored and nothing is WRITTEN into the profile here. The turn does propose entries —
/// see <see cref="AssistantProposal"/> — but a proposal is a thing to read, and the write goes
/// through the profile's own routes when the user has accepted it, as every other write does.
/// </summary>
public class AssistantConversation(ILanguageModel model, ILogger<AssistantConversation> log)
    : IAssistantConversation
{
    /// <summary>
    /// The only German the reply may contain — the same closed list the interface keeps, in
    /// android/.../ui/GermanTerms.kt.
    ///
    /// Nothing enforces the pair. GermanTermsTest scans strings.xml and a model's prose never
    /// passes through it, so this is a guard by instruction: without it the reply says "dein
    /// Deutsch-Level" or "ein Sprachkurs" in the middle of a Russian sentence, which is precisely
    /// the German the rule takes out of the interface.
    /// </summary>
    private static readonly string[] KeptGermanTerms =
    [
        "Bewerbungsmappe", "Lebenslauf", "Anschreiben", "Motivationsschreiben", "Anlagenverzeichnis",
        "Zeugnis", "Nachweis", "Referenznummer", "anabin", "ZAB", "DIN 5008", "AGG", "AGB",
        "Impressum", "DSGVO",
    ];

    /// <summary>
    /// The profile sections a proposal can land in, spelled as
    /// <c>PATCH /api/profile/{id}/sections/…</c> spells them. Named here because three things have
    /// to agree on the word — the prompt, the enum in <see cref="OutputSchemas.Assistant"/> and
    /// the client's own switch — and only two of them are C#.
    /// </summary>
    private const string ExperienceSection = "berufserfahrung";

    private const string EducationSection = "ausbildung";

    private const string LanguagesSection = "sprachen";

    public async Task<AssistantReply?> TurnAsync(Profile profile, IReadOnlyList<AssistantMessage> said,
        string uiLanguage, CancellationToken ct = default)
    {
        if (!model.IsConfigured) return null;

        var result = await model.CompleteAsync<AssistantReply>(
            System(uiLanguage), Prompt(profile, said),
            OutputSchemas.AssistantSchemaName, OutputSchemas.Assistant, ct);

        // What comes back may propose an entry the profile cannot take. That is filtered here
        // rather than drawn: see Usable.
        if (result is { Reply.Length: > 0 }) return result with { Proposals = Usable(result.Proposals) };

        // An answer with nothing in it is not an answer. The route turns this into the same refusal
        // an unconfigured installation gets, because from where the user stands it is the same
        // thing: the assistant did not answer.
        log.LogInformation("Model returned no usable assistant turn.");
        return null;
    }

    /// <summary>
    /// The proposals that can actually become a profile entry, and no others.
    ///
    /// An accepted proposal is saved through the profile's own section routes, and those need a
    /// section they know, a name, and — for a station or a qualification — a date they can parse.
    /// A proposal missing one of the three is a card offering a save that then fails, so it is
    /// dropped before the screen ever draws it. The system prompt asks the model for the same
    /// thing; this is what holds when it answers otherwise.
    ///
    /// The wording is never touched. Title and detail are what the user will read and then accept,
    /// and a server that tidied them would be putting a sentence in the profile that nobody saw.
    /// </summary>
    private static List<AssistantProposal> Usable(IEnumerable<AssistantProposal> proposals) =>
        proposals
            .Where(p => p.Kind is ExperienceSection or EducationSection or LanguagesSection)
            .Where(p => !string.IsNullOrWhiteSpace(p.Title) && !string.IsNullOrWhiteSpace(p.Source))
            .Select(p => p with { From = Day(p.From), To = Day(p.To) })
            .Where(p => p.Kind == LanguagesSection || p.From.Length > 0)
            .ToList();

    /// <summary>
    /// A date as the section routes take it — <c>yyyy-MM-dd</c> — or empty where what came back is
    /// not one. A model asked for "JJJJ-MM" answers "2019-04" and sometimes "2019"; both name a
    /// start the user really said, and the profile's own form widens them the same way. Anything
    /// else is a guess nobody can save, and empty is what says so.
    /// </summary>
    private static string Day(string? value)
    {
        var text = (value ?? "").Trim();
        foreach (var candidate in new[] { text, text + "-01", text + "-01-01" })
        {
            if (DateOnly.TryParseExact(candidate, "yyyy-MM-dd", out var date))
            {
                return date.ToString("yyyy-MM-dd");
            }
        }
        return "";
    }

    /// <summary>
    /// What the assistant is, in German as every other prompt in this project is — the language the
    /// prompt is written in has nothing to do with the language of the answer.
    /// </summary>
    private static string System(string uiLanguage) =>
        "Du bist der Assistent von Bewerbo und hilfst jemandem, der nach Deutschland gekommen ist, " +
        "aus dem eigenen Lebensweg eine deutsche Bewerbungsmappe zu machen. " +
        "ANTWORTE IN DER SPRACHE, IN DER DIE PERSON GESCHRIEBEN HAT — nicht auf Deutsch, außer " +
        "sie hat auf Deutsch geschrieben. Ist die Sprache nicht zu erkennen, weil der Text aus " +
        $"einem abfotografierten Dokument kommt, antworte in der Sprache mit dem Tag »{uiLanguage}«. " +
        "»reply« benennt zuerst, WAS DU VERSTANDEN HAST — Stationen, Jahre, Abschlüsse, Sprachen, " +
        "so wie die Person sie genannt hat. Behaupte nichts, was nicht gesagt wurde, und rate keine " +
        "Jahreszahl. »missing« nennt kurz, was für einen Lebenslauf noch fehlt, ein Punkt pro Zeile. " +
        "»proposals« ist dasselbe Verstandene, aber so, wie es im Profil stünde: ein Eintrag je " +
        "Station, Abschluss oder Sprache, und nichts sonst. »kind« ist der Abschnitt des Profils — " +
        $"{ExperienceSection}: »title« = Position, »detail« = Arbeitgeber; " +
        $"{EducationSection}: »title« = Abschluss, »detail« = Institution; " +
        $"{LanguagesSection}: »title« = Sprache, »detail« = Niveau. " +
        "»title« und »detail« stehen AUF DEUTSCH und genau so, wie sie später im Lebenslauf stehen " +
        "sollen — ohne Klammern, ohne Erklärung, ohne die Sprache der Person daneben. " +
        "»source« zitiert WÖRTLICH die Worte der Person, aus denen du den Eintrag gelesen hast, in " +
        "deren Sprache und unverändert. Was kein Eintrag ist, gehört nicht in »proposals«. " +
        $"»from« und »to« im Format JJJJ-MM; bei {LanguagesSection} bleiben beide leer, bei etwas " +
        "Andauerndem bleibt »to« leer. Eine Station oder ein Abschluss OHNE genanntes Anfangsjahr " +
        "gehört nach »missing« und nicht nach »proposals«. " +
        "Höchstens 120 Wörter in »reply«. Keine Emoji. " +
        "In »reply« und »missing« sind dies die einzigen deutschen Wörter, die stehen dürfen: " +
        string.Join(", ", KeptGermanTerms) +
        ". Jedes andere deutsche Wort übersetzt du dort in die Sprache der Antwort. Für »title« " +
        "und »detail« gilt das NICHT: die sind der deutsche Text selbst.";

    /// <summary>
    /// What has been said, under what is already on file — built as
    /// <see cref="ApplicationWriter"/> builds its prompts, line by line.
    ///
    /// The profile goes in so the assistant does not ask for what the user has already typed into
    /// the form: the two ways in are one profile, and an assistant that asks for the city again is
    /// an assistant that has not read it.
    /// </summary>
    private static string Prompt(Profile profile, IReadOnlyList<AssistantMessage> said)
    {
        var lines = new List<string>
        {
            "Was im Profil schon steht:",
            $"- Person: {Filled(profile.FirstName, profile.LastName)}, " +
            $"Ort: {Filled(profile.PostalCode, profile.City)}",
            $"- Eingabesprache des Profils: {profile.InputLanguage}",
        };
        lines.Add(profile.Experience.Count == 0
            ? "- Berufserfahrung: nichts eingetragen"
            : "- Berufserfahrung: " + string.Join("; ", profile.Experience
                .OrderByDescending(e => e.From)
                .Select(e => $"{e.Position} bei {e.Employer} ({e.From:MM/yyyy}–" +
                             $"{(e.To is null ? "heute" : e.To.Value.ToString("MM/yyyy"))})")));
        lines.Add(profile.Education.Count == 0
            ? "- Ausbildung: nichts eingetragen"
            : "- Ausbildung: " + string.Join("; ", profile.Education
                .OrderByDescending(e => e.From)
                .Select(e => $"{e.Degree}, {e.Institution}")));
        lines.Add(profile.Languages.Count == 0
            ? "- Sprachen: nichts eingetragen"
            : "- Sprachen: " + string.Join(", ", profile.Languages.Select(l => $"{l.Language} {l.Level}")));

        lines.Add("");
        lines.Add("Das Gespräch bisher:");
        foreach (var message in said)
        {
            lines.Add($"{(message.FromUser ? "PERSON" : "ASSISTENT")}: {message.Text}");
        }
        return string.Join("\n", lines);
    }

    /// <summary>Two fields as one line, and "(leer)" where neither is filled in — so the prompt says
    /// that the profile is empty there rather than showing a blank the model reads as a name.</summary>
    private static string Filled(string first, string second)
    {
        var joined = string.Join(" ", new[] { first, second }.Where(s => !string.IsNullOrWhiteSpace(s)));
        return joined.Length == 0 ? "(leer)" : joined;
    }
}
