CREATE TABLE document_reminders (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX document_reminders_document_id_idx ON document_reminders(document_id);

CREATE TABLE reminder_history (
    id UUID PRIMARY KEY,
    reminder_id UUID NOT NULL REFERENCES document_reminders(id) ON DELETE CASCADE,
    event VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX reminder_history_reminder_id_idx ON reminder_history(reminder_id);
