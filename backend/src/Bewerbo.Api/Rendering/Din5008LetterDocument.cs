using System.Globalization;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Bewerbo.Api.Rendering;

/// <summary>
/// The Anschreiben, laid out to DIN 5008 Form B.
///
/// This class is the only thing that decides where anything sits. What it receives is
/// <see cref="LetterContent"/> — a salutation, a subject line and paragraphs — and there is no
/// field in that type through which a model could ask for a different margin, font or page break.
/// </summary>
public class Din5008LetterDocument(
    LetterContent letter,
    Profile profile,
    Posting posting,
    DateOnly date,
    bool showInspector = false) : IDocument
{
    private static readonly CultureInfo German = CultureInfo.GetCultureInfo("de-DE");

    public void Compose(IDocumentContainer container)
    {
        container.Page(page =>
        {
            page.Size(PageSizes.A4);
            // Margins are zero on purpose: every distance in this letter is measured from the sheet
            // edge, the way the norm states them, rather than from a margin box.
            page.Margin(0);
            page.DefaultTextStyle(t => t
                .FontFamily(DocumentTheme.Serif)
                .FontSize(DocumentTheme.BodySize)
                .LineHeight(1.25f)
                .FontColor(DocumentTheme.Ink));

            page.Content().Layers(layers =>
            {
                layers.Layer().Element(FoldAndPunchMarks);
                if (showInspector) layers.Layer().Element(Inspector);
                layers.PrimaryLayer().Element(Body);
            });
        });
    }

    // -- the letter -----------------------------------------------------------------------------

    private void Body(IContainer container)
    {
        container.Column(column =>
        {
            // 0 – 45 mm: the Briefkopf band. The sender's one line sits at its foot, directly above
            // the address field, which is what a German one-line letterhead looks like.
            column.Item()
                .Height(Din.AddressFieldTop, Unit.Millimetre)
                .PaddingLeft(Din.MarginLeft, Unit.Millimetre)
                .PaddingRight(Din.MarginRight, Unit.Millimetre)
                .PaddingBottom(2, Unit.Millimetre)
                .AlignBottom()
                .Text(SenderLine())
                .FontSize(DocumentTheme.SmallSize)
                .FontColor(DocumentTheme.Muted);

            // 45 – 90 mm: the Anschriftenfeld.
            column.Item()
                .Height(Din.AddressFieldHeight, Unit.Millimetre)
                .PaddingLeft(Din.MarginLeft, Unit.Millimetre)
                .Width(Din.AddressFieldWidth, Unit.Millimetre)
                .Column(address =>
                {
                    address.Item().Height(Din.ReturnLineHeight, Unit.Millimetre);
                    foreach (var line in RecipientLines())
                    {
                        address.Item().Text(line);
                    }
                });

            // The Informationsblock. Place and date, right-aligned — never "den 22.09.2026".
            column.Item()
                .PaddingLeft(Din.MarginLeft, Unit.Millimetre)
                .PaddingRight(Din.MarginRight, Unit.Millimetre)
                .PaddingTop(Din.Line * 2, Unit.Millimetre)
                .AlignRight()
                .Text(PlaceAndDate(date));

            column.Item()
                .PaddingLeft(Din.MarginLeft, Unit.Millimetre)
                .PaddingRight(Din.MarginRight, Unit.Millimetre)
                .PaddingBottom(Din.MarginBottom, Unit.Millimetre)
                .Column(text =>
                {
                    // The Betreffzeile: bold, and without the word "Betreff". Two lines below the
                    // Informationsblock, one blank line before the salutation.
                    text.Item().PaddingTop(Din.Line * 2, Unit.Millimetre)
                        .Text(letter.Subject).Bold();

                    text.Item().PaddingTop(Din.Line * 2, Unit.Millimetre)
                        .Text($"{letter.Salutation},");

                    foreach (var paragraph in letter.Paragraphs)
                    {
                        text.Item().PaddingTop(Din.Line, Unit.Millimetre)
                            .Text(paragraph).Justify();
                    }

                    text.Item().PaddingTop(Din.Line * 2, Unit.Millimetre).Text(letter.Closing);

                    // The space a signature is written into, then the typed name under it.
                    text.Item().PaddingTop(14, Unit.Millimetre)
                        .Width(55, Unit.Millimetre)
                        .LineHorizontal(0.4f).LineColor(DocumentTheme.Rule);
                    text.Item().PaddingTop(1, Unit.Millimetre)
                        .Text($"{profile.FirstName} {profile.LastName}");

                    if (letter.Attachments.Count > 0)
                    {
                        // Anlagen at the foot, which is where the norm puts them and where a reader
                        // looks for them.
                        text.Item().PaddingTop(Din.Line * 2, Unit.Millimetre).Row(row =>
                        {
                            row.ConstantItem(18, Unit.Millimetre).Text("Anlagen").Bold();
                            row.RelativeItem().Text(string.Join(", ", letter.Attachments));
                        });
                    }
                });
        });
    }

    private string SenderLine() =>
        string.Join("  ·  ", new[]
        {
            $"{profile.FirstName} {profile.LastName}",
            profile.Street,
            $"{profile.PostalCode} {profile.City}".Trim(),
        }.Where(s => !string.IsNullOrWhiteSpace(s)));

    private string SenderCity() => string.IsNullOrWhiteSpace(profile.City) ? "" : profile.City;

    // The separator belongs to the two parts, not to the line: SenderCity() already answers "" for
    // a profile that has not named a city yet, but the comma was written regardless and the letter
    // opened with ", 23. September 2026". Dropped the way SenderLine drops a missing street.
    private string PlaceAndDate(DateOnly date) =>
        string.Join(", ", new[]
        {
            SenderCity(),
            date.ToDateTime(TimeOnly.MinValue).ToString("d. MMMM yyyy", German),
        }.Where(s => !string.IsNullOrWhiteSpace(s)));

    /// <summary>
    /// Company, then the person, then the role, then the street address. A German recipient block
    /// names the person under the company — the other way round is a tell.
    /// </summary>
    private IEnumerable<string> RecipientLines()
    {
        if (!string.IsNullOrWhiteSpace(posting.Company)) yield return posting.Company;
        if (!string.IsNullOrWhiteSpace(posting.ContactName)) yield return posting.ContactName;
        if (!string.IsNullOrWhiteSpace(posting.ContactRole)) yield return posting.ContactRole;

        foreach (var line in SplitAddress(posting.CompanyAddress)) yield return line;
    }

    private static IEnumerable<string> SplitAddress(string? address)
    {
        if (string.IsNullOrWhiteSpace(address)) yield break;

        // "Industriestraße 8, 70563 Stuttgart" is two lines on an envelope, not one.
        var parts = address.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        foreach (var part in parts) yield return part;
    }

    // -- the marks the norm puts on the page ------------------------------------------------------

    /// <summary>
    /// Faltmarken and Lochmarke: short hairlines at the left edge. They are part of the letter, not
    /// of the inspector — a letter printed without them does not fold into a window envelope.
    /// </summary>
    private static void FoldAndPunchMarks(IContainer container)
    {
        (float At, float Length)[] marks =
        [
            (Din.FoldMark1, 5f), (Din.PunchMark, 8f), (Din.FoldMark2, 5f),
        ];

        container.Column(column =>
        {
            // Each mark consumes the distance from the previous one. The cursor is a local, so two
            // letters rendering at the same time cannot interleave their positions.
            var cursor = 0f;
            foreach (var (at, length) in marks)
            {
                column.Item().Height(at - cursor, Unit.Millimetre);
                column.Item().PaddingLeft(6, Unit.Millimetre)
                    .Width(length, Unit.Millimetre)
                    .LineHorizontal(0.4f).LineColor(DocumentTheme.Rule);
                cursor = at;
            }
        });
    }

    /// <summary>
    /// The DIN inspector: the norm drawn over the letter so it can be checked rather than believed.
    /// This is scaffolding — it is only ever rendered into the preview, never into the export.
    /// </summary>
    private static void Inspector(IContainer container)
    {
        container.Column(column =>
        {
            column.Item().Height(Din.AddressFieldTop, Unit.Millimetre);

            column.Item().PaddingLeft(Din.MarginLeft, Unit.Millimetre).Row(row =>
            {
                row.ConstantItem(Din.AddressFieldWidth, Unit.Millimetre)
                    .Height(Din.AddressFieldHeight, Unit.Millimetre)
                    .Border(0.5f).BorderColor(DocumentTheme.InspectorLine);

                row.RelativeItem().PaddingLeft(3, Unit.Millimetre).PaddingTop(1, Unit.Millimetre)
                    .Text($"Anschriftenfeld {Din.AddressFieldTop:0} mm")
                    .FontFamily(DocumentTheme.Sans)
                    .FontSize(6.5f)
                    .FontColor(DocumentTheme.InspectorLine);
            });

            column.Item().Height(Din.FoldMark1 - Din.AddressFieldTop - Din.AddressFieldHeight, Unit.Millimetre);
            column.Item().PaddingLeft(6, Unit.Millimetre)
                .Text($"Faltmarke {Din.FoldMark1:0} mm")
                .FontFamily(DocumentTheme.Sans).FontSize(6.5f).FontColor(DocumentTheme.InspectorLine);
        });
    }
}
