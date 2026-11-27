-- Conversation-scoped Team Chat. Existing team_messages are moved into a
-- per-organization "Everyone" conversation without changing message IDs/content.

create table public.chat_conversations (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    conversation_type text not null
        check (conversation_type in ('organization', 'direct', 'group')),
    title text,
    direct_key text,
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chat_conversations_title_length
        check (title is null or char_length(btrim(title)) between 2 and 80),
    constraint chat_conversations_type_key_consistency
        check (
            (conversation_type = 'direct' and direct_key is not null and title is null)
            or (conversation_type <> 'direct' and direct_key is null)
        ),
    constraint chat_conversations_org_id_unique unique (organization_id, id)
);

create unique index chat_conversations_one_org_channel
    on public.chat_conversations (organization_id)
    where conversation_type = 'organization';

create unique index chat_conversations_direct_pair_unique
    on public.chat_conversations (organization_id, direct_key)
    where direct_key is not null;

create index chat_conversations_org_updated_idx
    on public.chat_conversations (organization_id, updated_at desc);

create table public.chat_conversation_members (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null,
    conversation_id uuid not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    member_role text not null default 'member'
        check (member_role in ('owner', 'member')),
    joined_at timestamptz not null default now(),
    constraint chat_conversation_members_conversation_fkey
        foreign key (organization_id, conversation_id)
        references public.chat_conversations (organization_id, id)
        on delete cascade,
    constraint chat_conversation_members_one_user
        unique (organization_id, conversation_id, user_id)
);

create index chat_conversation_members_user_idx
    on public.chat_conversation_members (organization_id, user_id, conversation_id);

