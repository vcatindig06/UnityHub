-- Persist a simple category for organization-wide reference files.
-- Existing organization membership and chat conversation RLS policies remain
-- the sole access boundary; this migration does not broaden table grants.

alter table public.chat_shared_files
    add column if not exists category text not null default 'other';

-- Backfill a helpful category for existing rows without guessing that a
-- document or PDF is a song or service plan.
update public.chat_shared_files
   set category = case
       when mime_type ilike 'image/%'
         or mime_type ilike 'audio/%'
         or mime_type ilike 'video/%'
           then 'media'
       when mime_type = 'application/pdf'
         or mime_type ilike 'application/%word%'
         or mime_type ilike 'application/%sheet%'
         or mime_type ilike 'application/%presentation%'
         or mime_type ilike 'text/%'
           then 'documents'
       else 'other'
   end
 where category = 'other';

do $migration$
begin
    if not exists (
        select 1
          from pg_constraint
         where conname = 'chat_shared_files_category_check'
           and conrelid = 'public.chat_shared_files'::regclass
    ) then
        alter table public.chat_shared_files
            add constraint chat_shared_files_category_check
            check (category in ('songs', 'services', 'rehearsals', 'media', 'documents', 'other'));
    end if;
end
$migration$;

create index if not exists chat_shared_files_org_category_created_idx
    on public.chat_shared_files (organization_id, category, created_at desc)
    where shared_to_team = true;
