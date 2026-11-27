-- Include the target conversation on chat notifications so the notification
-- opens the correct direct/group conversation and never implies church-wide scope.

alter table public.notifications
    add column conversation_id uuid;

alter table public.notifications
    add constraint notifications_chat_conversation_fkey
    foreign key (organization_id, conversation_id)
    references public.chat_conversations (organization_id, id)
    on delete cascade;

update public.notifications as notification
   set conversation_id = message.conversation_id
  from public.team_messages as message
 where notification.notification_type = 'team_message'
   and notification.team_message_id = message.id
   and notification.organization_id = message.organization_id
   and notification.conversation_id is null;

create index notifications_recipient_conversation_idx
    on public.notifications (recipient_user_id, conversation_id, created_at desc)
    where conversation_id is not null;

create or replace function private.notify_team_message_created()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_sender_name text;
    v_conversation_title text;
    v_recipient record;
begin
    if auth.uid() is null
       or new.sender_id is distinct from auth.uid()
       or not private.is_chat_conversation_member(new.conversation_id) then
        return new;
    end if;

    select coalesce(nullif(btrim(profile.display_name), ''), 'A team member')
      into v_sender_name
      from public.profiles as profile
     where profile.id = new.sender_id;

    select coalesce(conversation.title, 'Direct message')
      into v_conversation_title
      from public.chat_conversations as conversation
     where conversation.id = new.conversation_id;

    v_sender_name := coalesce(v_sender_name, 'A team member');

    for v_recipient in
        select member.user_id
          from public.chat_conversation_members as member
         where member.conversation_id = new.conversation_id
           and member.organization_id = new.organization_id
           and member.user_id <> new.sender_id
    loop
        insert into public.notifications (
            organization_id,
            recipient_user_id,
            notification_type,
            title,
            message,
            team_message_id,
            conversation_id,
            dedupe_key
        ) values (
            new.organization_id,
            v_recipient.user_id,
            'team_message',
            'New message',
            left(v_sender_name, 120) || ' sent a message in ' || left(coalesce(v_conversation_title, 'a conversation'), 80) || '. Open UnityHub to view it.',
            new.id,
            new.conversation_id,
            'team-message:' || new.id::text || ':' || v_recipient.user_id::text
        )
        on conflict (dedupe_key) do nothing;
    end loop;

    return new;
end;
$function$;
