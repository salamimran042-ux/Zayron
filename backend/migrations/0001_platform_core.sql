CREATE TABLE IF NOT EXISTS public.user_profiles (
    user_id uuid PRIMARY KEY,
    display_name text,
    locale text NOT NULL DEFAULT 'en',
    timezone text NOT NULL DEFAULT 'UTC',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.conversations (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    title text NOT NULL DEFAULT '',
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz,
    CONSTRAINT conversations_id_user_id_unique UNIQUE (id, user_id)
);
CREATE INDEX IF NOT EXISTS conversations_user_updated_idx
    ON public.conversations (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS public.messages (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role text NOT NULL CHECK (role IN ('system', 'user', 'assistant', 'tool')),
    content text NOT NULL,
    status text NOT NULL DEFAULT 'complete'
        CHECK (status IN ('pending', 'streaming', 'complete', 'failed', 'cancelled')),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT messages_conversation_owner_fk
        FOREIGN KEY (conversation_id, user_id)
        REFERENCES public.conversations (id, user_id) ON DELETE CASCADE,
    CONSTRAINT messages_id_conversation_owner_unique
        UNIQUE (id, conversation_id, user_id)
);
CREATE INDEX IF NOT EXISTS messages_conversation_created_idx
    ON public.messages (conversation_id, created_at, id);
CREATE INDEX IF NOT EXISTS messages_user_created_idx
    ON public.messages (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.user_entitlements (
    user_id uuid PRIMARY KEY,
    plan_code text NOT NULL DEFAULT 'free',
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'grace_period', 'expired', 'cancelled')),
    source text NOT NULL DEFAULT 'system',
    limits jsonb NOT NULL DEFAULT '{}'::jsonb,
    period_started_at timestamptz,
    period_ends_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.usage_events (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    feature_code text NOT NULL,
    provider_code text,
    model_code text,
    input_tokens bigint NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    output_tokens bigint NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    units numeric(18, 6) NOT NULL DEFAULT 0 CHECK (units >= 0),
    estimated_cost_usd numeric(18, 8) CHECK (estimated_cost_usd IS NULL OR estimated_cost_usd >= 0),
    idempotency_key text,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS usage_events_user_created_idx
    ON public.usage_events (user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS usage_events_user_idempotency_idx
    ON public.usage_events (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.ai_jobs (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    job_type text NOT NULL,
    status text NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
    input_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_payload jsonb,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts integer NOT NULL DEFAULT 3 CHECK (max_attempts > 0),
    available_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    finished_at timestamptz,
    error_code text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ai_jobs_queue_idx
    ON public.ai_jobs (available_at, created_at)
    WHERE status = 'queued';
CREATE INDEX IF NOT EXISTS ai_jobs_user_created_idx
    ON public.ai_jobs (user_id, created_at DESC);