-- The helper is private and SECURITY DEFINER so membership checks do not
-- recurse through chat_conversation_members RLS.
create or replace function private.is_chat_conversation_member(p_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select exists (
        select 1
          from public.chat_conversation_members as member
         where member.conversation_id = p_conversation_id
           and member.organization_id = (
                select conversation.organization_id
                  from public.chat_conversations as conversation
                 where conversation.id = p_conversation_id
           )
           and member.user_id = (select auth.uid())
    );
$function$;

revoke all on function private.is_chat_conversation_member(uuid)
    from public, anon;
grant execute on function private.is_chat_conversation_member(uuid)
    to authenticated;

alter table public.chat_conversations enable row level security;
alter table public.chat_conversation_members enable row level security;

create policy chat_conversations_select_members
    on public.chat_conversations
    for select to authenticated
    using (private.is_chat_conversation_member(id));

create policy chat_conversation_members_select_members
    on public.chat_conversation_members
    for select to authenticated
    using (private.is_chat_conversation_member(conversation_id));

revoke all on table public.chat_conversations from public, anon, authenticated;
grant select on table public.chat_conversations to authenticated;

revoke all on table public.chat_conversation_members from public, anon, authenticated;
grant select on table public.chat_conversation_members to authenticated;

-- Create the default Everyone channel for every existing organization.
insert into public.chat_conversations (organization_id, conversation_type, title)
select organization.id, 'organization', 'Everyone'
  from public.organizations as organization
on conflict (organization_id) where conversation_type = 'organization'
do nothing;

-- Every current member receives membership in that default channel.
insert into public.chat_conversation_members (
    organization_id, conversation_id, user_id, member_role
)
select membership.organization_id, conversation.id, membership.user_id, 'member'
  from public.organization_members as membership
  join public.chat_conversations as conversation
    on conversation.organization_id = membership.organization_id
   and conversation.conversation_type = 'organization'
on conflict (organization_id, conversation_id, user_id) do nothing;

-- Keep the Everyone channel in sync with future organization membership.
create or replace function private.sync_organization_chat_membership()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_conversation_id uuid;
begin
    if TG_OP = 'INSERT' then
        insert into public.chat_conversations (organization_id, conversation_type, title)
        values (new.organization_id, 'organization', 'Everyone')
        on conflict (organization_id) where conversation_type = 'organization'
        do nothing;

        select conversation.id
          into v_conversation_id
          from public.chat_conversations as conversation
         where conversation.organization_id = new.organization_id
           and conversation.conversation_type = 'organization';

        insert into public.chat_conversation_members (
            organization_id, conversation_id, user_id, member_role
        ) values (
            new.organization_id, v_conversation_id, new.user_id, 'member'
        )
        on conflict (organization_id, conversation_id, user_id) do nothing;

        return new;
    end if;

    delete from public.chat_conversation_members
     where organization_id = old.organization_id
       and user_id = old.user_id;

    return old;
end;
$function$;

revoke all on function private.sync_organization_chat_membership()
    from public, anon, authenticated;

create trigger sync_organization_chat_membership
    after insert or delete on public.organization_members
    for each row
    execute function private.sync_organization_chat_membership();

-- Migrate existing messages without changing their IDs or content.
-- Temporarily remove the old update guard during the schema backfill; it is
-- recreated below with the stricter conversation-aware rules.
drop trigger if exists team_chat_message_update_guard on public.team_messages;

alter table public.team_messages
    add column conversation_id uuid;

update public.team_messages as message
   set conversation_id = conversation.id
  from public.chat_conversations as conversation
 where conversation.organization_id = message.organization_id
   and conversation.conversation_type = 'organization'
   and message.conversation_id is null;

alter table public.team_messages
    alter column conversation_id set not null;

alter table public.team_messages
    add constraint team_messages_conversation_fkey
    foreign key (organization_id, conversation_id)
    references public.chat_conversations (organization_id, id)
    on delete cascade;

alter table public.team_messages
    add constraint team_messages_org_conversation_id_unique
    unique (organization_id, conversation_id, id);

create index team_messages_conversation_page_idx
    on public.team_messages (conversation_id, created_at desc, id desc);

-- All conversations receive their own read cursor. This avoids sharing a
-- single organization's read cursor between DMs, groups, and Everyone.
create table public.chat_conversation_read_states (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null,
    conversation_id uuid not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    last_read_message_id uuid not null,
    last_read_at timestamptz not null default now(),
    constraint chat_conversation_read_states_member_fkey
        foreign key (organization_id, conversation_id, user_id)
        references public.chat_conversation_members (organization_id, conversation_id, user_id)
        on delete cascade,
    constraint chat_conversation_read_states_message_fkey
        foreign key (organization_id, conversation_id, last_read_message_id)
        references public.team_messages (organization_id, conversation_id, id)
        on delete cascade,
    constraint chat_conversation_read_states_one_per_member
        unique (organization_id, conversation_id, user_id)
);

create index chat_conversation_read_states_lookup_idx
    on public.chat_conversation_read_states (conversation_id, user_id);

alter table public.chat_conversation_read_states enable row level security;

create policy chat_conversation_read_states_select_members
    on public.chat_conversation_read_states
    for select to authenticated
    using (private.is_chat_conversation_member(conversation_id));

create policy chat_conversation_read_states_insert_own
    on public.chat_conversation_read_states
    for insert to authenticated
    with check (
        user_id = (select auth.uid())
        and private.is_chat_conversation_member(conversation_id)
    );

create policy chat_conversation_read_states_update_own
    on public.chat_conversation_read_states
    for update to authenticated
    using (
        user_id = (select auth.uid())
        and private.is_chat_conversation_member(conversation_id)
    )
    with check (
        user_id = (select auth.uid())
        and private.is_chat_conversation_member(conversation_id)
    );

revoke all on table public.chat_conversation_read_states from public, anon, authenticated;
grant select, insert, update on table public.chat_conversation_read_states to authenticated;

-- Message, reaction, and read APIs are now scoped to a conversation.
drop policy if exists team_messages_select_current_members on public.team_messages;
create policy team_messages_select_conversation_members
    on public.team_messages
    for select to authenticated
    using (private.is_chat_conversation_member(conversation_id));

drop policy if exists team_messages_insert_current_members on public.team_messages;
create policy team_messages_insert_conversation_members
    on public.team_messages
    for insert to authenticated
    with check (
        sender_id = (select auth.uid())
        and private.is_chat_conversation_member(conversation_id)
    );

drop policy if exists team_messages_update_current_members on public.team_messages;
create policy team_messages_update_conversation_members
    on public.team_messages
    for update to authenticated
    using (private.is_chat_conversation_member(conversation_id))
    with check (private.is_chat_conversation_member(conversation_id));

drop policy if exists team_message_reactions_select_members on public.team_message_reactions;
create policy team_message_reactions_select_conversation_members
    on public.team_message_reactions
    for select to authenticated
    using (
        exists (
            select 1
              from public.team_messages as message
             where message.id = team_message_reactions.message_id
               and message.organization_id = team_message_reactions.organization_id
               and private.is_chat_conversation_member(message.conversation_id)
        )
    );

drop policy if exists team_message_reactions_insert_own on public.team_message_reactions;
create policy team_message_reactions_insert_conversation_members
    on public.team_message_reactions
    for insert to authenticated
    with check (
        user_id = (select auth.uid())
        and exists (
            select 1
              from public.team_messages as message
             where message.id = team_message_reactions.message_id
               and message.organization_id = team_message_reactions.organization_id
               and private.is_chat_conversation_member(message.conversation_id)
        )
    );

drop policy if exists team_message_reactions_delete_own on public.team_message_reactions;
create policy team_message_reactions_delete_conversation_members
    on public.team_message_reactions
    for delete to authenticated
    using (
        user_id = (select auth.uid())
        and exists (
            select 1
              from public.team_messages as message
             where message.id = team_message_reactions.message_id
               and message.organization_id = team_message_reactions.organization_id
               and private.is_chat_conversation_member(message.conversation_id)
        )
    );

-- Harden update paths so a row cannot be moved to another conversation.
create or replace function private.guard_team_chat_message_update()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
    if auth.uid() is null
       or not private.is_chat_conversation_member(old.conversation_id)
       or new.organization_id is distinct from old.organization_id
       or new.conversation_id is distinct from old.conversation_id
       or new.sender_id is distinct from old.sender_id
       or new.created_at is distinct from old.created_at
       or new.reply_to_message_id is distinct from old.reply_to_message_id then
        raise exception 'A Team Chat message cannot be reassigned or changed outside its conversation.'
            using errcode = '42501';
    end if;

    if new.content is distinct from old.content then
        if old.deleted_at is not null
           or (
               old.sender_id <> auth.uid()
               and not (
                   old.deleted_at is null
                   and new.deleted_at is not null
                   and private.has_org_role(old.organization_id, array['admin', 'worship_leader']::text[])
               )
           ) then
            raise exception 'Only the message author can edit an active message.'
                using errcode = '42501';
        end if;
    end if;

    if new.edited_at is distinct from old.edited_at
       and old.sender_id <> auth.uid() then
        raise exception 'Only the message author can change the edit timestamp.'
            using errcode = '42501';
    end if;

    if new.deleted_at is distinct from old.deleted_at then
        if old.deleted_at is not null
           or new.deleted_at is null
           or (
               old.sender_id <> auth.uid()
               and not private.has_org_role(old.organization_id, array['admin', 'worship_leader']::text[])
           ) then
            raise exception 'Only the author or a team leader can delete this message.'
                using errcode = '42501';
        end if;
    end if;

    if new.pinned_at is distinct from old.pinned_at
       or new.pinned_by is distinct from old.pinned_by then
        if new.pinned_at is null then
            if old.pinned_by <> auth.uid()
               and not private.has_org_role(old.organization_id, array['admin', 'worship_leader']::text[]) then
                raise exception 'Only the pinner or a team leader can unpin this message.'
                    using errcode = '42501';
            end if;
        else
            if old.pinned_at is not null and old.pinned_by <> auth.uid() then
                raise exception 'This message is already pinned by another member.'
                    using errcode = '42501';
            end if;
            if new.pinned_by <> auth.uid() then
                raise exception 'A member can only pin a message as themselves.'
                    using errcode = '42501';
            end if;
        end if;
    end if;

    return new;
end;
$function$;

create trigger team_chat_message_update_guard
    before update on public.team_messages
    for each row execute function private.guard_team_chat_message_update();

-- Message notification recipients must belong to that conversation (not merely
-- to the wider organization), otherwise a DM would notify the whole church.
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
            dedupe_key
        ) values (
            new.organization_id,
            v_recipient.user_id,
            'team_message',
            'New message',
            left(v_sender_name, 120) || ' sent a message in ' || left(coalesce(v_conversation_title, 'a conversation'), 80) || '. Open UnityHub to view it.',
            new.id,
            'team-message:' || new.id::text || ':' || v_recipient.user_id::text
        )
        on conflict (dedupe_key) do nothing;
    end loop;

    return new;
