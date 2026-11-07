-- Use Supabase's init-plan-friendly auth.uid() policy pattern.
-- The profile RPC now runs as SECURITY INVOKER, so the legacy owner policies are unused.
DROP POLICY IF EXISTS zayron_profile_rpc_insert_own ON public.user_profiles;
DROP POLICY IF EXISTS zayron_profile_rpc_select_own ON public.user_profiles;

DROP POLICY IF EXISTS zayron_profiles_select_authenticated_own ON public.user_profiles;
CREATE POLICY zayron_profiles_select_authenticated_own
    ON public.user_profiles
    FOR SELECT
    TO authenticated
    USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS zayron_profiles_insert_authenticated_own ON public.user_profiles;
CREATE POLICY zayron_profiles_insert_authenticated_own
    ON public.user_profiles
    FOR INSERT
    TO authenticated
    WITH CHECK (user_id = (SELECT auth.uid()));

-- The API service role keeps its existing independent policies and grants.
