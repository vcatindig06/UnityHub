CREATE OR REPLACE FUNCTION private.guard_library_files_not_used_as_chat_attachments()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
BEGIN
    IF NEW.attachment_id IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM public.chat_shared_files AS file
           WHERE file.id = NEW.attachment_id
             AND file.conversation_id IS NULL
       ) THEN
        RAISE EXCEPTION 'Organization shared-library files cannot be attached directly to chat messages.'
            USING ERRCODE = '42501';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER team_message_library_file_attachment_guard
BEFORE INSERT OR UPDATE OF attachment_id
ON public.team_messages
FOR EACH ROW
EXECUTE FUNCTION private.guard_library_files_not_used_as_chat_attachments();
