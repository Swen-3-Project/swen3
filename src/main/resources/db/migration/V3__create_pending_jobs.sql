CREATE TABLE pending_jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL
);
-- No document FK: file cleanup must survive deletion of its document.
CREATE INDEX pending_jobs_due ON pending_jobs (state, next_attempt_at);
CREATE INDEX pending_jobs_document ON pending_jobs (document_id);
