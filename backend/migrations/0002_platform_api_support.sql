ALTER TABLE public.ai_jobs
    ADD COLUMN IF NOT EXISTS idempotency_key text;

CREATE UNIQUE INDEX IF NOT EXISTS ai_jobs_user_idempotency_idx
    ON public.ai_jobs (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.ai_job_events (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES public.ai_jobs(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    event_type text NOT NULL CHECK (event_type IN
        ('queued', 'started', 'retry_scheduled', 'succeeded', 'failed', 'cancelled')),
    detail jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ai_job_events_user_job_idx
    ON public.ai_job_events (user_id, job_id, created_at);
