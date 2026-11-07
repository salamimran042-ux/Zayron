ALTER TABLE public.ai_jobs
    ADD COLUMN IF NOT EXISTS lease_owner text,
    ADD COLUMN IF NOT EXISTS lease_expires_at timestamptz,
    ADD COLUMN IF NOT EXISTS cancel_requested boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS ai_jobs_expired_lease_idx
    ON public.ai_jobs (lease_expires_at)
    WHERE status = 'running';