end;
$function$;

create or replace function private.bump_chat_conversation_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
    update public.chat_conversations
       set updated_at = new.created_at
     where id = new.conversation_id
       and organization_id = new.organization_id;
    return new;
end;
$function$;

revoke all on function private.bump_chat_conversation_activity()
    from public, anon, authenticated;

create trigger bump_chat_conversation_activity
    after insert on public.team_messages
    for each row execute function private.bump_chat_conversation_activity();

-- Called by the authenticated app to find/insert a stable one-to-one thread.
create or replace function public.get_or_create_direct_chat(
    p_organization_id uuid,
    p_other_user_id uuid
)
returns table (conversation_id uuid)
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_current_user uuid := (select auth.uid());
    v_direct_key text;
    v_conversation_id uuid;
begin
    if v_current_user is null
       or p_other_user_id is null
       or p_other_user_id = v_current_user
       or not private.is_org_member(p_organization_id)
       or not exists (
            select 1 from public.organization_members as member
             where member.organization_id = p_organization_id
               and member.user_id = p_other_user_id
       ) then
        raise exception 'Choose another current member of this organization.'
            using errcode = '42501';
    end if;

    v_direct_key := least(v_current_user::text, p_other_user_id::text)
        || ':' || greatest(v_current_user::text, p_other_user_id::text);

    insert into public.chat_conversations (
        organization_id, conversation_type, direct_key, created_by
    ) values (
        p_organization_id, 'direct', v_direct_key, v_current_user
    )
    on conflict (organization_id, direct_key) where direct_key is not null
    do nothing;

    select conversation.id into v_conversation_id
      from public.chat_conversations as conversation
     where conversation.organization_id = p_organization_id
       and conversation.direct_key = v_direct_key;

    insert into public.chat_conversation_members (
        organization_id, conversation_id, user_id, member_role
    ) values
        (p_organization_id, v_conversation_id, v_current_user, 'owner'),
        (p_organization_id, v_conversation_id, p_other_user_id, 'member')
    on conflict (organization_id, conversation_id, user_id) do nothing;

    return query select v_conversation_id;
