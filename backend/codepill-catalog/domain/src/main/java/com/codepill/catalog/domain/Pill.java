package com.codepill.catalog.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root of the catalog: a short, focused learning unit.
 *
 * <p>Lifecycle: DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED. Editing is allowed in
 * every state except ARCHIVED; publishing only from DRAFT or IN_REVIEW.
 * {@code createdAt}/{@code updatedAt}/{@code version} are persistence-managed
 * and carried only for reconstitution and optimistic locking.
 */
public final class Pill {

    private final PillId id;
    private final AuthorId authorId;
    private Title title;
    private Slug slug;
    private Summary summary; // nullable — a pill may have no summary
    private PillContent content;
    private PillType type;
    private EstimatedDuration estimatedDuration;
    private PillStatus status;
    private Instant publishedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private Pill(PillId id, AuthorId authorId, Title title, Slug slug, Summary summary,
                 PillContent content, PillType type, EstimatedDuration estimatedDuration,
                 PillStatus status, Instant publishedAt, Instant createdAt, Instant updatedAt,
                 long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.authorId = Objects.requireNonNull(authorId, "authorId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.slug = Objects.requireNonNull(slug, "slug must not be null");
        this.summary = summary;
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.estimatedDuration = Objects.requireNonNull(estimatedDuration, "estimatedDuration must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /** A brand-new, never-persisted pill in DRAFT. */
    public static Pill draft(PillId id, AuthorId authorId, Title title, Slug slug, Summary summary,
                             PillContent content, PillType type, EstimatedDuration estimatedDuration) {
        return new Pill(id, authorId, title, slug, summary, content, type, estimatedDuration,
                PillStatus.DRAFT, null, null, null, 0L);
    }

    public void update(Title title, Slug slug, Summary summary, PillContent content,
                       PillType type, EstimatedDuration estimatedDuration) {
        if (status == PillStatus.ARCHIVED) {
            throw new PillLifecycleException("an archived pill cannot be edited");
        }
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.slug = Objects.requireNonNull(slug, "slug must not be null");
        this.summary = summary;
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.estimatedDuration = Objects.requireNonNull(estimatedDuration, "estimatedDuration must not be null");
    }

    public void publish(Instant now) {
        Objects.requireNonNull(now, "publication instant must not be null");
        if (status == PillStatus.PUBLISHED) {
            throw new PillLifecycleException("pill is already published");
        }
        if (status == PillStatus.ARCHIVED) {
            throw new PillLifecycleException("an archived pill cannot be published");
        }
        this.status = PillStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public boolean isOwnedBy(AuthorId candidate) {
        return authorId.equals(candidate);
    }

    public boolean isPublished() {
        return status == PillStatus.PUBLISHED;
    }

    public PillSnapshot snapshot() {
        return new PillSnapshot(
                id.value(),
                authorId.value(),
                title.value(),
                slug.value(),
                summary == null ? null : summary.value(),
                content.json(),
                type.name(),
                status.name(),
                estimatedDuration.seconds(),
                publishedAt,
                createdAt,
                updatedAt,
                version);
    }

    public static Pill fromSnapshot(PillSnapshot s) {
        Objects.requireNonNull(s, "snapshot must not be null");
        return new Pill(
                PillId.of(s.id()),
                AuthorId.of(s.authorId()),
                new Title(s.title()),
                new Slug(s.slug()),
                Summary.ofNullable(s.summary()),
                new PillContent(s.content()),
                PillType.valueOf(s.pillType()),
                new EstimatedDuration(s.estimatedDurationSeconds()),
                PillStatus.valueOf(s.status()),
                s.publishedAt(),
                s.createdAt(),
                s.updatedAt(),
                s.version());
    }

    public PillId id() { return id; }
    public AuthorId authorId() { return authorId; }
    public Title title() { return title; }
    public Slug slug() { return slug; }
    public Summary summary() { return summary; }
    public PillContent content() { return content; }
    public PillType type() { return type; }
    public EstimatedDuration estimatedDuration() { return estimatedDuration; }
    public PillStatus status() { return status; }
    public Instant publishedAt() { return publishedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
