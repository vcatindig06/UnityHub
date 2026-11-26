create or replace function private.capture_user_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_row jsonb;
    v_actor uuid;
    v_organization_id uuid;
    v_entity_id text;
    v_entity_label text;
    v_action text;
    v_title text;
begin
    v_actor := auth.uid();
    if v_actor is null then
        if TG_OP = 'DELETE' then return OLD; else return NEW; end if;
    end if;

    if TG_OP = 'DELETE' then
        v_row := to_jsonb(OLD);
    else
        v_row := to_jsonb(NEW);
    end if;

    v_organization_id := nullif(v_row ->> 'organization_id', '')::uuid;
    v_entity_id := coalesce(
        nullif(v_row ->> 'id', ''),
        nullif(v_row ->> 'member_id', '') || '/' || nullif(v_row ->> 'position_id', ''),
        nullif(v_row ->> 'service_plan_id', ''),
        nullif(v_row ->> 'rehearsal_id', '')
    );
    v_entity_label := coalesce(
        nullif(v_row ->> 'title', ''),
        nullif(v_row ->> 'name', ''),
        nullif(v_row ->> 'display_name', ''),
        nullif(v_row ->> 'role_label', ''),
        'item'
    );

    v_action := case TG_OP
        when 'INSERT' then 'created'
        when 'UPDATE' then 'updated'
        when 'DELETE' then 'removed'
        else 'changed'
    end;

    v_title := case TG_TABLE_NAME
        when 'profiles' then 'Updated profile details'
        when 'songs' then initcap(v_action) || ' song: ' || v_entity_label
        when 'service_plans' then initcap(v_action) || ' service: ' || v_entity_label
        when 'service_plan_items' then initcap(v_action) || ' service item: ' || v_entity_label
        when 'service_plan_assignments' then initcap(v_action) || ' service assignment'
        when 'rehearsals' then initcap(v_action) || ' rehearsal: ' || v_entity_label
        when 'rehearsal_assignments' then initcap(v_action) || ' rehearsal attendance'
        when 'team_messages' then 'Sent a Team Chat message'
        when 'member_positions' then case TG_OP
            when 'INSERT' then 'Assigned a ministry position'
            when 'DELETE' then 'Removed a ministry position'
            else 'Updated a ministry position assignment'
        end
        when 'ministry_positions' then initcap(v_action) || ' ministry position: ' || v_entity_label
        when 'song_setlists' then initcap(v_action) || ' song setlist: ' || v_entity_label
        when 'song_setlist_items' then initcap(v_action) || ' song setlist item: ' || v_entity_label
        when 'service_templates' then initcap(v_action) || ' service template: ' || v_entity_label
        when 'service_template_items' then initcap(v_action) || ' service template item: ' || v_entity_label
        when 'organization_members' then 'Updated team membership'
        when 'organization_join_requests' then initcap(v_action) || ' a team join request'
        else initcap(v_action) || ' ' || replace(TG_TABLE_NAME, '_', ' ')
    end;

    -- Profile edits are global to the account. Copy the event into each
    -- current team so each member sees it only in the matching team history.
    if TG_TABLE_NAME = 'profiles' and v_organization_id is null then
        insert into public.user_activity (
            actor_user_id, organization_id, event_key, title, description, entity_type, entity_id
        )
        select
            v_actor,
            membership.organization_id,
            TG_TABLE_NAME || '.' || lower(TG_OP),
            left(v_title, 180),
            '',
            TG_TABLE_NAME,
            v_entity_id
        from public.organization_members as membership
        where membership.user_id = v_actor;

        if not found then
            insert into public.user_activity (
                actor_user_id, organization_id, event_key, title, description, entity_type, entity_id
            ) values (
                v_actor, null, TG_TABLE_NAME || '.' || lower(TG_OP),
                left(v_title, 180), '', TG_TABLE_NAME, v_entity_id
            );
        end if;

        if TG_OP = 'DELETE' then return OLD; else return NEW; end if;
    end if;

    insert into public.user_activity (
        actor_user_id, organization_id, event_key, title, description, entity_type, entity_id
    ) values (
        v_actor,
        v_organization_id,
        TG_TABLE_NAME || '.' || lower(TG_OP),
        left(v_title, 180),
        case
            when TG_TABLE_NAME = 'team_messages' then 'A message was sent to the team.'
            when TG_TABLE_NAME = 'service_plan_assignments' then 'A service assignment was changed.'
            when TG_TABLE_NAME = 'rehearsal_assignments' then 'A rehearsal response or attendance record was changed.'
            else ''
        end,
        TG_TABLE_NAME,
        v_entity_id
    );

    if TG_OP = 'DELETE' then return OLD; else return NEW; end if;
end;
$$;

revoke all on function private.capture_user_activity() from public, anon, authenticated;
