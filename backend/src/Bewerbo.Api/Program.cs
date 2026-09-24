using System.Text.Json.Serialization;
using Bewerbo.Api.Controllers;
using Bewerbo.Api.Data;
using Bewerbo.Api.Llm;
using Bewerbo.Api.Mail;
using Bewerbo.Api.Services;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);


// The rule, checked before the first request rather than trusted: no schema handed to the model may
// contain a layout field.
OutputSchemas.AssertNoLayoutFields();

builder.Services.AddDbContext<BewerboDbContext>(options =>
{
    // PostgreSQL is the product's database and the one the EU hosting runs. A dev machine rarely
    // has one, so a connection string decides: with one, Npgsql; without, a SQLite file. Same
    // DbContext, same model, same migrations shape — only the provider differs.
    var connection = builder.Configuration.GetConnectionString("Bewerbo");
    if (!string.IsNullOrWhiteSpace(connection))
    {
        options.UseNpgsql(connection);
    }
    else
    {
        var path = Path.Combine(AppContext.BaseDirectory, "bewerbo.db");
        options.UseSqlite($"Data Source={path}");
    }
});

// Who is asking, and whether what they named is theirs. The scheme reads the Authorization header
// and hands the request the account's profile id; the filter compares that id against the record
// every route is addressed by. [Authorize] on BewerboController is what applies the first to the
// whole API, and the filter is registered globally for the same reason — a route written later is
// covered by default rather than by being remembered. See SessionAuthentication and OwnershipFilter.
builder.Services
    .AddAuthentication(SessionAuthentication.Scheme)
    .AddScheme<AuthenticationSchemeOptions, SessionAuthenticationHandler>(SessionAuthentication.Scheme, null);
builder.Services.AddAuthorization();

builder.Services.AddControllers(options => options.Filters.Add<OwnershipFilter>());

builder.Services.Configure<MvcOptions>(options =>
{
    // A record member that the JSON left out arrives as null, and the routes read it as "the user
    // did not fill this in" — ProfileController turns an absent "industry" into "". MVC would
    // otherwise reject the same body before a route sees it, because it takes a non-nullable
    // reference type for a required field. The check that decides which fields are truly required
    // stays in the controllers, where it can say which entry was at fault.
    options.SuppressImplicitRequiredAttributeForNonNullableReferenceTypes = true;
});

// Two JSON configurations, one behaviour. Controllers serialise through Mvc.JsonOptions; what the
// framework writes around them — a ProblemDetails from the status-code or exception middleware —
// goes through Http.Json.JsonOptions. Setting only one of them makes null members appear in half
// the answers.
builder.Services.Configure<Microsoft.AspNetCore.Http.Json.JsonOptions>(options =>
{
    options.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
});
builder.Services.Configure<Microsoft.AspNetCore.Mvc.JsonOptions>(options =>
{
    options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
});

// The one error shape. Everything the API answers with outside 2xx is a ProblemDetails: the
// framework's own 400 for a body it could not read, the 404 and 400 the controllers write through
// BewerboController, the 405 for a wrong verb, and an exception nobody caught.
builder.Services.AddProblemDetails();

