-- Organization-scoped rehearsal planning, team availability, and attendance.
-- All client access is granted explicitly and remains protected by RLS.

alter table public.organization_members
    add constraint organization_members_id_organization_unique
    unique (id, organization_id);

create table public.rehearsals (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    title text not null,
    rehearsal_date date not null,
    start_time time not null,
    end_time time,
    location text not null default '',
    notes text not null default '',
    status text not null default 'scheduled',
    service_plan_id uuid references public.service_plans(id) on delete set null,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint rehearsals_title_check check (char_length(btrim(title)) between 2 and 120),
    constraint rehearsals_location_check check (char_length(location) <= 160),
    constraint rehearsals_notes_check check (char_length(notes) <= 1000),
    constraint rehearsals_status_check check (status in ('scheduled', 'completed', 'cancelled')),
    constraint rehearsals_end_time_check check (end_time is null or end_time > start_time),
    constraint rehearsals_id_organization_unique unique (id, organization_id)
);

create index rehearsals_organization_date_idx
    on public.rehearsals (organization_id, rehearsal_date, start_time);
create index rehearsals_service_plan_idx
    on public.rehearsals (service_plan_id) where service_plan_id is not null;

create table public.rehearsal_assignments (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    rehearsal_id uuid not null,
    member_id uuid not null,
    response_status text not null default 'pending',
    response_note text not null default '',
    assigned_by uuid not null references auth.users(id),
    responded_at timestamptz,
    created_at timestamptz not null default now(),
    constraint rehearsal_assignments_response_status_check
        check (response_status in ('pending', 'available', 'unavailable', 'attended', 'absent')),
    constraint rehearsal_assignments_response_note_check check (char_length(response_note) <= 500),
    constraint rehearsal_assignments_rehearsal_member_unique unique (rehearsal_id, member_id),
    constraint rehearsal_assignments_rehearsal_org_fkey
        foreign key (rehearsal_id, organization_id)
        references public.rehearsals(id, organization_id) on delete cascade,
    constraint rehearsal_assignments_member_org_fkey
        foreign key (member_id, organization_id)
        references public.organization_members(id, organization_id) on delete cascade
);

create index rehearsal_assignments_rehearsal_status_idx
    on public.rehearsal_assignments (rehearsal_id, response_status);
create index rehearsal_assignments_member_idx
    on public.rehearsal_assignments (member_id, created_at desc);

create or replace function private.validate_rehearsal_service_plan_scope()
returns trigger language plpgsql set search_path = ''
as $function$
begin
    if new.service_plan_id is not null and not exists (
        select 1 from public.service_plans sp
        where sp.id = new.service_plan_id
          and sp.organization_id = new.organization_id
    ) then
        raise exception 'The selected service plan does not belong to this worship team';
    end if;
    return new;
end;
$function$;
revoke all on function private.validate_rehearsal_service_plan_scope() from public, anon, authenticated;

create trigger validate_rehearsal_service_plan_scope
    before insert or update of service_plan_id, organization_id on public.rehearsals
    for each row execute function private.validate_rehearsal_service_plan_scope();

alter table public.rehearsals enable row level security;
alter table public.rehearsal_assignments enable row level security;

create policy rehearsals_read_members on public.rehearsals
    for select to authenticated using (private.is_org_member(organization_id));
create policy rehearsals_insert_leaders on public.rehearsals
    for insert to authenticated
    with check (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
        and created_by = (select auth.uid())
    );
create policy rehearsals_update_leaders on public.rehearsals
    for update to authenticated
    using (private.has_org_role(organization_id, array['admin', 'worship_leader']::text[]))
    with check (private.has_org_role(organization_id, array['admin', 'worship_leader']::text[]));
create policy rehearsals_delete_leaders on public.rehearsals
    for delete to authenticated using (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
    );

create policy rehearsal_assignments_read_members on public.rehearsal_assignments
    for select to authenticated using (private.is_org_member(organization_id));
create policy rehearsal_assignments_insert_leaders on public.rehearsal_assignments
    for insert to authenticated
    with check (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
        and assigned_by = (select auth.uid())
        and exists (
            select 1 from public.rehearsals r
            where r.id = rehearsal_assignments.rehearsal_id
              and r.organization_id = rehearsal_assignments.organization_id
              and r.status = 'scheduled'
        )
    );
create policy rehearsal_assignments_update_leaders on public.rehearsal_assignments
    for update to authenticated
    using (private.has_org_role(organization_id, array['admin', 'worship_leader']::text[]))
    with check (private.has_org_role(organization_id, array['admin', 'worship_leader']::text[]));
create policy rehearsal_assignments_respond_self on public.rehearsal_assignments
    for update to authenticated
    using (
        exists (
            select 1
            from public.organization_members om
            join public.rehearsals r
              on r.id = rehearsal_assignments.rehearsal_id
             and r.organization_id = rehearsal_assignments.organization_id
            where om.id = rehearsal_assignments.member_id
              and om.organization_id = rehearsal_assignments.organization_id
              and om.user_id = (select auth.uid())
              and r.status = 'scheduled'
        )
    )
    with check (
        exists (
            select 1
            from public.organization_members om
            join public.rehearsals r
              on r.id = rehearsal_assignments.rehearsal_id
             and r.organization_id = rehearsal_assignments.organization_id
            where om.id = rehearsal_assignments.member_id
              and om.organization_id = rehearsal_assignments.organization_id
              and om.user_id = (select auth.uid())
              and r.status = 'scheduled'
        )
        and response_status in ('available', 'unavailable')
        and responded_at is not null
    );
