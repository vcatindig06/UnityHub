-- Efficient cursor-based Team Chat history and private-member read receipts.
--
-- The RPC is SECURITY INVOKER: it runs with the caller's permissions and remains
-- subject to the team_messages SELECT grant and organization-membership RLS policy.

alter table public.team_messages
    add constraint team_messages_organization_id_id_key
    unique (organization_id, id);

create table public.team_chat_read_states (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null
        references public.organizations(id) on delete cascade,
    user_id uuid not null
        references auth.users(id) on delete cascade,
    last_read_message_id uuid not null,
    last_read_at timestamptz not null default now(),
    constraint team_chat_read_states_one_per_member
        unique (organization_id, user_id),
    constraint team_chat_read_states_message_same_org
        foreign key (organization_id, last_read_message_id)
        references public.team_messages (organization_id, id)
        on delete cascade
);

create index team_chat_read_states_organization_idx
    on public.team_chat_read_states (organization_id);

alter table public.team_chat_read_states enable row level security;

create policy team_chat_read_states_select_current_members
    on public.team_chat_read_states
    for select
    to authenticated
    using (private.is_org_member(organization_id));

create policy team_chat_read_states_insert_own_member
    on public.team_chat_read_states
    for insert
    to authenticated
    with check (
        user_id = (select auth.uid())
        and private.is_org_member(organization_id)
    );

create policy team_chat_read_states_update_own_member
    on public.team_chat_read_states
    for update
    to authenticated
    using (
        user_id = (select auth.uid())
        and private.is_org_member(organization_id)
    )
    with check (
        user_id = (select auth.uid())
        and private.is_org_member(organization_id)
    );

revoke all on table public.team_chat_read_states from public, anon, authenticated;
grant select on table public.team_chat_read_states to authenticated;
grant insert (organization_id, user_id, last_read_message_id)
    on table public.team_chat_read_states to authenticated;
grant update (organization_id, user_id, last_read_message_id)
    on table public.team_chat_read_states to authenticated;

-- Derive read_at from the referenced message timestamp and prevent the cursor
-- from moving backwards. The composite foreign key separately guarantees that
-- a cursor can only point at a message in the same organization.
create or replace function private.keep_team_chat_read_cursor_monotonic()
returns trigger
language plpgsql
set search_path = ''
as $function$
declare
    v_new_created_at timestamptz;
    v_old_created_at timestamptz;
begin
    if tg_op = 'UPDATE' and (
        new.organization_id is distinct from old.organization_id
        or new.user_id is distinct from old.user_id
    ) then
        raise exception 'A Team Chat read state cannot change its organization or owner.';
    end if;

    select message.created_at
      into v_new_created_at
      from public.team_messages as message
     where message.organization_id = new.organization_id
       and message.id = new.last_read_message_id;

    if v_new_created_at is null then
        raise exception 'The read cursor must reference a message in this organization.';
    end if;

    if tg_op = 'UPDATE' then
        select message.created_at
          into v_old_created_at
          from public.team_messages as message
         where message.organization_id = old.organization_id
           and message.id = old.last_read_message_id;

        if v_old_created_at is not null
           and (v_new_created_at, new.last_read_message_id)
               < (v_old_created_at, old.last_read_message_id) then
            new.last_read_message_id := old.last_read_message_id;
            v_new_created_at := v_old_created_at;
        end if;
    end if;

    new.last_read_at := v_new_created_at;
    return new;
end;
$function$;

revoke all on function private.keep_team_chat_read_cursor_monotonic()
    from public, anon, authenticated;

create trigger team_chat_read_state_cursor_guard
    before insert or update on public.team_chat_read_states
    for each row
    execute function private.keep_team_chat_read_cursor_monotonic();

-- Keyset pagination keeps older pages stable while new messages are inserted.
-- The RPC returns newest-first; the Android client reverses each page for its
-- normal chronological LazyColumn. The hard upper bound prevents huge requests.
create or replace function public.get_team_chat_messages(
    p_organization_id uuid,
    p_before_created_at timestamptz default null,
    p_before_message_id uuid default null,
    p_page_size integer default 51
)
returns setof public.team_messages
language sql
stable
security invoker
set search_path = ''
as $function$
    select message.*
      from public.team_messages as message
     where message.organization_id = p_organization_id
       and (
           (p_before_created_at is null and p_before_message_id is null)
           or (message.created_at, message.id)
              < (p_before_created_at, p_before_message_id)
       )
     order by message.created_at desc, message.id desc
     limit least(greatest(coalesce(p_page_size, 51), 1), 51);
$function$;

revoke all on function public.get_team_chat_messages(uuid, timestamptz, uuid, integer)
    from public, anon;
grant execute on function public.get_team_chat_messages(uuid, timestamptz, uuid, integer)
    to authenticated;

-- Postgres Changes drives the live receipt count for the active organization.
do $$
begin
    if not exists (
        select 1
          from pg_publication_tables
         where pubname = 'supabase_realtime'
           and schemaname = 'public'
           and tablename = 'team_chat_read_states'
    ) then
        alter publication supabase_realtime add table public.team_chat_read_states;
    end if;
end;
$$;

-- Clear only the caller's private Team Chat notifications in one request.
-- This invoker function uses the same recipient RLS policy and read_at-only
-- UPDATE privilege as the existing Notifications screen.
create or replace function public.mark_team_chat_notifications_read(
    p_team_message_ids uuid[]
)
returns integer
language sql
security invoker
set search_path = ''
as $function$
    with updated as (
        update public.notifications as notification
           set read_at = now()
         where notification.recipient_user_id = (select auth.uid())
           and notification.notification_type = 'team_message'
           and notification.team_message_id = any(
               coalesce(p_team_message_ids, array[]::uuid[])
           )
           and notification.read_at is null
        returning 1
    )
    select count(*)::integer from updated;
$function$;

revoke all on function public.mark_team_chat_notifications_read(uuid[])
    from public, anon;
grant execute on function public.mark_team_chat_notifications_read(uuid[])
    to authenticated;
