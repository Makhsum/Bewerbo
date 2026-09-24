namespace Bewerbo.Api.Rendering;

/// <summary>
/// The one sentence a rendered page carries about who wrote its German.
///
/// It lives here rather than in the document that first printed it because the Anschreiben and the
/// Lebenslauf leave the app inside ONE file and each names its own writer — and the two really can
/// differ: the letter is the stored one, written when the user asked for it, while the Lebenslauf
/// is written afresh on the way into the export. Two copies of this German would drift, and the
/// employer reading both pages is exactly the reader who would see it.
/// </summary>
public static class WriterNote
{
    /// <summary>
    /// <paramref name="writer"/> is <see cref="Llm.IApplicationWriter.LastSource"/> — "model" or
    /// "regeln". Two writers, two sentences and no third: an unknown value reads as the rule-based
    /// one, because that is what runs whenever no model answered.
    ///
    /// German, because the page is German — it is read by an employer and not by the user's
    /// interface, which names the writer on screen in the interface language of its own.
    /// </summary>
    public static string For(string writer) => writer == "model"
        ? "Erstellt mit Bewerbo. Die deutschen Formulierungen stammen von einem KI-Sprachmodell."
        : "Erstellt mit Bewerbo. Die deutschen Formulierungen stammen aus den Textregeln von " +
          "Bewerbo, nicht von einem KI-Sprachmodell.";
}
