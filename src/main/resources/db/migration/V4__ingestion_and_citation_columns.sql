ALTER TABLE documents ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED';
ALTER TABLE documents ADD COLUMN content_type VARCHAR(100);
ALTER TABLE documents ADD COLUMN size_bytes BIGINT;
ALTER TABLE documents ADD COLUMN page_count INTEGER;
ALTER TABLE documents ADD COLUMN failure_reason VARCHAR(500);

ALTER TABLE chunks ADD COLUMN page_number INTEGER;

CREATE INDEX idx_documents_status ON documents (status);
