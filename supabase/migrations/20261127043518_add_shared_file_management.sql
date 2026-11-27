REVOKE UPDATE ON TABLE public.chat_shared_files FROM PUBLIC, anon, authenticated;
GRANT UPDATE (file_name, category) ON TABLE public.chat_shared_files TO authenticated;

CREATE POLICY chat_shared_files_update_own_library
ON public.chat_shared_files
FOR UPDATE
TO authenticated
USING (
    uploader_id = (SELECT auth.uid())
    AND conversation_id IS NULL
    AND shared_to_team
    AND EXISTS (
        SELECT 1
        FROM public.organization_members AS membership
        WHERE membership.organization_id = chat_shared_files.organization_id
          AND membership.user_id = (SELECT auth.uid())
    )
)
WITH CHECK (
    uploader_id = (SELECT auth.uid())
    AND conversation_id IS NULL
    AND shared_to_team
    AND EXISTS (
        SELECT 1
        FROM public.organization_members AS membership
        WHERE membership.organization_id = chat_shared_files.organization_id
          AND membership.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY chat_attachments_delete_own_library
ON storage.objects
FOR DELETE
TO authenticated
USING (
    bucket_id = 'chat-attachments'
    AND (storage.foldername(name))[2] = 'library'
    AND (storage.foldername(name))[3] = (SELECT auth.uid())::text
    AND EXISTS (
        SELECT 1
        FROM public.organization_members AS membership
        WHERE membership.organization_id::text = (storage.foldername(objects.name))[1]
          AND membership.user_id = (SELECT auth.uid())
    )
);
