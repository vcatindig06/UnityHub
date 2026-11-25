-- Keep the Team Chat unread badge synchronized with the member's monotonic
-- read cursor, including messages outside the currently loaded 50-message page.
--
-- This is additive and keeps the earlier message-ID-array RPC available for
-- older installed app versions during rollout.
create or replace function public.mark_team_chat_notifications_read_through(
    p_organization_id uuid,
    p_through_message_id uuid
)
returns integer
language plpgsql
security invoker
set search_path = ''
as $function$
declare
    v_through_created_at timestamptz;
    v_updated_count integer;
begin
    if auth.uid() is null or not private.is_org_member(p_organization_id) then
        raise exception 'You must be a current member of this organization.'
            using errcode = '42501';
    end if;

    select message.created_at
      into v_through_created_at
      from public.team_messages as message
     where message.organization_id = p_organization_id
       and message.id = p_through_message_id;

    if not found then
        raise exception 'The read cursor must point to a message in this organization.'
            using errcode = '23503';
    end if;

    update public.notifications as notification
       set read_at = now()
     where notification.organization_id = p_organization_id
       and notification.recipient_user_id = (select auth.uid())
       and notification.notification_type = 'team_message'
       and notification.read_at is null
       and exists (
           select 1
             from public.team_messages as message
            where message.organization_id = p_organization_id
              and message.id = notification.team_message_id
              and (message.created_at, message.id)
                  <= (v_through_created_at, p_through_message_id)
       );

    get diagnostics v_updated_count = row_count;
    return v_updated_count;
end;
$function$;

revoke all on function public.mark_team_chat_notifications_read_through(uuid, uuid)
    from public, anon;
grant execute on function public.mark_team_chat_notifications_read_through(uuid, uuid)
    to authenticated;
