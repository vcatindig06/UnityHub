-- Reusable, organization-scoped service setlist templates.
create table public.service_templates (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    name text not null,
    description text not null default '',
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint service_templates_name_length_check
        check (char_length(btrim(name)) between 2 and 100),
    constraint service_templates_description_length_check
        check (char_length(description) <= 500),
    constraint service_templates_id_organization_unique
        unique (id, organization_id)
);

create unique index service_templates_organization_name_ci_idx
    on public.service_templates (organization_id, lower(btrim(name)));

create index service_templates_organization_updated_idx
    on public.service_templates (organization_id, updated_at desc);

create table public.service_template_items (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    template_id uuid not null,
    song_id uuid references public.songs(id),
    title text not null,
    item_type text not null default 'song',
    chordpro_snapshot text not null default '',
    item_order integer not null default 0,
    duration_minutes integer,
    notes text not null default '',
    created_at timestamptz not null default now(),
    constraint service_template_items_title_length_check
        check (char_length(btrim(title)) between 1 and 200),
    constraint service_template_items_type_check
        check (item_type in ('song', 'prayer', 'reading', 'announcement', 'other')),
    constraint service_template_items_duration_check
        check (duration_minutes is null or duration_minutes between 0 and 600),
    constraint service_template_items_order_check
        check (item_order >= 0),
    constraint service_template_items_template_org_fkey
        foreign key (template_id, organization_id)
        references public.service_templates(id, organization_id)
        on delete cascade
);

create index service_template_items_template_order_idx
    on public.service_template_items (template_id, item_order, created_at, id);

create index service_template_items_organization_idx
    on public.service_template_items (organization_id);

alter table public.service_templates enable row level security;
alter table public.service_template_items enable row level security;

create policy service_templates_read_members
    on public.service_templates
    for select
    to authenticated
    using (private.is_org_member(organization_id));

create policy service_templates_insert_leaders
    on public.service_templates
    for insert
    to authenticated
    with check (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
        and created_by = (select auth.uid())
    );

create policy service_templates_update_leaders
    on public.service_templates
    for update
    to authenticated
    using (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
    )
    with check (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
    );

create policy service_templates_delete_leaders
    on public.service_templates
    for delete
    to authenticated
    using (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
    );

create policy service_template_items_read_members
    on public.service_template_items
    for select
    to authenticated
    using (private.is_org_member(organization_id));

create policy service_template_items_insert_leaders
    on public.service_template_items
    for insert
    to authenticated
    with check (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
        and exists (
            select 1
            from public.service_templates template
            where template.id = service_template_items.template_id
              and template.organization_id = service_template_items.organization_id
        )
        and (
            song_id is null
            or exists (
                select 1
                from public.songs song
                where song.id = service_template_items.song_id
                  and song.organization_id = service_template_items.organization_id
            )
        )
    );

create policy service_template_items_delete_leaders
    on public.service_template_items
    for delete
    to authenticated
    using (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
    );

-- Data API exposure is explicit for new public tables.
revoke all on table public.service_templates from anon, public, authenticated;
revoke all on table public.service_template_items from anon, public, authenticated;

grant select, insert, delete on table public.service_templates to authenticated;
grant update (name, description, updated_at)
    on table public.service_templates to authenticated;

grant select, insert, delete on table public.service_template_items to authenticated;
