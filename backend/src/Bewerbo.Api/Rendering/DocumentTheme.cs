using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Bewerbo.Api.Rendering;

/// <summary>
/// The norm, as numbers. Everything in <c>Rendering/</c> measures against these and nothing else —
/// no renderer carries its own idea of where the Anschriftenfeld starts, and no value here can be
/// influenced by the model.
///
/// DIN 5008 Form B (the form used when the letterhead is one line, which is what a job application
/// is). All values are millimetres from the top-left corner of the sheet.
/// </summary>
public static class Din
{
    /// <summary>Left edge of the type area. DIN 5008 gives 24.1 mm.</summary>
    public const float MarginLeft = 24.1f;

    /// <summary>Right edge. DIN 5008 gives 8.1 mm; 20 mm is the common practical choice.</summary>
    public const float MarginRight = 20f;

    /// <summary>Bottom edge of the type area.</summary>
    public const float MarginBottom = 16.9f;

    /// <summary>Top of the Anschriftenfeld — 45 mm in Form B. The number the inspector labels.</summary>
    public const float AddressFieldTop = 45f;

    /// <summary>The Anschriftenfeld itself: 85 mm wide, 45 mm tall.</summary>
    public const float AddressFieldWidth = 85f;
    public const float AddressFieldHeight = 45f;

    /// <summary>
    /// The Rücksendeangabe — the sender in one small line inside the top of the address field, so a
    /// window envelope shows the recipient and nothing else.
    /// </summary>
    public const float ReturnLineHeight = 5f;

    /// <summary>First fold mark. A letter folded here fits a DIN lang envelope.</summary>
    public const float FoldMark1 = 87f;

    /// <summary>Punch mark, exactly half the height of the sheet.</summary>
    public const float PunchMark = 148.5f;

    /// <summary>Second fold mark.</summary>
    public const float FoldMark2 = 192f;

    /// <summary>One line at the body's type size. Used for the norm's line-count distances.</summary>
    public const float Line = 4.23f;

    public const float A4Width = 210f;
    public const float A4Height = 297f;
}

/// <summary>
/// The document typography. Deliberately a different family from the app's UI: the app is Segoe UI
/// / Inter, the documents are a serif, because that is what a German application looks like on
/// paper. The list is a fallback chain so the same PDF renders on a Windows dev box and a Linux
/// host without the layout shifting.
/// </summary>
public static class DocumentTheme
{
    public static readonly string[] Serif =
    [
        "Georgia", "Times New Roman", "Liberation Serif", "DejaVu Serif", "Nimbus Roman",
    ];

    public static readonly string[] Sans =
    [
        "Segoe UI", "Inter", "Liberation Sans", "DejaVu Sans", "Arial",
    ];

    /// <summary>Petrol. The same token the app's UI uses, so document and app read as one product.</summary>
    public static readonly Color Primary = Color.FromHex("#0E3A53");
    public static readonly Color Ink = Color.FromHex("#0E1620");
    public static readonly Color Muted = Color.FromHex("#5A6B78");
    public static readonly Color Rule = Color.FromHex("#C7D2DA");

    /// <summary>The inspector's own colour — it is scaffolding drawn over the letter, not part of it.</summary>
    public static readonly Color InspectorLine = Color.FromHex("#1668A6");

    public const float BodySize = 10.5f;
    public const float SmallSize = 7.5f;
    public const float HeadingSize = 15f;
    public const float SubheadingSize = 11f;
}
