-- Organization-wide Team Chat.
-- Messages are immutable in the first release; members can only read and send
-- messages in organizations where they currently have an active membership.

create table public.team_messages (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    content text not null,
    created_at timestamptz not null default now(),
    constraint team_messages_content_length_check
        check (char_length(btrim(content)) between 1 and 1000)
);

create index team_messages_organization_created_at_idx
    on public.team_messages (organization_id, created_at desc, id desc);

alter table public.team_messages enable row level security;

create policy team_messages_select_current_members
    on public.team_messages
    for select
    to authenticated
    using (private.is_org_member(organization_id));

create policy team_messages_insert_current_members
    on public.team_messages
    for insert
    to authenticated
    with check (
        sender_id = (select auth.uid())
        and private.is_org_member(organization_id)
    );

revoke all on table public.team_messages from public;
revoke all on table public.team_messages from anon;
revoke all on table public.team_messages from authenticated;
grant select, insert on table public.team_messages to authenticated;

-- Enable Postgres Changes for the app's organization-scoped live message feed.
do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'team_messages'
    ) then
        alter publication supabase_realtime add table public.team_messages;
    end if;
end;
$$;
