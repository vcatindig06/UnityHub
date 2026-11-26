-- Team Chat reactions, replies, edit/delete, and pinned messages.
-- Message deletion is soft deletion so replies and history remain stable.

alter table public.team_messages
    add column if not exists reply_to_message_id uuid,
    add column if not exists edited_at timestamptz,
    add column if not exists deleted_at timestamptz,
    add column if not exists pinned_at timestamptz,
    add column if not exists pinned_by uuid references auth.users(id) on delete set null;

alter table public.team_messages
    add constraint team_messages_reply_same_org
    foreign key (organization_id, reply_to_message_id)
    references public.team_messages (organization_id, id);

alter table public.team_messages
    add constraint team_messages_no_self_reply
    check (reply_to_message_id is null or reply_to_message_id <> id);

create index team_messages_reply_to_idx
    on public.team_messages (organization_id, reply_to_message_id)
    where reply_to_message_id is not null;
create index team_messages_pinned_idx
    on public.team_messages (organization_id, pinned_at desc)
    where pinned_at is not null;

create table public.team_message_reactions (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    message_id uuid not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    emoji text not null,
    created_at timestamptz not null default now(),
    constraint team_message_reactions_emoji_length check (char_length(emoji) between 1 and 16),
    constraint team_message_reactions_unique unique (message_id, user_id, emoji),
    constraint team_message_reactions_message_same_org
        foreign key (organization_id, message_id)
        references public.team_messages (organization_id, id)
        on delete cascade
);

create index team_message_reactions_org_message_idx
    on public.team_message_reactions (organization_id, message_id);
create index team_message_reactions_user_idx
    on public.team_message_reactions (user_id);

alter table public.team_message_reactions enable row level security;

create policy team_message_reactions_select_members
    on public.team_message_reactions
    for select to authenticated
    using (private.is_org_member(organization_id));

create policy team_message_reactions_insert_own
    on public.team_message_reactions
    for insert to authenticated
    with check (
        user_id = (select auth.uid())
        and private.is_org_member(organization_id)
    );

create policy team_message_reactions_delete_own
    on public.team_message_reactions
    for delete to authenticated
    using (
        user_id = (select auth.uid())
        and private.is_org_member(organization_id)
    );

revoke all on table public.team_message_reactions from public, anon, authenticated;
grant select, insert, delete on table public.team_message_reactions to authenticated;

create policy team_messages_update_current_members
    on public.team_messages
    for update to authenticated
    using (private.is_org_member(organization_id))
    with check (private.is_org_member(organization_id));

grant update (content, edited_at, deleted_at, pinned_at, pinned_by)
    on public.team_messages to authenticated;

create or replace function private.guard_team_chat_message_update()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
    if auth.uid() is null
       or not private.is_org_member(old.organization_id)
       or new.organization_id is distinct from old.organization_id
       or new.sender_id is distinct from old.sender_id
       or new.created_at is distinct from old.created_at
       or new.reply_to_message_id is distinct from old.reply_to_message_id then
        raise exception 'A Team Chat message cannot be reassigned or changed outside its organization.'
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

revoke all on function private.guard_team_chat_message_update()
    from public, anon, authenticated;

create trigger team_chat_message_update_guard
    before update on public.team_messages
    for each row
    execute function private.guard_team_chat_message_update();

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'team_message_reactions'
    ) then
        alter publication supabase_realtime add table public.team_message_reactions;
    end if;
end;
$$;
