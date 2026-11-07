-- Give signed-in clients narrowly scoped conversation and user-message access.
-- Ownership is enforced by RLS; messages can only be created with role='user'.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.conversations TO authenticated;
GRANT SELECT, INSERT ON TABLE public.messages TO authenticated;

DROP POLICY IF EXISTS zayron_conversations_authenticated_select_own ON public.conversations;
CREATE POLICY zayron_conversations_authenticated_select_own
    ON public.conversations FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS zayron_conversations_authenticated_insert_own ON public.conversations;
CREATE POLICY zayron_conversations_authenticated_insert_own
    ON public.conversations FOR INSERT TO authenticated
    WITH CHECK (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS zayron_conversations_authenticated_update_own ON public.conversations;
CREATE POLICY zayron_conversations_authenticated_update_own
    ON public.conversations FOR UPDATE TO authenticated
    USING (user_id = (SELECT auth.uid()))
    WITH CHECK (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS zayron_conversations_authenticated_delete_own ON public.conversations;
CREATE POLICY zayron_conversations_authenticated_delete_own
    ON public.conversations FOR DELETE TO authenticated
    USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS zayron_messages_authenticated_select_own ON public.messages;
CREATE POLICY zayron_messages_authenticated_select_own
    ON public.messages FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS zayron_messages_authenticated_insert_user_only ON public.messages;
CREATE POLICY zayron_messages_authenticated_insert_user_only
    ON public.messages FOR INSERT TO authenticated
    WITH CHECK (
      user_id = (SELECT auth.uid())
      AND role = 'user'
    );
