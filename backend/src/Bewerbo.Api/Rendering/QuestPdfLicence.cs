using System.Runtime.CompilerServices;
using QuestPDF.Infrastructure;

namespace Bewerbo.Api.Rendering;

internal static class QuestPdfLicence
{
    /// <summary>
    /// Declares the QuestPDF licence for the whole assembly, as a module initializer rather than a
    /// line in Program.cs: anything that renders needs it, and a unit test that renders a document
    /// never runs Program.cs. Setting it there meant the test host threw on the first PDF.
    ///
    /// Community covers us below roughly $1M annual revenue. Above that this line has to change.
    /// </summary>
    [ModuleInitializer]
    internal static void Configure() => QuestPDF.Settings.License = LicenseType.Community;
}
