using Bewerbo.Api.Domain;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Bewerbo.Api.Rendering;

/// <summary>
/// The Anlagenverzeichnis — the page that lists what the "Anlagen" line at the foot of the letter
/// refers to. German applications carry one whenever there is more than a Lebenslauf attached; a
/// bundle of unlabelled scans is what an application from abroad usually looks like.
/// </summary>
public class AnlagenverzeichnisDocument(IReadOnlyList<StoredDocument> documents, Profile profile) : IDocument
{
    public void Compose(IDocumentContainer container)
    {
        container.Page(page =>
        {
            page.Size(PageSizes.A4);
            page.MarginLeft(Din.MarginLeft, Unit.Millimetre);
            page.MarginRight(Din.MarginRight, Unit.Millimetre);
            page.MarginTop(20, Unit.Millimetre);
            page.MarginBottom(Din.MarginBottom, Unit.Millimetre);
            page.DefaultTextStyle(t => t
                .FontFamily(DocumentTheme.Serif)
                .FontSize(DocumentTheme.BodySize)
                .FontColor(DocumentTheme.Ink));

            page.Content().Column(column =>
            {
                column.Item().Text("Anlagenverzeichnis")
                    .FontSize(DocumentTheme.HeadingSize).Bold().FontColor(DocumentTheme.Primary);

                column.Item().PaddingTop(1, Unit.Millimetre)
                    .Text($"Bewerbung von {profile.FirstName} {profile.LastName}")
                    .FontColor(DocumentTheme.Muted);

                column.Item().PaddingTop(3, Unit.Millimetre)
                    .LineHorizontal(0.6f).LineColor(DocumentTheme.Rule);

                var index = 1;
                foreach (var group in documents.GroupBy(d => d.Kind).OrderBy(g => g.Key))
                {
                    column.Item().PaddingTop(5, Unit.Millimetre)
                        .Text(GroupTitle(group.Key))
                        .FontFamily(DocumentTheme.Sans)
                        .FontSize(DocumentTheme.SmallSize + 1)
                        .Bold().LetterSpacing(0.08f)
                        .FontColor(DocumentTheme.Primary);

                    foreach (var document in group.OrderBy(d => d.Title))
                    {
                        column.Item().PaddingTop(1.5f, Unit.Millimetre).Row(row =>
                        {
                            row.ConstantItem(8, Unit.Millimetre).Text($"{index}.");
                            row.RelativeItem().Column(c =>
                            {
                                c.Item().Text(document.Title);
                                if (!string.IsNullOrWhiteSpace(document.Note))
                                {
                                    c.Item().Text(document.Note)
                                        .FontSize(DocumentTheme.SmallSize + 0.5f)
                                        .FontColor(DocumentTheme.Muted);
                                }
                            });
                            row.ConstantItem(20, Unit.Millimetre).AlignRight()
                                .Text(document.PageCount == 1 ? "1 Seite" : $"{document.PageCount} Seiten")
                                .FontSize(DocumentTheme.SmallSize + 0.5f)
                                .FontColor(DocumentTheme.Muted);
                        });
                        index++;
                    }
                }

                if (documents.Count == 0)
                {
                    column.Item().PaddingTop(5, Unit.Millimetre)
                        .Text("Der Bewerbung liegt der Lebenslauf bei.")
                        .FontColor(DocumentTheme.Muted);
                }
            });
        });
    }

    private static string GroupTitle(DocumentKind kind) => kind switch
    {
        DocumentKind.Arbeitszeugnis => "ARBEITSZEUGNISSE",
        DocumentKind.Zertifikat => "ZERTIFIKATE",
        DocumentKind.Sprachnachweis => "SPRACHNACHWEISE",
        DocumentKind.AnabinAuszug => "ANERKENNUNG",
        _ => "WEITERE UNTERLAGEN",
    };
}
