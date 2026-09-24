using Bewerbo.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace Bewerbo.Api.Data;

public class BewerboDbContext(DbContextOptions<BewerboDbContext> options) : DbContext(options)
{
    public DbSet<Profile> Profiles => Set<Profile>();
    public DbSet<ExperienceEntry> Experience => Set<ExperienceEntry>();
    public DbSet<EducationEntry> Education => Set<EducationEntry>();
    public DbSet<LanguageSkill> Languages => Set<LanguageSkill>();
    public DbSet<GapExplanation> Gaps => Set<GapExplanation>();
    public DbSet<StoredDocument> Documents => Set<StoredDocument>();
    public DbSet<DocumentScan> DocumentScans => Set<DocumentScan>();
    public DbSet<Posting> Postings => Set<Posting>();
    public DbSet<Application> Applications => Set<Application>();
    public DbSet<Account> Accounts => Set<Account>();
    public DbSet<AuthToken> AuthTokens => Set<AuthToken>();
    public DbSet<PasswordReset> PasswordResets => Set<PasswordReset>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<Account>(e =>
        {
            // The address IS the name of the account, so two accounts may not carry the same one.
            // The check in the controller says which of them came second; this is what makes the
            // answer true when two registrations arrive at once.
            e.HasIndex(a => a.Email).IsUnique();
            e.HasMany(a => a.Tokens).WithOne(t => t.Account!).HasForeignKey(t => t.AccountId)
                .OnDelete(DeleteBehavior.Cascade);
            // Down the same cascade as the sessions, and for the same reason: an erased account
            // that left a live reset code behind would leave a way back into an account that no
            // longer exists. See AccountErasure.
            e.HasMany(a => a.PasswordResets).WithOne(r => r.Account!).HasForeignKey(r => r.AccountId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        b.Entity<Profile>(e =>
        {
            e.HasMany(p => p.Experience).WithOne(x => x.Profile!).HasForeignKey(x => x.ProfileId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasMany(p => p.Education).WithOne(x => x.Profile!).HasForeignKey(x => x.ProfileId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasMany(p => p.Languages).WithOne(x => x.Profile!).HasForeignKey(x => x.ProfileId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasMany(p => p.Gaps).WithOne(x => x.Profile!).HasForeignKey(x => x.ProfileId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasMany(p => p.Documents).WithOne(x => x.Profile!).HasForeignKey(x => x.ProfileId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasMany(p => p.Applications).WithOne(x => x.Profile!).HasForeignKey(x => x.ProfileId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        b.Entity<StoredDocument>(e =>
        {
            // One scan per document, and it goes when the document goes. That edge is what makes
            // the whole of erasure the one cascade Profile → StoredDocument → DocumentScan, so
            // AccountErasure has no second store to sweep — see DocumentScan.
            e.HasOne(d => d.Scan).WithOne(s => s.Document!).HasForeignKey<DocumentScan>(s => s.DocumentId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // Enums are stored by name: a Lebenslauf outlives a renumbering of the enum.
        b.Entity<Profile>().Property(p => p.Template).HasConversion<string>();
        b.Entity<StoredDocument>().Property(d => d.Kind).HasConversion<string>();
        b.Entity<Posting>().Property(p => p.EmployerType).HasConversion<string>();
        b.Entity<Application>().Property(a => a.Tone).HasConversion<string>();
        b.Entity<Application>().Property(a => a.Status).HasConversion<string>();
    }
}
