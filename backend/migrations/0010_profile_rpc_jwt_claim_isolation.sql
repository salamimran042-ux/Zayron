-- Keep the restricted SECURITY DEFINER owner independent of the protected
-- auth schema. PostgREST sets this transaction-local claim from the verified JWT.
DROP POLICY IF EXISTS zayron_profile_rpc_select_own ON public.user_profiles;
DROP POLICY IF EXISTS zayron_profile_rpc_insert_own ON public.user_profiles;

CREATE POLICY zayron_profile_rpc_select_own ON public.user_profiles
    FOR SELECT TO zayron_profile_rpc_owner
    USING (
        user_id = NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid
    );

CREATE POLICY zayron_profile_rpc_insert_own ON public.user_profiles
    FOR INSERT TO zayron_profile_rpc_owner
    WITH CHECK (
        user_id = NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid
    );

GRANT CREATE ON SCHEMA public TO zayron_profile_rpc_owner;
GRANT zayron_profile_rpc_owner TO postgres;
SET LOCAL ROLE zayron_profile_rpc_owner;

CREATE OR REPLACE FUNCTION public.zayron_get_my_profile()
RETURNS TABLE (
    user_id uuid,
    display_name text,
    locale text,
    timezone text,
    created_at timestamptz,
    updated_at timestamptz
)
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    v_user_id uuid;
BEGIN
    v_user_id :=
        NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'authentication required'
            USING ERRCODE = '28000';
    END IF;

    INSERT INTO public.user_profiles (user_id)
    VALUES (v_user_id)
    ON CONFLICT ON CONSTRAINT user_profiles_pkey DO NOTHING;

    RETURN QUERY
    SELECT p.user_id, p.display_name, p.locale, p.timezone,
           p.created_at, p.updated_at
    FROM public.user_profiles AS p
    WHERE p.user_id = v_user_id;
END;
$function$;

RESET ROLE;
REVOKE CREATE ON SCHEMA public FROM zayron_profile_rpc_owner;
REVOKE zayron_profile_rpc_owner FROM postgres;
REVOKE ALL ON FUNCTION public.zayron_get_my_profile() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.zayron_get_my_profile() TO authenticated;
