using System.Text.RegularExpressions;
using Bewerbo.Api.Llm;

namespace Bewerbo.Api.Text;

public record ReviewCheck(string Key, string Title, string Verdict, string Detail, IReadOnlyList<string> Items);

public record ReviewResult(IReadOnlyList<ReviewCheck> Checks, int HintCount)
{
    public bool Passed => Checks.All(c => c.Verdict != "fehler");
}

/// <summary>
/// The Prüfung. Every check here is rule-based and countable — a phrase list, sentence openings,
/// numeral density, a word count. Nothing asks a model whether its own letter is any good.
/// </summary>
public static class TextReview
{
    public static ReviewResult Run(LetterContent letter, string? expectedContact, string? expectedReference)
    {
        var body = string.Join("\n\n", letter.Paragraphs);
        var full = $"{letter.Salutation}\n{letter.Subject}\n{body}";
        var checks = new List<ReviewCheck>();

        // 1. Floskeln — the banned list, matched literally.
        var floskeln = FloskelRules.Find(full);
        checks.Add(floskeln.Count == 0
            ? new ReviewCheck("floskeln", "Keine Floskeln gefunden", "ok",
                "Die gesperrten Wendungen kommen im Brief nicht vor.", FloskelRules.Banned.Take(4).ToList())
            : new ReviewCheck("floskeln", "Floskeln gefunden", "fehler",
                "Diese Wendungen stehen im Brief und sollten ersetzt werden.", floskeln));

        // 2. "Wirkt der Text maschinell?" — concrete numbers lower it, lists of virtues raise it.
        var sentences = SplitSentences(body);
        var digits = Regex.Matches(body, @"\d").Count;
        var density = body.Length == 0 ? 0 : (double)digits / body.Length;
        var virtueRun = Regex.Matches(body, @"\w+(?:stark|fähig|bewusst|orientiert)\b").Count;
        var machineScore = Math.Clamp(virtueRun * 2 - (density > 0.01 ? 1 : 0), 0, 4);
        checks.Add(new ReviewCheck("maschinell", "Wirkt der Text maschinell?",
            machineScore <= 1 ? "ok" : "hinweis",
            machineScore <= 1
                ? "Niedrig — konkrete Zahlen, keine Aufzählung von Tugenden."
                : $"Erhöht — {virtueRun} Tugendwörter, wenig konkrete Angaben.",
            []));

        // 3. Anrede, Betreff und Länge.
        var words = body.Split(' ', '\n').Count(w => w.Trim().Length > 0);
        var addressesPerson = !letter.Salutation.Contains("Damen und Herren", StringComparison.OrdinalIgnoreCase);
        var referenceInSubject = string.IsNullOrWhiteSpace(expectedReference)
            || letter.Subject.Contains(expectedReference, StringComparison.OrdinalIgnoreCase);
        var subjectClean = !letter.Subject.StartsWith("Betreff", StringComparison.OrdinalIgnoreCase);
        var formOk = addressesPerson && referenceInSubject && subjectClean && words is >= 120 and <= 450;
        var formDetail = new List<string>();
        if (!addressesPerson) formDetail.Add("Keine persönliche Anrede");
        if (!referenceInSubject) formDetail.Add("Referenznummer fehlt in der Betreffzeile");
        if (!subjectClean) formDetail.Add("Betreffzeile beginnt mit dem Wort »Betreff«");
        if (words < 120) formDetail.Add("Kürzer als eine Seite");
        if (words > 450) formDetail.Add("Länger als eine Seite");
        checks.Add(new ReviewCheck("form", "Anrede, Betreff und Länge", formOk ? "ok" : "hinweis",
            formOk
                ? $"{expectedContact ?? letter.Salutation} · Referenznummer im Betreff · 1 Seite, {words} Wörter"
                : string.Join(" · ", formDetail),
            formDetail));

        // 4. Ich-/Sie-Perspektive: a letter that opens every sentence with "Ich" reads as a list of
        //    claims rather than an answer to the posting.
        var ichOpens = sentences.Count(s => s.TrimStart().StartsWith("Ich", StringComparison.Ordinal));
        var perspectiveOk = sentences.Count == 0 || ichOpens * 2 <= sentences.Count;
        checks.Add(new ReviewCheck("perspektive", "Sie-Perspektive überwiegt nicht",
            perspectiveOk ? "ok" : "hinweis",
            $"{ichOpens} von {sentences.Count} Sätzen beginnen mit »Ich«",
            []));

        // 5. Anschreiben, not Motivationsschreiben.
        var isMotivation = full.Contains("Motivationsschreiben", StringComparison.OrdinalIgnoreCase)
                           || full.Contains("Studienplatz", StringComparison.OrdinalIgnoreCase)
                           || full.Contains("Stipendium", StringComparison.OrdinalIgnoreCase);
        checks.Add(new ReviewCheck("anschreiben", "Anschreiben, nicht Motivationsschreiben",
            isMotivation ? "fehler" : "ok",
            isMotivation
                ? "Der Text liest sich wie ein Motivationsschreiben — das gehört zu Studium und Stipendium."
                : "Der Text ist ein Anschreiben und antwortet auf die Anzeige.",
            []));

        var hints = checks.Count(c => c.Verdict == "hinweis");
        return new ReviewResult(checks, hints);
    }

    private static List<string> SplitSentences(string text) =>
        Regex.Split(text, @"(?<=[.!?])\s+")
             .Where(s => s.Trim().Length > 0)
             .ToList();
}
