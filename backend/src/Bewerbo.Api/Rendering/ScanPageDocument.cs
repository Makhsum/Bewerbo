using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Bewerbo.Api.Rendering;

/// <summary>
/// A photographed document on a sheet of A4 — the one thing that has to happen to a JPEG or a PNG
/// before it can be appended to the Bewerbungsmappe.
///
/// A PDF scan is already pages and is appended as it stands; a picture is not, and a Mappe whose
/// last attachment is a loose image file is the "four attachments" problem
/// <see cref="MergedApplicationDocument"/> exists to avoid. So the picture is given a page.
///
/// FitArea and a margin rather than the whole sheet: a phone photograph is almost never A4's
/// proportions, and stretching a Zeugnis to fill the page is the one thing that would make it look
/// forged. It keeps its own shape, centred, with white around it.
/// </summary>
public class ScanPageDocument(byte[] image) : IDocument
{
    /// <summary>The white border around a scan, so it does not run off the printed edge.</summary>
    private const float MarginMm = 10f;

    public void Compose(IDocumentContainer container)
    {
        container.Page(page =>
        {
            page.Size(PageSizes.A4);
            page.Margin(MarginMm, Unit.Millimetre);
            page.Content().AlignCenter().AlignMiddle().Image(image).FitArea();
        });
    }
}
