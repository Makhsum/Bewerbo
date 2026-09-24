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
/// Nothing is stored and nothing is proposed into the profile here: the reply is read, and a write
/// goes through the profile's own routes as every other write does.
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

    public async Task<AssistantReply?> TurnAsync(Profile profile, IReadOnlyList<AssistantMessage> said,
        string uiLanguage, CancellationToken ct = default)
    {
        if (!model.IsConfigured) return null;

        var result = await model.CompleteAsync<AssistantReply>(
            System(uiLanguage), Prompt(profile, said),
            OutputSchemas.AssistantSchemaName, OutputSchemas.Assistant, ct);

        if (result is { Reply.Length: > 0 }) return result;

        // An answer with nothing in it is not an answer. The route turns this into the same refusal
        // an unconfigured installation gets, because from where the user stands it is the same
        // thing: the assistant did not answer.
        log.LogInformation("Model returned no usable assistant turn.");
        return null;
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
        "Höchstens 120 Wörter in »reply«. Keine Emoji. " +
        "Die einzigen deutschen Wörter, die in deiner Antwort stehen dürfen, sind diese: " +
        string.Join(", ", KeptGermanTerms) +
        ". Jedes andere deutsche Wort übersetzt du in die Sprache der Antwort.";

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
