namespace Bewerbo.Api.Legal;

/// <summary>
/// Who runs this installation — the particulars § 5 DDG requires an Impressum to name, and the
/// only part of the legal pages that is not the app's own text.
///
/// It is configuration and not a resource string for one reason: the operator is a fact of the
/// DEPLOYMENT. A name and an address compiled into the APK would be wrong for everybody who is not
/// the one operator it was written for, and a placeholder address in an Impressum is worse than no
/// Impressum at all — it is a false statement about who is responsible. So the app asks, and where
/// nothing has been configured it says so plainly instead of inventing a Berlin address.
///
/// Read out of configuration the way <see cref="Llm.LlmOptions"/> is, so the usual environment
/// variables work: Legal__OperatorName, Legal__Street, and so on.
/// </summary>
public class LegalOptions
{
    public string OperatorName { get; set; } = "";
    public string Street { get; set; } = "";
    public string PostalCode { get; set; } = "";
    public string City { get; set; } = "";
    public string Country { get; set; } = "";
    public string Email { get; set; } = "";

    /// <summary>The natural person answering for a company — § 5 Abs. 1 Nr. 1 DDG.</summary>
    public string Represented { get; set; } = "";

    /// <summary>Register and number, where the operator is entered in one.</summary>
    public string Register { get; set; } = "";

    /// <summary>
    /// Whether what is configured amounts to an Impressum at all. Four members carry the duty — who,
    /// where, and how to reach them — and a page missing any of them must not present itself as one.
    /// </summary>
    public bool IsStated =>
        !string.IsNullOrWhiteSpace(OperatorName)
        && !string.IsNullOrWhiteSpace(Street)
        && !string.IsNullOrWhiteSpace(City)
        && !string.IsNullOrWhiteSpace(Email);
}
