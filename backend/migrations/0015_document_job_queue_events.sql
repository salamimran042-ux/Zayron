-- Allow the authenticated API role to append an immutable queue event
-- for jobs belonging to the current verified user. Worker event writes remain
-- separately scoped to the internal worker role.
GRANT INSERT ON public.ai_job_events TO zayron_api;

DROP POLICY IF EXISTS zayron_api_job_events_insert_own ON public.ai_job_events;
CREATE POLICY zayron_api_job_events_insert_own ON public.ai_job_events
    FOR INSERT TO zayron_api
    WITH CHECK (
        (SELECT auth.uid()) = user_id
        AND EXISTS (
            SELECT 1
            FROM public.ai_jobs AS job
            WHERE job.id = ai_job_events.job_id
              AND job.user_id = (SELECT auth.uid())
        )
    );