builder.Services.AddSingleton(new LlmOptions
{
    ApiKey = builder.Configuration["Anthropic:ApiKey"]
             ?? Environment.GetEnvironmentVariable("ANTHROPIC_API_KEY"),
    Model = builder.Configuration["Anthropic:Model"] ?? "claude-opus-5",
    // The endpoint, configurable for the reason the two above are: a deployment that reaches the
    // model through a gateway of its own is the same installation with a different address, and it
    // is also the only way a machine with no key can exercise the model path at all. Unset means
    // Anthropic's own, which is what every installation has done so far.
    BaseUrl = builder.Configuration["Anthropic:BaseUrl"] ?? new LlmOptions().BaseUrl,
});
// Who runs this installation, for the Impressum. Configuration and not a resource string, because
// an address compiled into the app would be wrong for every deployment but one — see LegalOptions.
builder.Services.AddSingleton(new Bewerbo.Api.Legal.LegalOptions
{
    OperatorName = builder.Configuration["Legal:OperatorName"] ?? "",
    Street = builder.Configuration["Legal:Street"] ?? "",
    PostalCode = builder.Configuration["Legal:PostalCode"] ?? "",
    City = builder.Configuration["Legal:City"] ?? "",
    Country = builder.Configuration["Legal:Country"] ?? "",
    Email = builder.Configuration["Legal:Email"] ?? "",
    Represented = builder.Configuration["Legal:Represented"] ?? "",
    Register = builder.Configuration["Legal:Register"] ?? "",
});
// Where the one mail this product sends goes out. Two implementations rather than one that checks
// whether it is configured, because they share nothing: with a host and a from-address it is SMTP,
// and without them the mail is written to a file next to the binary so that a dev machine with
// nothing installed can still finish a password reset. GET /api/health says which one is running,
// for the reason it says which writer is.
builder.Services.AddSingleton(new MailOptions
{
    Host = builder.Configuration["Mail:Host"],
    Port = int.TryParse(builder.Configuration["Mail:Port"], out var mailPort) ? mailPort : 587,
    User = builder.Configuration["Mail:User"],
    Password = builder.Configuration["Mail:Password"],
    From = builder.Configuration["Mail:From"],
    UseSsl = !bool.TryParse(builder.Configuration["Mail:UseSsl"], out var mailSsl) || mailSsl,
});
builder.Services.AddSingleton<IMailSender>(services =>
{
    var options = services.GetRequiredService<MailOptions>();
    return options.IsConfigured
        ? new SmtpMailSender(options, services.GetRequiredService<ILogger<SmtpMailSender>>())
        : new FileMailSender(services.GetRequiredService<ILogger<FileMailSender>>());
});

builder.Services.AddHttpClient<ILanguageModel, LlmClient>(client =>
{
    client.Timeout = TimeSpan.FromSeconds(90);
});

// The page behind a link the user pasted. Short timeout — the user is waiting in front of a
// spinner, and a portal that has not answered in fifteen seconds is not going to. The User-Agent
// is a browser's on purpose: several job portals answer a client without one with a consent wall
// instead of the advert.
builder.Services.AddHttpClient(Bewerbo.Api.Controllers.PostingsController.LinkClient, client =>
{
    client.Timeout = TimeSpan.FromSeconds(15);
    client.DefaultRequestHeaders.UserAgent.ParseAdd(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36");
    client.DefaultRequestHeaders.AcceptLanguage.ParseAdd("de-DE,de;q=0.9");
    // A portal that serves a 40 MB single-page bundle is not serving an advert this can read, and
    // reading it into a string would cost the server more than saying so.
    client.MaxResponseContentBufferSize = 8 * 1024 * 1024;
});
builder.Services.AddScoped<IApplicationWriter, ApplicationWriter>();
// The assistant, beside the writer and registered the same way. No second implementation stands
// behind it: with no key there is no assistant, and AssistantController says so — see
// AssistantConversation for why a rule-based chat would be worse than none.
builder.Services.AddScoped<IAssistantConversation, AssistantConversation>();

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<BewerboDbContext>();
    // Two different starts, one schema. A machine with no database gets the whole of it from
    // EnsureCreated; a machine that has been running since an earlier version gets nothing from it —
    // EnsureCreated only ever creates, never completes — and keeps its rows while SchemaUpdate adds
    // what the model has gained since. The line it writes is deliberate: an installation that was
    // behind should say so once on the way up, rather than let the next request explain it as a 500.
    db.Database.EnsureCreated();
    var schemaChanges = db.BringSchemaUpToDate();
    if (schemaChanges.Count > 0)
    {
        app.Logger.LogInformation(
            "Database schema brought up to date, {Count} addition(s): {Changes}",
            schemaChanges.Count, string.Join(", ", schemaChanges));
    }
}

app.UseExceptionHandler();
app.UseStatusCodePages();

// A request that names no Content-Type at all is read as JSON. The Minimal API this replaced had
// one body reader and simply used it, so a POST with an empty body and no header came back 400
// "a body is required"; a controller refuses it with 415 before the action is reached. Only the
// ABSENT header is filled in — a request that says text/plain still gets the 415 it always got.
app.Use(async (context, next) =>
{
    var request = context.Request;
    if (string.IsNullOrEmpty(request.ContentType)
        && (HttpMethods.IsPost(request.Method)
            || HttpMethods.IsPut(request.Method)
            || HttpMethods.IsPatch(request.Method)))
    {
        request.ContentType = "application/json";
    }

    await next(context);
});

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();

/// <summary>Named so the integration tests can reach the host builder.</summary>
public partial class Program;
