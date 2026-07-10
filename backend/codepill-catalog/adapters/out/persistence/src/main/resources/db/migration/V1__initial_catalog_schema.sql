-- codepill-catalog — initial schema (pills, tags, pill_tags)
--
-- Read-heavy service: the dominant queries are the published feed (keyset
-- pagination), pill-by-slug, pills-by-tag, author workspaces, and full-text
-- search. Indexes below are designed for those paths; write amplification is
-- acceptable because authoring volume is orders of magnitude below read volume.
--
-- author_id is the users.id UUID owned by codepill-identity — referenced as an
-- opaque value, never a cross-service FK (ARCHITECTURE.md §3.2).

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── pills ──────────────────────────────────────────────────────────────────
CREATE TABLE pills (
    id                         uuid         NOT NULL DEFAULT gen_random_uuid(),
    author_id                  uuid         NOT NULL,
    title                      varchar(160) NOT NULL,
    slug                       varchar(180) NOT NULL,
    summary                    varchar(500),
    content                    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    pill_type                  varchar(30)  NOT NULL DEFAULT 'ARTICLE',
    status                     varchar(20)  NOT NULL DEFAULT 'DRAFT',
    estimated_duration_seconds integer      NOT NULL DEFAULT 300,
    published_at               timestamptz,
    version                    bigint       NOT NULL DEFAULT 0,  -- optimistic locking
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    updated_at                 timestamptz  NOT NULL DEFAULT now(),
    search_vector              tsvector GENERATED ALWAYS AS (
        to_tsvector('english', title || ' ' || coalesce(summary, ''))
    ) STORED,

    CONSTRAINT pk_pills PRIMARY KEY (id),
    CONSTRAINT ck_pills_type CHECK (pill_type IN ('ARTICLE', 'QUIZ', 'FLASHCARD', 'VIDEO')),
    CONSTRAINT ck_pills_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_pills_duration_positive CHECK (estimated_duration_seconds > 0),
    CONSTRAINT ck_pills_published_has_timestamp CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE TRIGGER trg_pills_updated_at
    BEFORE UPDATE ON pills
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Read paths:
--   pill-by-slug (public detail page, hottest single-row lookup)
CREATE UNIQUE INDEX ux_pills_slug ON pills (slug);
--   published feed, newest first, keyset pagination:
--     WHERE status = 'PUBLISHED' [AND (published_at, id) < (?, ?)]
--     ORDER BY published_at DESC, id DESC LIMIT n
--   Partial: excludes drafts/archived so the index stays small and hot.
CREATE INDEX ix_pills_published_feed ON pills (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED';
--   author workspace ("my pills", recently edited first)
CREATE INDEX ix_pills_author_updated_at ON pills (author_id, updated_at DESC);
--   curator review queue (oldest submissions first); partial keeps it tiny
CREATE INDEX ix_pills_review_queue ON pills (created_at)
    WHERE status = 'IN_REVIEW';
--   full-text search over title + summary
CREATE INDEX ix_pills_search_vector ON pills USING gin (search_vector);
--   search-as-you-type / fuzzy title matching (ILIKE '%term%')
CREATE INDEX ix_pills_title_trgm ON pills USING gin (title gin_trgm_ops);

-- ── tags ───────────────────────────────────────────────────────────────────
CREATE TABLE tags (
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    name       citext      NOT NULL,  -- case-insensitive: 'Java' == 'java'
    slug       varchar(80) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_tags PRIMARY KEY (id)
);

-- Read paths: tag-by-slug (topic pages) and case-insensitive name lookup
CREATE UNIQUE INDEX ux_tags_slug ON tags (slug);
CREATE UNIQUE INDEX ux_tags_name ON tags (name);

-- ── pill_tags (m:n) ────────────────────────────────────────────────────────
CREATE TABLE pill_tags (
    pill_id uuid NOT NULL,
    tag_id  uuid NOT NULL,

    CONSTRAINT pk_pill_tags PRIMARY KEY (pill_id, tag_id),
    CONSTRAINT fk_pill_tags_pill FOREIGN KEY (pill_id) REFERENCES pills (id) ON DELETE CASCADE,
    CONSTRAINT fk_pill_tags_tag  FOREIGN KEY (tag_id)  REFERENCES tags (id)  ON DELETE RESTRICT
);

-- Read path: "all pills for tag X" — reverse of the PK ordering, so it needs
-- its own composite index; (tag_id, pill_id) makes it covering for the join.
CREATE INDEX ix_pill_tags_tag_pill ON pill_tags (tag_id, pill_id);
