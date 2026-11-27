-- Private Team Chat attachments and organization-wide reference files.
alter table public.team_messages
    add column if not exists attachment_id uuid,
    add column if not exists attachment_path text,
    add column if not exists attachment_name text,
    add column if not exists attachment_mime_type text,
    add column if not exists attachment_size bigint;

create table if not exists public.chat_shared_files (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    conversation_id uuid references public.chat_conversations(id) on delete cascade,
    uploader_id uuid not null references auth.users(id) on delete cascade,
    storage_path text not null unique,
    file_name text not null check (char_length(file_name) between 1 and 255),
    mime_type text not null check (char_length(mime_type) between 1 and 160),
    file_size bigint not null check (file_size between 1 and 26214400),
    shared_to_team boolean not null default false,
    created_at timestamptz not null default now(),
    constraint chat_shared_files_scope_check check (
        (conversation_id is null and shared_to_team)
        or conversation_id is not null
    )
);

create index if not exists chat_shared_files_org_library_idx
    on public.chat_shared_files (organization_id, created_at desc)
    where shared_to_team = true;
create index if not exists chat_shared_files_conversation_idx
    on public.chat_shared_files (conversation_id, created_at desc)
    where conversation_id is not null;

alter table public.chat_shared_files enable row level security;
grant select, insert, delete on public.chat_shared_files to authenticated;
grant select, insert, update on public.team_messages to authenticated;

drop policy if exists chat_shared_files_select_accessible on public.chat_shared_files;
create policy chat_shared_files_select_accessible
on public.chat_shared_files
for select
to authenticated
using (
    exists (
        select 1
        from public.organization_members as membership
        where membership.organization_id = chat_shared_files.organization_id
          and membership.user_id = (select auth.uid())
    )
    and (
        chat_shared_files.shared_to_team
        or (
            chat_shared_files.conversation_id is not null
            and private.is_chat_conversation_member(chat_shared_files.conversation_id)
        )
    )
);

drop policy if exists chat_shared_files_insert_member on public.chat_shared_files;
create policy chat_shared_files_insert_member
on public.chat_shared_files
for insert
to authenticated
with check (
    uploader_id = (select auth.uid())
    and exists (
        select 1
        from public.organization_members as membership
        where membership.organization_id = chat_shared_files.organization_id
          and membership.user_id = (select auth.uid())
    )
    and (
        (conversation_id is null and shared_to_team)
        or (
            conversation_id is not null
            and exists (
                select 1
                from public.chat_conversations as conversation
                where conversation.id = chat_shared_files.conversation_id
                  and conversation.organization_id = chat_shared_files.organization_id
            )
            and private.is_chat_conversation_member(conversation_id)
        )
    )
);

drop policy if exists chat_shared_files_delete_own on public.chat_shared_files;
create policy chat_shared_files_delete_own
on public.chat_shared_files
for delete
to authenticated
using (
    uploader_id = (select auth.uid())
    and exists (
        select 1
        from public.organization_members as membership
        where membership.organization_id = chat_shared_files.organization_id
          and membership.user_id = (select auth.uid())
    )
);

alter table public.team_messages
    drop constraint if exists team_messages_attachment_id_fkey;
alter table public.team_messages
    add constraint team_messages_attachment_id_fkey
    foreign key (attachment_id) references public.chat_shared_files(id) on delete set null;

create or replace function private.guard_team_chat_message_attachment()
returns trigger
language plpgsql
set search_path to ''
as $function$
declare
    v_file public.chat_shared_files%rowtype;
begin
    if tg_op = 'UPDATE' then
        if new.attachment_id is distinct from old.attachment_id
           or new.attachment_path is distinct from old.attachment_path
           or new.attachment_name is distinct from old.attachment_name
           or new.attachment_mime_type is distinct from old.attachment_mime_type
           or new.attachment_size is distinct from old.attachment_size then
            raise exception 'A Team Chat attachment cannot be replaced after the message is sent.'
                using errcode = '42501';
        end if;
        return new;
    end if;

    if new.attachment_id is null then
        if new.attachment_path is not null
           or new.attachment_name is not null
           or new.attachment_mime_type is not null
           or new.attachment_size is not null then
            raise exception 'Attachment metadata must reference a registered chat file.'
                using errcode = '23514';
        end if;
        return new;
    end if;

    select file.*
      into v_file
      from public.chat_shared_files as file
     where file.id = new.attachment_id;

    if not found
       or v_file.organization_id <> new.organization_id
       or v_file.uploader_id <> new.sender_id
       or v_file.storage_path is distinct from new.attachment_path
       or v_file.file_name is distinct from new.attachment_name
       or v_file.mime_type is distinct from new.attachment_mime_type
       or v_file.file_size is distinct from new.attachment_size
       or (
           v_file.conversation_id is not null
           and v_file.conversation_id <> new.conversation_id
       ) then
        raise exception 'This file is not registered for the sender or conversation.'
            using errcode = '42501';
    end if;

    return new;
end;
$function$;

drop trigger if exists team_message_attachment_guard on public.team_messages;
create trigger team_message_attachment_guard
before insert or update on public.team_messages
for each row
execute function private.guard_team_chat_message_attachment();

insert into storage.buckets (
    id, name, public, file_size_limit, allowed_mime_types
) values (
    'chat-attachments',
    'chat-attachments',
    false,
    26214400,
    array[
        'image/jpeg',
        'image/png',
        'image/webp',
        'image/gif',
        'image/heic',
        'image/heif',
        'application/pdf',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'text/plain',
        'text/csv',
        'audio/mpeg',
        'audio/mp4',
        'audio/aac',
        'audio/ogg',
        'audio/wav',
        'video/mp4'
    ]
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists chat_attachments_insert_member on storage.objects;
create policy chat_attachments_insert_member
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'chat-attachments'
    and (storage.foldername(name))[3] = (select auth.uid())::text
    and (
        (
            (storage.foldername(name))[2] = 'library'
            and exists (
                select 1
                from public.organization_members as membership
                where membership.organization_id::text = (storage.foldername(name))[1]
                  and membership.user_id = (select auth.uid())
            )
        )
        or exists (
            select 1
            from public.chat_conversations as conversation
            join public.chat_conversation_members as member
              on member.conversation_id = conversation.id
            where conversation.id::text = (storage.foldername(name))[2]
              and conversation.organization_id::text = (storage.foldername(name))[1]
              and member.user_id = (select auth.uid())
        )
    )
);

drop policy if exists chat_attachments_select_accessible on storage.objects;
create policy chat_attachments_select_accessible
on storage.objects
for select
to authenticated
using (
    bucket_id = 'chat-attachments'
    and exists (
        select 1
        from public.chat_shared_files as file
        where file.storage_path = name
          and (
              (
                  file.shared_to_team
                  and exists (
                      select 1
                      from public.organization_members as membership
                      where membership.organization_id = file.organization_id
                        and membership.user_id = (select auth.uid())
                  )
              )
              or (
                  file.conversation_id is not null
                  and private.is_chat_conversation_member(file.conversation_id)
              )
          )
    )
);

drop policy if exists chat_attachments_delete_own on storage.objects;
create policy chat_attachments_delete_own
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'chat-attachments'
    and exists (
        select 1
        from public.chat_shared_files as file
        where file.storage_path = name
          and file.uploader_id = (select auth.uid())
          and exists (
              select 1
              from public.organization_members as membership
              where membership.organization_id = file.organization_id
                and membership.user_id = (select auth.uid())
          )
    )
);
