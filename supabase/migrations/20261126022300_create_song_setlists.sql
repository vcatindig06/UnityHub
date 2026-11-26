-- Team-shared song setlists are separate from dated service plans.
create table public.song_setlists (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    name text not null,
    description text not null default '',
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint song_setlists_name_length_check
        check (char_length(btrim(name)) between 2 and 100),
    constraint song_setlists_description_length_check
        check (char_length(description) <= 500),
    constraint song_setlists_id_organization_unique
        unique (id, organization_id)
);

create unique index song_setlists_organization_name_ci_idx
    on public.song_setlists (organization_id, lower(btrim(name)));
create index song_setlists_organization_updated_idx
    on public.song_setlists (organization_id, updated_at desc);

create table public.song_setlist_items (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    setlist_id uuid not null,
    song_id uuid references public.songs(id) on delete set null,
    title text not null,
    artist text not null default '',
    key_signature text not null default '',
    chordpro_snapshot text not null default '',
    item_order integer not null default 0,
    created_at timestamptz not null default now(),
    constraint song_setlist_items_title_length_check
        check (char_length(btrim(title)) between 1 and 200),
    constraint song_setlist_items_order_check
        check (item_order >= 0),
    constraint song_setlist_items_setlist_org_fkey
        foreign key (setlist_id, organization_id)
        references public.song_setlists(id, organization_id)
        on delete cascade
);

create index song_setlist_items_setlist_order_idx
    on public.song_setlist_items (setlist_id, item_order, created_at, id);
create index song_setlist_items_organization_idx
    on public.song_setlist_items (organization_id);
create index song_setlist_items_song_id_idx
    on public.song_setlist_items (song_id);

alter table public.song_setlists enable row level security;
alter table public.song_setlist_items enable row level security;

create policy song_setlists_read_members
    on public.song_setlists
    for select
    to authenticated
    using (private.is_org_member(organization_id));

create policy song_setlists_insert_editors
    on public.song_setlists
    for insert
    to authenticated
    with check (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
        and created_by = (select auth.uid())
    );

create policy song_setlists_update_editors
    on public.song_setlists
    for update
    to authenticated
    using (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
    )
    with check (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
    );

create policy song_setlists_delete_editors
    on public.song_setlists
    for delete
    to authenticated
    using (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
    );

create policy song_setlist_items_read_members
    on public.song_setlist_items
    for select
    to authenticated
    using (private.is_org_member(organization_id));

create policy song_setlist_items_insert_editors
    on public.song_setlist_items
    for insert
    to authenticated
    with check (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
        and exists (
            select 1
            from public.song_setlists setlist
            where setlist.id = song_setlist_items.setlist_id
              and setlist.organization_id = song_setlist_items.organization_id
        )
        and (
            song_id is null
            or exists (
                select 1
                from public.songs song
                where song.id = song_setlist_items.song_id
                  and song.organization_id = song_setlist_items.organization_id
            )
        )
    );

create policy song_setlist_items_update_editors
    on public.song_setlist_items
    for update
    to authenticated
    using (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
    )
    with check (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
    );

create policy song_setlist_items_delete_editors
    on public.song_setlist_items
    for delete
    to authenticated
    using (
        private.has_org_role(
            organization_id,
            array['admin', 'worship_leader', 'song_editor']::text[]
        )
    );

revoke all on table public.song_setlists from anon, public, authenticated;
revoke all on table public.song_setlist_items from anon, public, authenticated;
grant select, insert, delete on table public.song_setlists to authenticated;
grant update (name, description, updated_at)
    on table public.song_setlists to authenticated;
grant select, insert, delete on table public.song_setlist_items to authenticated;
grant update (item_order) on table public.song_setlist_items to authenticated;
