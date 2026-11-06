-- Secure administrator member controls and make ownership transfer atomic.
-- All functions lock the organization before membership rows so concurrent
-- role changes, removals and transfers follow a consistent lock order.

create or replace function public.change_member_role(p_member_id uuid, p_new_role text)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_organization_id uuid;
  v_member public.organization_members%rowtype;
  v_owner_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if p_new_role not in ('admin', 'worship_leader', 'song_editor', 'team_member', 'viewer') then
    raise exception 'Invalid role';
  end if;

  select organization_id into v_organization_id
  from public.organization_members
  where id = p_member_id;

  if not found then
    raise exception 'Member not found';
  end if;

  select o.created_by into v_owner_id
  from public.organizations as o
  where o.id = v_organization_id
  for update;

  if not found then
    raise exception 'Organization not found';
  end if;

  select * into v_member
  from public.organization_members
  where id = p_member_id
    and organization_id = v_organization_id
  for update;

  if not found then
    raise exception 'Member not found';
  end if;

  if not private.has_org_role(v_member.organization_id, array['admin']) then
    raise exception 'Only an administrator can change roles';
  end if;

  if v_member.user_id = v_owner_id and p_new_role <> 'admin' then
    raise exception 'The organization owner must transfer ownership before changing their administrator role';
  end if;

  if v_member.role = 'admin'
     and p_new_role <> 'admin'
     and (
       select count(*)
       from public.organization_members
       where organization_id = v_member.organization_id
         and role = 'admin'
     ) <= 1 then
    raise exception 'The organization must retain at least one administrator';
  end if;

  update public.organization_members
  set role = p_new_role
  where id = p_member_id;
end;
$function$;

create or replace function public.remove_member(p_member_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_organization_id uuid;
  v_member public.organization_members%rowtype;
  v_owner_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  select organization_id into v_organization_id
  from public.organization_members
  where id = p_member_id;

  if not found then
    raise exception 'Member not found';
  end if;

  select o.created_by into v_owner_id
  from public.organizations as o
  where o.id = v_organization_id
  for update;

  if not found then
    raise exception 'Organization not found';
  end if;

  select * into v_member
  from public.organization_members
  where id = p_member_id
    and organization_id = v_organization_id
  for update;

  if not found then
    raise exception 'Member not found';
  end if;

  if not private.has_org_role(v_member.organization_id, array['admin']) then
    raise exception 'Only an administrator can remove members';
  end if;

  if v_member.user_id = v_owner_id then
    raise exception 'The organization owner must transfer ownership before being removed';
  end if;

  if v_member.role = 'admin'
     and (
       select count(*)
       from public.organization_members
       where organization_id = v_member.organization_id
         and role = 'admin'
     ) <= 1 then
    raise exception 'The last administrator cannot be removed';
  end if;

  delete from public.organization_members
  where id = p_member_id;
end;
$function$;

create or replace function public.transfer_organization_ownership(
  p_organization_id uuid,
  p_new_owner_user_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_organization public.organizations%rowtype;
  v_current_member public.organization_members%rowtype;
  v_new_member public.organization_members%rowtype;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if p_new_owner_user_id is null or p_new_owner_user_id = auth.uid() then
    raise exception 'Choose another existing organization member';
  end if;

  select * into v_organization
  from public.organizations
  where id = p_organization_id
  for update;

  if not found then
    raise exception 'Organization not found';
  end if;

  if v_organization.created_by <> auth.uid() then
    raise exception 'Only the current organization owner can transfer ownership';
  end if;

  -- Lock both membership rows in a deterministic order after locking the
  -- organization, preventing concurrent role changes/removals from racing.
  perform om.id
  from public.organization_members as om
  where om.organization_id = p_organization_id
    and om.user_id in (auth.uid(), p_new_owner_user_id)
  order by om.id
  for update;

  select * into v_current_member
  from public.organization_members
  where organization_id = p_organization_id
    and user_id = auth.uid();

  if not found then
    raise exception 'The current organization owner must be a team member';
  end if;

  if v_current_member.role <> 'admin' then
    raise exception 'The current organization owner must be an administrator';
  end if;

  select * into v_new_member
  from public.organization_members
  where organization_id = p_organization_id
    and user_id = p_new_owner_user_id;

  if not found then
    raise exception 'The new owner must already be a member of this organization';
  end if;

  update public.organization_members
  set role = 'admin'
  where id = v_new_member.id;

  update public.organizations
  set created_by = p_new_owner_user_id,
      updated_at = now()
  where id = p_new_owner.id;

  update public.organization_members
  set role = 'team_member'
  where id = v_current_member.id;
end;
$function$;

-- SECURITY DEFINER transfer is granted only to authenticated users.
revoke all on function public.transfer_organization_ownership(uuid, uuid) from public;
revoke all on function public.transfer_organization_ownership(uuid, uuid) from anon;
grant execute on function public.transfer_organization_ownership(uuid, uuid) to authenticated;
