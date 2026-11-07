-- Replace the profile RPC's SECURITY DEFINER execution with caller privileges.
-- Authenticated users receive only SELECT/INSERT on their own profile through RLS.
GRANT SELECT, INSERT ON TABLE public.user_profiles TO authenticated;

DROP POLICY IF EXISTS zayron_profiles_select_authenticated_own ON public.user_profiles;
CREATE POLICY zayron_profiles_select_authenticated_own
    ON public.user_profiles
    FOR SELECT
    TO authenticated
    USING (
        user_id = (SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid)
    );

DROP POLICY IF EXISTS zayron_profiles_insert_authenticated_own ON public.user_profiles;
CREATE POLICY zayron_profiles_insert_authenticated_own
    ON public.user_profiles
    FOR INSERT
    TO authenticated
    WITH CHECK (
        user_id = (SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid)
    );

-- The function is owned by this deliberately restricted NOLOGIN role.
GRANT zayron_profile_rpc_owner TO postgres;
SET LOCAL ROLE zayron_profile_rpc_owner;
ALTER FUNCTION public.zayron_get_my_profile() SECURITY INVOKER;
RESET ROLE;
REVOKE zayron_profile_rpc_owner FROM postgres;
