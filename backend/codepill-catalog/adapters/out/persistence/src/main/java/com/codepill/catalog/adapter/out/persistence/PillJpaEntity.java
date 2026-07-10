package com.codepill.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the {@code pills} table. Persistence model only — never
 * exposed through controllers (ARCHITECTURE.md §3.1 rule 4). The generated
 * {@code search_vector} column is intentionally unmapped.
 */
@Entity
@Table(name = "pills")
public class PillJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "summary", length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "pill_type", nullable = false, length = 30)
    private String pillType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "estimated_duration_seconds", nullable = false)
    private int estimatedDurationSeconds;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** {@code null} marks a never-persisted aggregate (insert instead of merge). */
    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PillJpaEntity() {
        // for JPA
    }

    PillJpaEntity(UUID id, UUID authorId, String title, String slug, String summary,
                  String content, String pillType, String status, int estimatedDurationSeconds,
                  Instant publishedAt, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.authorId = authorId;
        this.title = title;
        this.slug = slug;
        this.summary = summary;
        this.content = content;
        this.pillType = pillType;
        this.status = status;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
        this.publishedAt = publishedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() { return id; }
    UUID getAuthorId() { return authorId; }
    String getTitle() { return title; }
    String getSlug() { return slug; }
    String getSummary() { return summary; }
    String getContent() { return content; }
    String getPillType() { return pillType; }
    String getStatus() { return status; }
    int getEstimatedDurationSeconds() { return estimatedDurationSeconds; }
    Instant getPublishedAt() { return publishedAt; }
    Long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
