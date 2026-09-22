namespace Bewerbo.Api.Text;

/// <summary>
/// Whether a piece of text is written in the Latin alphabet.
///
/// The product's promise is German output. The user's input is not: it is Ukrainian, Russian or
/// Turkish. Translating it is the language model's job, and where no model is configured the
/// rule-based writer must not pretend — a German sentence with a Cyrillic clause spliced into it is
/// worse than a shorter German sentence, because the applicant cannot see that it is wrong.
/// </summary>
public static class ScriptCheck
{
    /// <summary>
    /// True when every letter is Latin. Digits, punctuation and spaces do not count either way, so
    /// "DATEV 14" is Latin and "DATEV для 14" is not.
    /// </summary>
    public static bool IsLatin(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return true;

        foreach (var c in text)
        {
            if (!char.IsLetter(c)) continue;
            // Basic Latin, Latin-1 Supplement, Latin Extended-A and -B. Anything past that —
            // Cyrillic starts at U+0400 — is a different script.
            if (c > 'ɏ') return false;
        }
        return true;
    }

    /// <summary>The fragments that are not usable in a German sentence as they stand.</summary>
    public static IReadOnlyList<string> Untranslated(IEnumerable<string> lines) =>
        lines.Where(l => !IsLatin(l)).ToList();
}
