-- Restrict Data API access and create least-privilege backend roles.
-- The runtime login remains disabled until its password is configured in the
-- selected deployment secret manager.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zayron_api') THEN
        CREATE ROLE zayron_api NOLOGIN NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zayron_worker') THEN
        CREATE ROLE zayron_worker NOLOGIN NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zayron_runtime') THEN
        CREATE ROLE zayron_runtime NOLOGIN NOINHERIT;
    END IF;
END
$$;

ALTER ROLE zayron_api NOLOGIN NOINHERIT;
ALTER ROLE zayron_worker NOLOGIN NOINHERIT;
ALTER ROLE zayron_runtime NOLOGIN NOINHERIT;
GRANT zayron_api, zayron_worker TO zayron_runtime;

-- Application tables are accessed only through the trusted backend.
REVOKE ALL ON ALL TABLES IN SCHEMA public
    FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public
    FROM PUBLIC, anon, authenticated, service_role;

-- Prevent future tables/sequences created by the migration owner from
-- silently regaining broad Data API grants.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated, service_role;

GRANT USAGE ON SCHEMA public TO zayron_api, zayron_worker;
GRANT USAGE ON SCHEMA auth TO zayron_api;
GRANT EXECUTE ON FUNCTION auth.uid() TO zayron_api;

-- User-facing API permissions: only the operations used by the platform API.
GRANT SELECT, INSERT, UPDATE ON public.user_profiles TO zayron_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.conversations TO zayron_api;
GRANT SELECT, INSERT ON public.messages TO zayron_api;
GRANT SELECT ON public.user_entitlements TO zayron_api;
GRANT SELECT ON public.usage_events TO zayron_api;
GRANT SELECT, INSERT, UPDATE ON public.ai_jobs TO zayron_api;

-- Worker permissions are limited to job claiming/completion and event writes.
GRANT SELECT, UPDATE ON public.ai_jobs TO zayron_worker;
GRANT INSERT ON public.ai_job_events TO zayron_worker;

-- RLS is enforced even if ownership changes in a later deployment.
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations FORCE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages FORCE ROW LEVEL SECURITY;
ALTER TABLE public.user_entitlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_entitlements FORCE ROW LEVEL SECURITY;
ALTER TABLE public.usage_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.usage_events FORCE ROW LEVEL SECURITY;
ALTER TABLE public.ai_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_jobs FORCE ROW LEVEL SECURITY;
ALTER TABLE public.ai_job_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_job_events FORCE ROW LEVEL SECURITY;

-- Remove earlier policies if this migration is safely re-run by the
-- repository's independent checksum-based migration runner.
DROP POLICY IF EXISTS zayron_profiles_select_own ON public.user_profiles;
DROP POLICY IF EXISTS zayron_profiles_insert_own ON public.user_profiles;
DROP POLICY IF EXISTS zayron_profiles_update_own ON public.user_profiles;
DROP POLICY IF EXISTS zayron_conversations_select_own ON public.conversations;
DROP POLICY IF EXISTS zayron_conversations_insert_own ON public.conversations;
DROP POLICY IF EXISTS zayron_conversations_update_own ON public.conversations;
DROP POLICY IF EXISTS zayron_conversations_delete_own ON public.conversations;
DROP POLICY IF EXISTS zayron_messages_select_own ON public.messages;
DROP POLICY IF EXISTS zayron_messages_insert_own ON public.messages;
DROP POLICY IF EXISTS zayron_entitlements_select_own ON public.user_entitlements;
DROP POLICY IF EXISTS zayron_usage_select_own ON public.usage_events;
DROP POLICY IF EXISTS zayron_jobs_select_own ON public.ai_jobs;
DROP POLICY IF EXISTS zayron_jobs_insert_own ON public.ai_jobs;
DROP POLICY IF EXISTS zayron_jobs_update_own ON public.ai_jobs;
DROP POLICY IF EXISTS zayron_worker_jobs_select ON public.ai_jobs;
DROP POLICY IF EXISTS zayron_worker_jobs_update ON public.ai_jobs;
DROP POLICY IF EXISTS zayron_worker_events_insert ON public.ai_job_events;

CREATE POLICY zayron_profiles_select_own ON public.user_profiles
    FOR SELECT TO zayron_api USING ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_profiles_insert_own ON public.user_profiles
    FOR INSERT TO zayron_api WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_profiles_update_own ON public.user_profiles
    FOR UPDATE TO zayron_api
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);

CREATE POLICY zayron_conversations_select_own ON public.conversations
    FOR SELECT TO zayron_api USING ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_conversations_insert_own ON public.conversations
    FOR INSERT TO zayron_api WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_conversations_update_own ON public.conversations
    FOR UPDATE TO zayron_api
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_conversations_delete_own ON public.conversations
    FOR DELETE TO zayron_api USING ((SELECT auth.uid()) = user_id);

CREATE POLICY zayron_messages_select_own ON public.messages
    FOR SELECT TO zayron_api USING ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_messages_insert_own ON public.messages
    FOR INSERT TO zayron_api WITH CHECK ((SELECT auth.uid()) = user_id);

CREATE POLICY zayron_entitlements_select_own ON public.user_entitlements
    FOR SELECT TO zayron_api USING ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_usage_select_own ON public.usage_events
    FOR SELECT TO zayron_api USING ((SELECT auth.uid()) = user_id);

CREATE POLICY zayron_jobs_select_own ON public.ai_jobs
    FOR SELECT TO zayron_api USING ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_jobs_insert_own ON public.ai_jobs
    FOR INSERT TO zayron_api WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY zayron_jobs_update_own ON public.ai_jobs
    FOR UPDATE TO zayron_api
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);

CREATE POLICY zayron_worker_jobs_select ON public.ai_jobs
    FOR SELECT TO zayron_worker USING (true);
CREATE POLICY zayron_worker_jobs_update ON public.ai_jobs
    FOR UPDATE TO zayron_worker USING (true) WITH CHECK (true);
CREATE POLICY zayron_worker_events_insert ON public.ai_job_events
    FOR INSERT TO zayron_worker WITH CHECK (true);
