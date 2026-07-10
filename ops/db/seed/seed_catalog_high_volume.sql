-- codepill_catalog — high-volume seed (LOCAL ONLY, opt-in)
--
--   docker compose --profile seed up db-seed
--
-- Loads 50 tags, 100,000 pills (~80% published) and ~3 tags per pill so the
-- read-path indexes can be validated under realistic cardinality. Idempotent:
-- deterministic slugs + ON CONFLICT DO NOTHING make reruns no-ops.
-- Kept out of Flyway history on purpose — this is test data, not schema.

\timing on

INSERT INTO tags (name, slug)
SELECT 'Seed Topic ' || i, 'seed-topic-' || lpad(i::text, 2, '0')
FROM generate_series(1, 50) AS i
ON CONFLICT (slug) DO NOTHING;

INSERT INTO pills (author_id, title, slug, summary, content, pill_type, status,
                   estimated_duration_seconds, published_at, created_at)
SELECT
    -- 200 synthetic authors, stable UUIDs
    ('00000000-0000-4000-8000-' || lpad(((i % 200) + 1)::text, 12, '0'))::uuid,
    'Seed Pill ' || i || ': '
        || (ARRAY['Spring Boot', 'PostgreSQL', 'React', 'Kubernetes',
                  'OAuth2', 'Kafka', 'Testing', 'Observability'])[1 + i % 8]
        || ' in 5 minutes',
    'seed-pill-' || lpad(i::text, 6, '0'),
    'Auto-generated microlearning pill #' || i || ' for local load simulation.',
    jsonb_build_object('blocks', jsonb_build_array(
        jsonb_build_object('type', 'text', 'body', 'Generated content for load testing.'))),
    (ARRAY['ARTICLE', 'QUIZ', 'FLASHCARD', 'VIDEO'])[1 + i % 4],
    CASE WHEN i % 10 < 8 THEN 'PUBLISHED'
         WHEN i % 10 = 8 THEN 'DRAFT'
         ELSE 'IN_REVIEW' END,
    60 + (i % 540),
    CASE WHEN i % 10 < 8 THEN now() - (i || ' minutes')::interval END,
    now() - (i || ' minutes')::interval
FROM generate_series(1, 100000) AS i
ON CONFLICT (slug) DO NOTHING;

-- ~3 deterministic pseudo-random tags per seeded pill
INSERT INTO pill_tags (pill_id, tag_id)
SELECT p.id, t.id
FROM pills p
CROSS JOIN LATERAL (
    SELECT id FROM tags
    WHERE slug LIKE 'seed-topic-%'
    ORDER BY md5(p.slug || id::text)
    LIMIT 3
) t
WHERE p.slug LIKE 'seed-pill-%'
ON CONFLICT DO NOTHING;

ANALYZE tags;
ANALYZE pills;
ANALYZE pill_tags;

-- Sanity: these should all report Index Scan / Bitmap Index Scan, never a
-- Seq Scan on pills.
--
-- Feed (keyset):    EXPLAIN ANALYZE SELECT id, title FROM pills
--                   WHERE status = 'PUBLISHED'
--                   ORDER BY published_at DESC, id DESC LIMIT 20;
-- By slug:          EXPLAIN ANALYZE SELECT * FROM pills WHERE slug = 'seed-pill-042000';
-- By tag:           EXPLAIN ANALYZE SELECT p.id, p.title FROM pills p
--                   JOIN pill_tags pt ON pt.pill_id = p.id
--                   JOIN tags t ON t.id = pt.tag_id
--                   WHERE t.slug = 'seed-topic-07' AND p.status = 'PUBLISHED' LIMIT 20;
-- Full-text:        EXPLAIN ANALYZE SELECT id, title FROM pills
--                   WHERE search_vector @@ plainto_tsquery('english', 'kubernetes') LIMIT 20;

SELECT status, count(*) FROM pills GROUP BY status ORDER BY status;
