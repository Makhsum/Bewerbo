using System.Globalization;
using Bewerbo.Api.Domain;
using Bewerbo.Api.Llm;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Bewerbo.Api.Rendering;

/// <summary>
/// The Lebenslauf. Three German-normed layouts, chosen by <see cref="CvTemplate"/> — and
/// deliberately no Europass: in Germany it reads as foreign and bureaucratic, which is the exact
/// impression the product exists to avoid.
///
/// The template changes proportions and rules, never the content: the same <see cref="CvContent"/>
/// renders under all three.
/// </summary>
public class LebenslaufDocument(CvContent content, Profile profile, CvTemplate template) : IDocument
{
    private static readonly CultureInfo German = CultureInfo.GetCultureInfo("de-DE");

    /// <summary>The width of the date column. Klassisch gives the dates their own generous column.</summary>
    private float PeriodColumn => template switch
    {
        CvTemplate.Klassisch => 38f,
        CvTemplate.Modern => 32f,
        _ => 34f,
    };

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
                .LineHeight(1.2f)
                .FontColor(DocumentTheme.Ink));

            page.Content().Column(column =>
            {
                Header(column);
                foreach (var section in content.Sections) Section(column, section);
                Footer(column);
            });
        });
    }

    private void Header(ColumnDescriptor column)
    {
        column.Item().Text($"{profile.FirstName} {profile.LastName}")
            .FontSize(DocumentTheme.HeadingSize + (template == CvTemplate.Modern ? 3 : 0))
            .Bold()
            .FontColor(DocumentTheme.Primary);

        if (!string.IsNullOrWhiteSpace(content.Headline))
        {
            column.Item().PaddingTop(1, Unit.Millimetre)
                .Text(content.Headline)
                .FontSize(DocumentTheme.SubheadingSize)
                .FontColor(DocumentTheme.Muted);
        }

        // Contact data is part of every German CV; the optional personal data (birth date, photo)
        // is not, and is only added where the employer type expects it.
        var contact = new[]
            {
                profile.Street,
                $"{profile.PostalCode} {profile.City}".Trim(),
                profile.Phone,
                profile.Email,
            }
            .Where(s => !string.IsNullOrWhiteSpace(s));

        column.Item().PaddingTop(2, Unit.Millimetre)
            .Text(string.Join("  ·  ", contact))
            .FontSize(DocumentTheme.SmallSize + 0.5f)
            .FontColor(DocumentTheme.Muted);

        column.Item().PaddingTop(3, Unit.Millimetre)
            .LineHorizontal(template == CvTemplate.Modern ? 1.2f : 0.6f)
            .LineColor(template == CvTemplate.Modern ? DocumentTheme.Primary : DocumentTheme.Rule);
    }

    private void Section(ColumnDescriptor column, CvSection section)
    {
        column.Item().PaddingTop(6, Unit.Millimetre)
            .Text(section.Title.ToUpperInvariant())
            .FontFamily(DocumentTheme.Sans)
            .FontSize(DocumentTheme.SmallSize + 1)
            .Bold()
            .LetterSpacing(0.08f)
            .FontColor(DocumentTheme.Primary);

        column.Item().PaddingTop(1.5f, Unit.Millimetre)
            .LineHorizontal(0.5f).LineColor(DocumentTheme.Rule);

        foreach (var item in section.Items)
        {
            column.Item().PaddingTop(3, Unit.Millimetre).Row(row =>
            {
                row.ConstantItem(PeriodColumn, Unit.Millimetre)
                    .PaddingTop(0.3f, Unit.Millimetre)
                    .Text(item.Period)
                    .FontSize(DocumentTheme.BodySize - 0.5f)
                    .FontColor(item.IsGap ? DocumentTheme.Muted : DocumentTheme.Ink);

                row.RelativeItem().Column(entry =>
                {
                    // A named gap is set in italics rather than hidden. The point is that the
                    // reader finds an answer where they were about to find a question.
                    if (item.IsGap)
                    {
                        entry.Item().Text(item.Title)
                            .Italic().FontColor(DocumentTheme.Muted);
                        return;
                    }

                    entry.Item().Text(item.Title).Bold();

                    if (!string.IsNullOrWhiteSpace(item.Subtitle))
                    {
                        entry.Item().Text(item.Subtitle).FontColor(DocumentTheme.Muted);
                    }

                    foreach (var bullet in item.Bullets)
                    {
                        entry.Item().PaddingTop(0.8f, Unit.Millimetre).Row(b =>
                        {
                            b.ConstantItem(3.5f, Unit.Millimetre).Text("–");
                            b.RelativeItem().Text(bullet);
                        });
                    }
                });
            });
        }
    }

    private void Footer(ColumnDescriptor column)
    {
        // Place and date under a Lebenslauf is still expected by a traditional German reader.
        // A profile that has not named a city yet gets the date alone rather than a leading comma.
        var placeAndDate = string.Join(", ", new[]
        {
            profile.City,
            DateTime.Today.ToString("d. MMMM yyyy", German),
        }.Where(s => !string.IsNullOrWhiteSpace(s)));

        column.Item().PaddingTop(10, Unit.Millimetre)
            .Text(placeAndDate)
            .FontSize(DocumentTheme.SmallSize + 0.5f)
            .FontColor(DocumentTheme.Muted);
    }
}
