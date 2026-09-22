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
    public DbSet<Posting> Postings => Set<Posting>();
    public DbSet<Application> Applications => Set<Application>();

    protected override void OnModelCreating(ModelBuilder b)
    {
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

        // Enums are stored by name: a Lebenslauf outlives a renumbering of the enum.
        b.Entity<Profile>().Property(p => p.Template).HasConversion<string>();
        b.Entity<StoredDocument>().Property(d => d.Kind).HasConversion<string>();
        b.Entity<Posting>().Property(p => p.EmployerType).HasConversion<string>();
        b.Entity<Application>().Property(a => a.Tone).HasConversion<string>();
        b.Entity<Application>().Property(a => a.Status).HasConversion<string>();
    }
}
