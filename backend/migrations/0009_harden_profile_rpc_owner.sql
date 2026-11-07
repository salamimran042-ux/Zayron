-- Give the SECURITY DEFINER profile RPC a dedicated, non-login, non-bypass
-- owner. The function owner can only read/insert a user's own profile under RLS.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'zayron_profile_rpc_owner'
    ) THEN
        CREATE ROLE zayron_profile_rpc_owner NOLOGIN NOINHERIT NOBYPASSRLS;
    END IF;
END
$$;

ALTER ROLE zayron_profile_rpc_owner NOLOGIN NOINHERIT NOBYPASSRLS;
GRANT USAGE ON SCHEMA public, auth TO zayron_profile_rpc_owner;
GRANT EXECUTE ON FUNCTION auth.uid() TO zayron_profile_rpc_owner;
GRANT SELECT, INSERT ON public.user_profiles TO zayron_profile_rpc_owner;

DROP POLICY IF EXISTS zayron_profile_rpc_select_own ON public.user_profiles;
DROP POLICY IF EXISTS zayron_profile_rpc_insert_own ON public.user_profiles;

CREATE POLICY zayron_profile_rpc_select_own ON public.user_profiles
    FOR SELECT TO zayron_profile_rpc_owner
    USING ((SELECT auth.uid()) = user_id);

CREATE POLICY zayron_profile_rpc_insert_own ON public.user_profiles
    FOR INSERT TO zayron_profile_rpc_owner
    WITH CHECK ((SELECT auth.uid()) = user_id);

-- PostgreSQL requires CREATE on the containing schema to transfer function
-- ownership. Remove that temporary privilege immediately afterward.
GRANT CREATE ON SCHEMA public TO zayron_profile_rpc_owner;
GRANT zayron_profile_rpc_owner TO postgres;
ALTER FUNCTION public.zayron_get_my_profile()
    OWNER TO zayron_profile_rpc_owner;
REVOKE zayron_profile_rpc_owner FROM postgres;
REVOKE CREATE ON SCHEMA public FROM zayron_profile_rpc_owner;
