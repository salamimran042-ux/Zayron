-- Private Zayron attachments are scoped to the authenticated user's UUID folder.
-- The bucket itself is provisioned separately through Supabase Storage API/Dashboard.
create policy "zayron_attachment_owner_insert"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'zayron-attachments'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

create policy "zayron_attachment_owner_select"
on storage.objects
for select
to authenticated
using (
  bucket_id = 'zayron-attachments'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

create policy "zayron_attachment_owner_delete"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'zayron-attachments'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);
