-- Allow the authenticated UnityHub clients to receive recipient-scoped
-- notification INSERT and UPDATE events through Supabase Realtime.
--
-- The existing SELECT and UPDATE RLS policies on public.notifications remain
-- the authorization boundary; this migration adds no table privileges or policies.

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'notifications'
    ) then
        alter publication supabase_realtime add table public.notifications;
    end if;
end
$$;
