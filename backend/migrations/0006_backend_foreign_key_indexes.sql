-- Add leading-column indexes for foreign-key lookups and cascade checks.
CREATE INDEX IF NOT EXISTS messages_conversation_owner_idx
    ON public.messages (conversation_id, user_id);

CREATE INDEX IF NOT EXISTS ai_job_events_job_id_idx
    ON public.ai_job_events (job_id);
