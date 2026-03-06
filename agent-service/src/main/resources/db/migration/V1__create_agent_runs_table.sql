CREATE TABLE agent_runs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_message TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    final_reply  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
