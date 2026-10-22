-- Distributed identifier sequence positions.
--
-- One row per logical sequence (tenant code, application number, artifact code, ...).
-- current_value is the high-water mark already reserved from the database; a running
-- instance may hold a small unissued buffer above it. Reservations are atomic
-- (INSERT ... ON CONFLICT ... RETURNING) and commit in their own transaction, so a
-- business rollback never re-issues a value.

CREATE TABLE id_sequence (
    sequence_key  varchar(192) PRIMARY KEY,
    current_value bigint       NOT NULL,
    step          integer      NOT NULL,
    max_value     bigint       NOT NULL,
    updated_at    timestamp with time zone NOT NULL,
    CONSTRAINT ck_id_sequence_step CHECK (step >= 1),
    CONSTRAINT ck_id_sequence_current CHECK (current_value >= 0),
    CONSTRAINT ck_id_sequence_max CHECK (max_value >= 1)
);

COMMENT ON TABLE id_sequence IS
    'Persisted high-water marks for DCC identifier sequences; values are never reused';
COMMENT ON COLUMN id_sequence.current_value IS
    'Last value reserved from the database (may be ahead of the last value issued)';
COMMENT ON COLUMN id_sequence.max_value IS
    'Inclusive upper bound; the allocator refuses to grow past it';