create policy rehearsal_assignments_delete_leaders on public.rehearsal_assignments
    for delete to authenticated using (
        private.has_org_role(organization_id, array['admin', 'worship_leader']::text[])
    );

revoke all on table public.rehearsals from public, anon, authenticated;
revoke all on table public.rehearsal_assignments from public, anon, authenticated;
grant select, insert, delete on table public.rehearsals to authenticated;
grant update (
    title, rehearsal_date, start_time, end_time, location, notes, status, service_plan_id, updated_at
) on table public.rehearsals to authenticated;
grant select, insert, delete on table public.rehearsal_assignments to authenticated;
grant update (response_status, response_note, responded_at)
    on table public.rehearsal_assignments to authenticated;

alter table public.notifications
    add column rehearsal_id uuid references public.rehearsals(id) on delete cascade,
    add column rehearsal_assignment_id uuid references public.rehearsal_assignments(id) on delete cascade;
alter table public.notifications drop constraint notifications_type_check;
alter table public.notifications add constraint notifications_type_check
    check (notification_type in (
        'assignment_created', 'assignment_reminder', 'assignment_response',
        'rehearsal_assigned', 'rehearsal_response'
    ));
create index notifications_rehearsal_id_idx on public.notifications (rehearsal_id)
    where rehearsal_id is not null;

create or replace function private.notify_rehearsal_assignment_created()
returns trigger language plpgsql security definer set search_path = ''
as $function$
declare
    target_user_id uuid;
    target_rehearsal_title text;
    target_rehearsal_date date;
    target_start_time time;
begin
    select om.user_id into target_user_id
    from public.organization_members om
    where om.id = new.member_id and om.organization_id = new.organization_id;

    select r.title, r.rehearsal_date, r.start_time
    into target_rehearsal_title, target_rehearsal_date, target_start_time
    from public.rehearsals r
    where r.id = new.rehearsal_id and r.organization_id = new.organization_id;

    if target_user_id is null or target_rehearsal_title is null then return new; end if;

    insert into public.notifications (
        organization_id, recipient_user_id, notification_type, title, message,
        rehearsal_id, rehearsal_assignment_id, dedupe_key
    ) values (
        new.organization_id,
        target_user_id,
        'rehearsal_assigned',
        'New rehearsal invitation',
        'You have been invited to ' || target_rehearsal_title ||
            ' on ' || to_char(target_rehearsal_date, 'Mon FMDD, YYYY') ||
            ' at ' || to_char(target_start_time, 'HH12:MI AM') ||
            '. Please respond with your availability.',
        new.rehearsal_id,
        new.id,
        'rehearsal:' || new.id::text || ':invited'
    ) on conflict (dedupe_key) do nothing;

    return new;
end;
$function$;
revoke all on function private.notify_rehearsal_assignment_created() from public, anon, authenticated;
create trigger rehearsal_assignment_notification_created
    after insert on public.rehearsal_assignments
    for each row execute function private.notify_rehearsal_assignment_created();

create or replace function private.notify_rehearsal_assignment_response()
returns trigger language plpgsql security definer set search_path = ''
as $function$
declare
    assigned_member_user_id uuid;
    assigned_member_name text;
    target_rehearsal_title text;
    recipient record;
    response_text text;
begin
    if old.response_status is not distinct from new.response_status then return new; end if;
    if new.response_status not in ('available', 'unavailable') then return new; end if;

    select om.user_id, p.display_name
    into assigned_member_user_id, assigned_member_name
    from public.organization_members om
    left join public.profiles p on p.id = om.user_id
    where om.id = new.member_id and om.organization_id = new.organization_id;

    select r.title into target_rehearsal_title
    from public.rehearsals r
    where r.id = new.rehearsal_id and r.organization_id = new.organization_id;

    response_text := case when new.response_status = 'available'
        then 'can attend' else 'cannot attend' end;

    for recipient in
        select om.user_id
        from public.organization_members om
        where om.organization_id = new.organization_id
          and om.role in ('admin', 'worship_leader')
          and om.user_id is distinct from assigned_member_user_id
    loop
        insert into public.notifications (
            organization_id, recipient_user_id, notification_type, title, message,
            rehearsal_id, rehearsal_assignment_id, dedupe_key
        ) values (
            new.organization_id,
            recipient.user_id,
            'rehearsal_response',
            'Rehearsal availability updated',
            coalesce(nullif(assigned_member_name, ''), 'A team member') ||
                ' ' || response_text || ' for ' ||
                coalesce(target_rehearsal_title, 'the rehearsal') || '.',
            new.rehearsal_id,
            new.id,
            'rehearsal:' || new.id::text || ':response:' || new.response_status
        ) on conflict (dedupe_key) do nothing;
    end loop;

    return new;
end;
$function$;
revoke all on function private.notify_rehearsal_assignment_response() from public, anon, authenticated;
create trigger rehearsal_assignment_notification_response
    after update of response_status on public.rehearsal_assignments
    for each row execute function private.notify_rehearsal_assignment_response();
