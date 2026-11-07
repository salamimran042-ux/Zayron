-- Fix the profile RPC conflict target explicitly: RETURNS TABLE declares
-- an output variable named user_id, so an unqualified conflict column is ambiguous.
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
    v_user_id := auth.uid();

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

REVOKE ALL ON FUNCTION public.zayron_get_my_profile() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.zayron_get_my_profile() TO authenticated;
