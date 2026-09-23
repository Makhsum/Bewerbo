using System.Text.Json.Serialization;
using Bewerbo.Api.Data;
using Bewerbo.Api.Llm;
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

builder.Services.AddControllers();

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
});
builder.Services.AddHttpClient<ILanguageModel, LlmClient>(client =>
{
    client.Timeout = TimeSpan.FromSeconds(90);
});
builder.Services.AddScoped<IApplicationWriter, ApplicationWriter>();

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<BewerboDbContext>();
    db.Database.EnsureCreated();
}

app.UseExceptionHandler();
app.UseStatusCodePages();

app.MapControllers();

app.Run();

/// <summary>Named so the integration tests can reach the host builder.</summary>
public partial class Program;