end;
$function$;

revoke all on function public.get_or_create_direct_chat(uuid, uuid)
    from public, anon;
grant execute on function public.get_or_create_direct_chat(uuid, uuid)
    to authenticated;

-- Create a named ministry/custom group with explicit, organization-validated
-- participants. The creator is always included and becomes the group owner.
create or replace function public.create_group_chat(
    p_organization_id uuid,
    p_title text,
    p_member_user_ids uuid[]
)
returns table (conversation_id uuid)
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_current_user uuid := (select auth.uid());
    v_conversation_id uuid;
    v_participant_ids uuid[];
    v_member_count integer;
begin
    if v_current_user is null or not private.is_org_member(p_organization_id) then
        raise exception 'You must be a current organization member to create a group.'
            using errcode = '42501';
    end if;

    if char_length(btrim(coalesce(p_title, ''))) not between 2 and 80 then
        raise exception 'Enter a group name between 2 and 80 characters.'
            using errcode = '22023';
    end if;

    select array_agg(distinct participant_id)
      into v_participant_ids
      from unnest(array_append(coalesce(p_member_user_ids, array[]::uuid[]), v_current_user)) as participant_id;

    v_member_count := coalesce(cardinality(v_participant_ids), 0);
    if v_member_count < 2 or v_member_count > 50 then
        raise exception 'A group must contain you and between 1 and 49 other members.'
            using errcode = '22023';
    end if;

    if exists (
        select 1
          from unnest(v_participant_ids) as participant(user_id)
         where not exists (
             select 1 from public.organization_members as member
              where member.organization_id = p_organization_id
                and member.user_id = participant.user_id
         )
    ) then
        raise exception 'Every group member must belong to this organization.'
            using errcode = '42501';
    end if;

    insert into public.chat_conversations (
        organization_id, conversation_type, title, created_by
    ) values (
        p_organization_id, 'group', btrim(p_title), v_current_user
    ) returning id into v_conversation_id;

    insert into public.chat_conversation_members (
        organization_id, conversation_id, user_id, member_role
    )
    select
        p_organization_id,
        v_conversation_id,
        participant.user_id,
        case when participant.user_id = v_current_user then 'owner' else 'member' end
    from unnest(v_participant_ids) as participant(user_id);

    return query select v_conversation_id;
end;
$function$;

revoke all on function public.create_group_chat(uuid, text, uuid[])
    from public, anon;
grant execute on function public.create_group_chat(uuid, text, uuid[])
    to authenticated;

create or replace function public.get_chat_conversation_messages(
    p_conversation_id uuid,
    p_before_created_at timestamptz default null,
    p_before_message_id uuid default null,
    p_page_size integer default 51
)
returns setof public.team_messages
language sql
stable
set search_path = ''
as $function$
    select message.*
      from public.team_messages as message
     where message.conversation_id = p_conversation_id
       and private.is_chat_conversation_member(message.conversation_id)
       and (
           (p_before_created_at is null and p_before_message_id is null)
           or (message.created_at, message.id)
              < (p_before_created_at, p_before_message_id)
       )
     order by message.created_at desc, message.id desc
     limit least(greatest(coalesce(p_page_size, 51), 1), 51);
