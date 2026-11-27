-- Older 0.63 clients do not send conversation_id. Keep their messages
-- in the existing organization-wide Everyone channel after the schema upgrade.

create or replace function private.assign_default_team_chat_conversation()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
    if new.conversation_id is null then
        select conversation.id
          into new.conversation_id
          from public.chat_conversations as conversation
         where conversation.organization_id = new.organization_id
           and conversation.conversation_type = 'organization';

        if new.conversation_id is null then
            raise exception 'The organization-wide Everyone conversation is not available.'
                using errcode = '23503';
        end if;
    end if;

    return new;
end;
$function$;

revoke all on function private.assign_default_team_chat_conversation()
    from public, anon, authenticated;

create trigger assign_default_team_chat_conversation
    before insert on public.team_messages
    for each row
    execute function private.assign_default_team_chat_conversation();
