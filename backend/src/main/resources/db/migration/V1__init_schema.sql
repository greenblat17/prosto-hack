CREATE TABLE datasets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    table_name  VARCHAR(255) NOT NULL UNIQUE,
    row_count   BIGINT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT now(),
    updated_at  TIMESTAMP DEFAULT now()
);

CREATE TABLE dataset_columns (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id   UUID NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    column_name  VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    field_type   VARCHAR(50) NOT NULL,
    category     VARCHAR(255) DEFAULT 'Общее',
    ordinal      INTEGER NOT NULL,
    UNIQUE(dataset_id, column_name)
);

CREATE INDEX idx_dataset_columns_dataset ON dataset_columns(dataset_id);
