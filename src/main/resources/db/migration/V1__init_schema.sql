CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE departments (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(50) UNIQUE NOT NULL,
    label       VARCHAR(100) NOT NULL,
    sensitive   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(50) UNIQUE NOT NULL,
    label       VARCHAR(100) NOT NULL
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role_id         INTEGER NOT NULL REFERENCES roles(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    role_id         INTEGER NOT NULL REFERENCES roles(id),
    department_id   INTEGER NOT NULL REFERENCES departments(id),
    PRIMARY KEY (role_id, department_id)
);

CREATE TABLE demo_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ip_address      VARCHAR(45) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP NOT NULL,
    questions_used  INTEGER NOT NULL DEFAULT 0,
    documents_used  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id   INTEGER REFERENCES departments(id),
    demo_session_id UUID REFERENCES demo_sessions(id) ON DELETE CASCADE,
    filename        VARCHAR(255) NOT NULL,
    title           VARCHAR(255),
    ingested_at     TIMESTAMP NOT NULL DEFAULT now(),
    version         INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT chk_document_scope CHECK (
        (department_id IS NOT NULL AND demo_session_id IS NULL) OR
        (department_id IS NULL AND demo_session_id IS NOT NULL)
    )
);

CREATE TABLE chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    department_id   INTEGER REFERENCES departments(id),
    demo_session_id UUID REFERENCES demo_sessions(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    section_label   VARCHAR(255),
    chunk_index     INTEGER NOT NULL,
    embedding       vector(1536) NOT NULL
);

CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunks_department ON chunks (department_id);
CREATE INDEX idx_chunks_demo_session ON chunks (demo_session_id);
CREATE INDEX idx_demo_sessions_expires ON demo_sessions (expires_at);

CREATE TABLE query_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID REFERENCES users(id),
    demo_session_id         UUID REFERENCES demo_sessions(id),
    question                TEXT NOT NULL,
    department_requested    INTEGER REFERENCES departments(id),
    access_allowed          BOOLEAN NOT NULL,
    refusal_reason          VARCHAR(50),
    answer                  TEXT,
    sources_cited           TEXT[],
    latency_ms              INTEGER,
    tokens_used             INTEGER,
    estimated_cost_usd      NUMERIC(10,6),
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_query_owner CHECK (
        (user_id IS NOT NULL AND demo_session_id IS NULL) OR
        (user_id IS NULL AND demo_session_id IS NOT NULL)
    )
);
