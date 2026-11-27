-- Run group administration as the caller and let RLS enforce owner scope.
create or replace function private.is_org_member_of(
    p_organization_id uuid,
    p_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select exists (
        select 1
          from public.organization_members as membership
         where membership.organization_id = p_organization_id
           and membership.user_id = p_user_id
    );
$function$;

revoke all on function private.is_org_member_of(uuid, uuid)
    from public, anon, authenticated;
grant execute on function private.is_org_member_of(uuid, uuid)
    to authenticated;

drop policy if exists chat_conversations_update_group_owner
    on public.chat_conversations;
create policy chat_conversations_update_group_owner
    on public.chat_conversations
    for update to authenticated
    using (
        conversation_type = 'group'
        and private.is_chat_conversation_member(id)
        and exists (
            select 1 from public.chat_conversation_members as owner
             where owner.conversation_id = chat_conversations.id
               and owner.user_id = (select auth.uid())
               and owner.member_role = 'owner'
        )
    )
    with check (
        conversation_type = 'group'
        and private.is_chat_conversation_member(id)
        and exists (
            select 1 from public.chat_conversation_members as owner
             where owner.conversation_id = id
               and owner.user_id = (select auth.uid())
               and owner.member_role = 'owner'
        )
    );

drop policy if exists chat_conversation_members_insert_group_owner
    on public.chat_conversation_members;
create policy chat_conversation_members_insert_group_owner
    on public.chat_conversation_members
    for insert to authenticated
    with check (
        member_role = 'member'
        and private.is_org_member_of(organization_id, user_id)
        and private.is_chat_conversation_member(conversation_id)
        and exists (
            select 1 from public.chat_conversations as conversation
             where conversation.id = chat_conversation_members.conversation_id
               and conversation.organization_id = chat_conversation_members.organization_id
               and conversation.conversation_type = 'group'
        )
        and exists (
            select 1 from public.chat_conversation_members as owner
             where owner.conversation_id = chat_conversation_members.conversation_id
               and owner.user_id = (select auth.uid())
               and owner.member_role = 'owner'
        )
    );

drop policy if exists chat_conversation_members_delete_group_owner
    on public.chat_conversation_members;
create policy chat_conversation_members_delete_group_owner
    on public.chat_conversation_members
    for delete to authenticated
    using (
        member_role <> 'owner'
        and private.is_chat_conversation_member(conversation_id)
        and exists (
            select 1 from public.chat_conversations as conversation
             where conversation.id = conversation_id
               and conversation.organization_id = chat_conversation_members.organization_id
               and conversation.conversation_type = 'group'
        )
        and exists (
            select 1 from public.chat_conversation_members as owner
             where owner.conversation_id = chat_conversation_members.conversation_id
               and owner.user_id = (select auth.uid())
               and owner.member_role = 'owner'
        )
    );

grant update (title, updated_at)
    on public.chat_conversations to authenticated;
grant insert (organization_id, conversation_id, user_id, member_role)
    on public.chat_conversation_members to authenticated;
grant delete on public.chat_conversation_members to authenticated;

alter function public.rename_group_chat(uuid, text) security invoker;
alter function public.add_group_chat_member(uuid, uuid) security invoker;
alter function public.remove_group_chat_member(uuid, uuid) security invoker;

create or replace function public.add_group_chat_member(
    p_conversation_id uuid,
    p_user_id uuid
)
returns void
language plpgsql
security invoker
set search_path = ''
as $function$
declare
    v_organization_id uuid;
begin
    select conversation.organization_id
      into v_organization_id
      from public.chat_conversations as conversation
     where conversation.id = p_conversation_id
       and conversation.conversation_type = 'group';

    if v_organization_id is null
       or not exists (
            select 1 from public.chat_conversation_members as owner
             where owner.conversation_id = p_conversation_id
               and owner.user_id = (select auth.uid())
               and owner.member_role = 'owner'
       ) then
        raise exception 'Only this group owner can add members.'
            using errcode = '42501';
    end if;

    if not private.is_org_member_of(v_organization_id, p_user_id) then
        raise exception 'The selected person is not a member of this organization.'
            using errcode = '42501';
    end if;

    if (select count(*) from public.chat_conversation_members
         where conversation_id = p_conversation_id) >= 50 then
        raise exception 'Groups can contain up to 50 members.'
            using errcode = '22023';
    end if;

    insert into public.chat_conversation_members (
        organization_id, conversation_id, user_id, member_role
    ) values (v_organization_id, p_conversation_id, p_user_id, 'member')
    on conflict (organization_id, conversation_id, user_id) do nothing;

    update public.chat_conversations set updated_at = now()
     where id = p_conversation_id;
end;
$function$;

create or replace function public.rename_group_chat(
    p_conversation_id uuid,
    p_title text
)
returns void
language plpgsql
security invoker
set search_path = ''
as $function$
begin
    if auth.uid() is null
       or char_length(btrim(coalesce(p_title, ''))) not between 2 and 80
       or not exists (
            select 1 from public.chat_conversation_members as member
             where member.conversation_id = p_conversation_id
               and member.user_id = (select auth.uid())
               and member.member_role = 'owner'
       )
       or not exists (
            select 1 from public.chat_conversations as conversation
             where conversation.id = p_conversation_id
               and conversation.conversation_type = 'group'
       ) then
        raise exception 'Only this group owner can rename the group. Use a name between 2 and 80 characters.'
            using errcode = '42501';
    end if;

    update public.chat_conversations
       set title = btrim(p_title), updated_at = now()
     where id = p_conversation_id;
end;
$function$;

create or replace function public.remove_group_chat_member(
    p_conversation_id uuid,
    p_user_id uuid
)
returns void
language plpgsql
security invoker
set search_path = ''
as $function$
begin
    if auth.uid() is null
       or not exists (
            select 1 from public.chat_conversation_members as owner
             where owner.conversation_id = p_conversation_id
               and owner.user_id = (select auth.uid())
               and owner.member_role = 'owner'
       )
       or not exists (
            select 1 from public.chat_conversations as conversation
             where conversation.id = p_conversation_id
               and conversation.conversation_type = 'group'
       ) then
        raise exception 'Only this group owner can remove members.'
            using errcode = '42501';
    end if;

    if exists (
        select 1 from public.chat_conversation_members
         where conversation_id = p_conversation_id
           and user_id = p_user_id
           and member_role = 'owner'
    ) then
        raise exception 'The group owner cannot be removed.'
            using errcode = '22023';
    end if;

    delete from public.chat_conversation_members
     where conversation_id = p_conversation_id
       and user_id = p_user_id;

    update public.chat_conversations set updated_at = now()
     where id = p_conversation_id;
end;
$function$;
