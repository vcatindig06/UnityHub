-- Create one in-app/push notification for each other current organization member
-- whenever an authenticated member sends a Team Chat message.
--
-- Notification rows remain private to their recipient under the existing RLS policies.
-- The existing notifications AFTER INSERT trigger dispatches push alerts through FCM.

alter table public.notifications
    add column team_message_id uuid
        references public.team_messages(id) on delete cascade;

alter table public.notifications
    drop constraint if exists notifications_type_check;

alter table public.notifications
    add constraint notifications_type_check
        check (notification_type in (
            'assignment_created',
            'assignment_reminder',
            'assignment_response',
            'rehearsal_assigned',
            'rehearsal_response',
            'team_message'
        ));

create index notifications_team_message_id_idx
    on public.notifications (team_message_id)
    where team_message_id is not null;

create or replace function private.notify_team_message_created()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_sender_name text;
    v_recipient record;
begin
    -- The existing team_messages RLS policy independently enforces the same
    -- authenticated sender and organization-membership rule.
    if auth.uid() is null or new.sender_id is distinct from auth.uid() then
        return new;
    end if;

    if not exists (
        select 1
        from public.organization_members member
        where member.organization_id = new.organization_id
          and member.user_id = new.sender_id
    ) then
        return new;
    end if;

    select coalesce(nullif(btrim(profile.display_name), ''), 'A team member')
      into v_sender_name
      from public.profiles profile
     where profile.id = new.sender_id;

    v_sender_name := coalesce(v_sender_name, 'A team member');

    for v_recipient in
        select member.user_id
        from public.organization_members member
        where member.organization_id = new.organization_id
          and member.user_id <> new.sender_id
    loop
        insert into public.notifications (
            organization_id,
            recipient_user_id,
            notification_type,
            title,
            message,
            team_message_id,
            dedupe_key
        ) values (
            new.organization_id,
            v_recipient.user_id,
            'team_message',
            'New Team Chat message',
            left(v_sender_name, 120) || ' sent a message in Team Chat. Open UnityHub to view it.',
            new.id,
            'team-message:' || new.id::text || ':' || v_recipient.user_id::text
        )
        on conflict (dedupe_key) do nothing;
    end loop;

    return new;
end;
$function$;

revoke all on function private.notify_team_message_created() from public, anon, authenticated;

drop trigger if exists team_message_notification_created on public.team_messages;
create trigger team_message_notification_created
    after insert on public.team_messages
    for each row
    execute function private.notify_team_message_created();