$function$;

revoke all on function public.get_chat_conversation_messages(uuid, timestamptz, uuid, integer)
    from public, anon;
grant execute on function public.get_chat_conversation_messages(uuid, timestamptz, uuid, integer)
    to authenticated;

create or replace function public.mark_chat_conversation_read(
    p_conversation_id uuid,
    p_message_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_organization_id uuid;
begin
    if auth.uid() is null or not private.is_chat_conversation_member(p_conversation_id) then
        raise exception 'You must be a current member of this conversation.'
            using errcode = '42501';
    end if;

    select conversation.organization_id
      into v_organization_id
      from public.chat_conversations as conversation
     where conversation.id = p_conversation_id;

    if not exists (
        select 1 from public.team_messages as message
         where message.organization_id = v_organization_id
           and message.conversation_id = p_conversation_id
           and message.id = p_message_id
    ) then
        raise exception 'The read cursor must point to a message in this conversation.'
            using errcode = '23503';
    end if;

    insert into public.chat_conversation_read_states (
        organization_id, conversation_id, user_id, last_read_message_id
    ) values (
        v_organization_id, p_conversation_id, (select auth.uid()), p_message_id
    )
    on conflict (organization_id, conversation_id, user_id)
    do update set
        last_read_message_id = excluded.last_read_message_id,
        last_read_at = now()
    where (
        select message.created_at from public.team_messages as message
         where message.id = excluded.last_read_message_id
           and message.conversation_id = p_conversation_id
    ) >= (
        select message.created_at from public.team_messages as message
         where message.id = public.chat_conversation_read_states.last_read_message_id
           and message.conversation_id = p_conversation_id
    );
end;
$function$;

revoke all on function public.mark_chat_conversation_read(uuid, uuid)
    from public, anon;
grant execute on function public.mark_chat_conversation_read(uuid, uuid)
    to authenticated;

-- Keep legacy app builds on the Everyone channel only, never all private rows.
create or replace function public.get_team_chat_messages(
    p_organization_id uuid,
    p_before_created_at timestamptz default null,
    p_before_message_id uuid default null,
    p_page_size integer default 51
)
returns setof public.team_messages
language sql
stable
set search_path = ''
as $function$
    select message.*
      from public.team_messages as message
      join public.chat_conversations as conversation
        on conversation.id = message.conversation_id
       and conversation.organization_id = message.organization_id
       and conversation.conversation_type = 'organization'
     where message.organization_id = p_organization_id
       and private.is_chat_conversation_member(message.conversation_id)
       and (
           (p_before_created_at is null and p_before_message_id is null)
           or (message.created_at, message.id)
              < (p_before_created_at, p_before_message_id)
       )
     order by message.created_at desc, message.id desc
     limit least(greatest(coalesce(p_page_size, 51), 1), 51);
$function$;

-- Preserve old read-state RPCs, but keep the existing notifications helper
-- from acknowledging other conversations in the same organization.
create or replace function public.mark_team_chat_notifications_read_through(
    p_organization_id uuid,
    p_through_message_id uuid
)
returns integer
language plpgsql
set search_path = ''
as $function$
declare
    v_conversation_id uuid;
    v_through_created_at timestamptz;
    v_updated_count integer;
begin
    if auth.uid() is null or not private.is_org_member(p_organization_id) then
        raise exception 'You must be a current member of this organization.'
            using errcode = '42501';
    end if;

    select message.conversation_id, message.created_at
      into v_conversation_id, v_through_created_at
      from public.team_messages as message
     where message.organization_id = p_organization_id
       and message.id = p_through_message_id;

    if not found or not private.is_chat_conversation_member(v_conversation_id) then
        raise exception 'The read cursor must point to a message in your conversation.'
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
              and message.conversation_id = v_conversation_id
              and message.id = notification.team_message_id
              and (message.created_at, message.id)
                  <= (v_through_created_at, p_through_message_id)
       );

    get diagnostics v_updated_count = row_count;
    return v_updated_count;
end;
$function$;

-- Route only new notifications to members of the target conversation.
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'chat_conversation_read_states'
    ) then
        alter publication supabase_realtime add table public.chat_conversation_read_states;
    end if;
end;
$$;
