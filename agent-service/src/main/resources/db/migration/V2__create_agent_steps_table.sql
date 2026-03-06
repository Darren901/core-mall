CREATE TABLE agent_steps (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id     UUID NOT NULL REFERENCES agent_runs(id),
    tool_name  VARCHAR(100) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    payload    TEXT,
    error      TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_steps_run_id ON agent_steps(run_id);
